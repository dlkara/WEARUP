package com.lec.spring.security;

import com.lec.spring.config.PrincipalBrandDetails;
import com.lec.spring.config.PrincipalUserDetails;
import com.lec.spring.domain.*;
import com.lec.spring.repository.*;
import com.lec.spring.service.*;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnershipServiceTest {
    @TempDir Path upload;
    PostRepository posts;
    PostAttachmentRepository attachments;
    ItemRepository items;
    ItemAttachmentRepository itemAttachments;
    PostAttachmentService postAttachments;
    ResourceAccess access;
    PostServiceImpl postService;
    ItemAttachmentServiceImpl itemService;

    @BeforeEach
    void setup() {
        posts = mock(PostRepository.class);
        attachments = mock(PostAttachmentRepository.class);
        items = mock(ItemRepository.class);
        itemAttachments = mock(ItemAttachmentRepository.class);
        postAttachments = mock(PostAttachmentService.class);
        access = new ResourceAccess(posts, attachments, items);
        SqlSession session = mock(SqlSession.class);
        when(session.getMapper(PostRepository.class)).thenReturn(posts);
        when(session.getMapper(UserRepository.class)).thenReturn(mock(UserRepository.class));
        when(session.getMapper(PostAttachmentRepository.class)).thenReturn(attachments);
        postService = new PostServiceImpl(session, attachments, postAttachments, mock(CommentRepository.class), access);
        ReflectionTestUtils.setField(postService, "uploadDir", upload.toString());
        itemService = new ItemAttachmentServiceImpl(itemAttachments, access);
        ReflectionTestUtils.setField(itemService, "uploadDirItem", upload.toString());
        when(posts.findById(10L)).thenReturn(Post.builder().id(10L).user(User.builder().id(1L).build()).build());
        when(items.findById(20L)).thenReturn(Item.builder().id(20L).brand(Brand.builder().id(2L).build()).build());
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void user(long id) {
        authenticate(new PrincipalUserDetails(User.builder().id(id).build()));
    }
    private void brand(long id) {
        authenticate(new PrincipalBrandDetails(Brand.builder().id(id).build(), mock(BrandService.class)));
    }
    private void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "unused", List.of()));
    }
    private void denied(Runnable operation, int status) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(status));
    }

    @Test void foreignAuthorCannotUpdateEvenWithForgedPostUser() {
        user(9);
        Post forged = Post.builder().id(10L).user(User.builder().id(9L).build()).build();
        denied(() -> postService.update(forged, Map.of(), null), 403);
        verify(posts, never()).update(any());
        verifyNoInteractions(postAttachments);
    }

    @Test void anonymousAndBrandCannotUpdateOrDeletePosts() {
        denied(() -> postService.deleteById(10L), 403);
        brand(1);
        denied(() -> postService.update(Post.builder().id(10L).build(), Map.of(), null), 403);
        verify(posts, never()).update(any());
        verify(posts, never()).delete(any());
        verifyNoInteractions(postAttachments);
    }

    @Test void foreignAndMissingAttachmentRejectWholeBatchBeforeAnyMutation() throws Exception {
        user(1);
        PostAttachment own = PostAttachment.builder().id(30L).postId(10L).filename("own.png").build();
        PostAttachment other = PostAttachment.builder().id(31L).postId(11L).filename("other.png").build();
        when(attachments.findById(30L)).thenReturn(own);
        when(attachments.findById(31L)).thenReturn(other);
        Files.writeString(upload.resolve("own.png"), "keep");
        Files.writeString(upload.resolve("other.png"), "keep");
        Post target = Post.builder().id(10L).build();
        denied(() -> postService.update(target, Map.of(), new Long[]{30L, 31L}), 400);
        denied(() -> postService.update(target, Map.of(), new Long[]{30L, 999L}), 400);
        denied(() -> postService.update(target, Map.of(), new Long[]{null}), 400);
        verify(posts, never()).update(any());
        verify(attachments, never()).delete(any());
        assertThat(upload.resolve("own.png")).hasContent("keep");
        assertThat(upload.resolve("other.png")).hasContent("keep");
    }

    @Test void authorCanUpdateAndDuplicateAttachmentIsDeletedOnce() throws Exception {
        user(1);
        PostAttachment own = PostAttachment.builder().id(30L).postId(10L).filename("own.png").build();
        when(attachments.findById(30L)).thenReturn(own);
        when(posts.update(any())).thenReturn(1);
        Files.writeString(upload.resolve("own.png"), "remove");
        assertThat(postService.update(Post.builder().id(10L).content("updated").build(), Map.of(), new Long[]{30L, 30L})).isEqualTo(1);
        verify(attachments, times(1)).delete(own);
        assertThat(upload.resolve("own.png")).doesNotExist();
    }

    @Test void authorCanDeleteAndForeignAuthorCannot() {
        user(9);
        denied(() -> postService.deleteById(10L), 403);
        verifyNoInteractions(postAttachments);
        user(1);
        when(posts.delete(any())).thenReturn(1);
        assertThat(postService.deleteById(10L)).isEqualTo(1);
        verify(postAttachments).deleteByPostId(10L);
        verify(posts).delete(argThat(p -> p.getId().equals(10L)));
    }

    @Test void directPostAttachmentDeletionAlsoRequiresOwner() throws Exception {
        SqlSession session = mock(SqlSession.class);
        when(session.getMapper(PostAttachmentRepository.class)).thenReturn(attachments);
        PostAttachmentServiceImpl service = new PostAttachmentServiceImpl(session, access);
        ReflectionTestUtils.setField(service, "uploadDir", upload.toString());
        when(attachments.findById(30L)).thenReturn(PostAttachment.builder().id(30L).postId(10L).filename("own.png").build());
        Files.writeString(upload.resolve("own.png"), "keep");
        user(9);
        denied(() -> service.deleteAttachment(30L), 403);
        denied(() -> service.deleteByPostId(10L), 403);
        verify(attachments, never()).delete(any());
        assertThat(upload.resolve("own.png")).hasContent("keep");
    }

    @Test void anonymousRegularUserAndForeignBrandCannotDeleteItemFile() throws Exception {
        when(itemAttachments.findById(40L)).thenReturn(ItemAttachment.builder().id(40L).itemId(20L).filename("item.png").build());
        Files.writeString(upload.resolve("item.png"), "keep");
        denied(() -> itemService.deleteById(40L), 403);
        user(2);
        denied(() -> itemService.deleteById(40L), 403);
        brand(9);
        denied(() -> itemService.deleteById(40L), 403);
        verify(itemAttachments, never()).deleteById(any());
        assertThat(upload.resolve("item.png")).hasContent("keep");
    }

    @Test void owningBrandCanDeleteItemFile() throws Exception {
        brand(2);
        ItemAttachment attachment = ItemAttachment.builder().id(40L).itemId(20L).filename("item.png").build();
        when(itemAttachments.findById(40L)).thenReturn(attachment);
        when(itemAttachments.deleteById(attachment)).thenReturn(1);
        Files.writeString(upload.resolve("item.png"), "remove");
        assertThat(itemService.deleteById(40L)).isEqualTo(1);
        verify(itemAttachments).deleteById(attachment);
        assertThat(upload.resolve("item.png")).doesNotExist();
    }

    @Test void missingAndMalformedOwnerAssociationsFailClosed() {
        user(1);
        denied(() -> access.requirePostOwner(999L), 404);
        when(posts.findById(10L)).thenReturn(Post.builder().id(10L).build());
        denied(() -> access.requirePostOwner(10L), 403);
        brand(2);
        denied(() -> access.requireItemOwner(999L), 404);
        when(items.findById(20L)).thenReturn(Item.builder().id(20L).build());
        denied(() -> access.requireItemOwner(20L), 403);
    }
}
