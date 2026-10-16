package com.lec.spring.security;

import com.lec.spring.config.*;
import com.lec.spring.config.oauth.PrincipalOauth2UserService;
import com.lec.spring.controller.*;
import com.lec.spring.domain.*;
import com.lec.spring.repository.*;
import com.lec.spring.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PostController.class, ItemAttachmentController.class, BrandItemController.class})
@org.springframework.test.context.ContextConfiguration(classes = {PostController.class, ItemAttachmentController.class, BrandItemController.class, ItemController.class})
@Import({SecurityConfig.class, ResourceAccess.class, PostAttachmentValidator.class, RequestIdFilter.class, OwnershipHttpTest.Views.class})
class OwnershipHttpTest {
    // Exercise routing, security and binding without requiring browser template fixtures.
    @org.springframework.boot.test.context.TestConfiguration
    static class Views {
        @org.springframework.context.annotation.Bean
        org.springframework.web.servlet.ViewResolver testViews() {
            return new TestViews();
        }
    }
    static class TestViews implements org.springframework.web.servlet.ViewResolver, org.springframework.core.Ordered {
        public int getOrder() { return org.springframework.core.Ordered.HIGHEST_PRECEDENCE; }
        public org.springframework.web.servlet.View resolveViewName(String name, java.util.Locale locale) {
            if (name.equals("post/update") || name.startsWith("redirect:")) return null;
            return new org.springframework.web.servlet.view.AbstractView() {
                protected void renderMergedOutputModel(java.util.Map<String, Object> model,
                        jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) { }
            };
        }
    }
    @Autowired MockMvc mvc;
    @MockitoBean PostService postService;
    @MockitoBean ItemService itemService;
    @MockitoBean UserService userService;
    @MockitoBean ItemAttachmentService itemAttachments;
    @MockitoBean PostRepository posts;
    @MockitoBean PostAttachmentRepository attachments;
    @MockitoBean ItemRepository items;
    @MockitoBean LoginFailureHandler loginFailureHandler;
    @MockitoBean LoginSuccessHandler loginSuccessHandler;
    @MockitoBean PrincipalOauth2UserService oauthUserService;
    @MockitoBean CustomOAuth2SuccessHandler oauthSuccessHandler;
    @MockitoBean ClientRegistrationRepository registrations;

    @BeforeEach void setup() {
        Post post = Post.builder().id(10L).content("original").items("shirt")
                .user(User.builder().id(1L).build())
                .fileList(List.of(PostAttachment.builder().id(30L).postId(10L).build(),
                        PostAttachment.builder().id(31L).postId(10L).build())).build();
        when(posts.findById(10L)).thenReturn(post);
        when(postService.detail(10L)).thenReturn(post);
        when(attachments.findById(30L)).thenReturn(post.getFileList().get(0));
        when(items.findById(20L)).thenReturn(Item.builder().id(20L).brand(Brand.builder().id(2L).build()).build());
    }

    private RequestPostProcessor user(long id) {
        return user(new PrincipalUserDetails(User.builder().id(id).username("test").build()));
    }
    private RequestPostProcessor user(PrincipalUserDetails principal) {
        return authentication(new UsernamePasswordAuthenticationToken(principal, "unused",
                List.of(new SimpleGrantedAuthority("USER"))));
    }
    private RequestPostProcessor brand(long id) {
        PrincipalBrandDetails principal = new PrincipalBrandDetails(Brand.builder().id(id).build(), mock(BrandService.class));
        return authentication(new UsernamePasswordAuthenticationToken(principal, "unused",
                List.of(new SimpleGrantedAuthority("BRAND"))));
    }

    @Test void anonymousItemAttachmentDeletionRequiresLoginAndRequestId() throws Exception {
        mvc.perform(post("/item/attachment/delete").param("id", "40"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"))
                .andExpect(header().exists("X-Request-ID"));
        verifyNoInteractions(itemAttachments);
    }

    @Test void regularUserCannotUseBrandAttachmentEndpoint() throws Exception {
        mvc.perform(post("/item/attachment/delete").param("id", "40").with(user(1)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(itemAttachments);
    }

    @Test void foreignPostUpdateAndInvalidFormAreRejectedWithoutMutation() throws Exception {
        mvc.perform(multipart("/post/update").param("id", "10").param("content", "edited").param("items", "shirt").with(user(9)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/post/update").param("id", "10").with(user(9)))
                .andExpect(status().isForbidden());
        verify(postService, never()).update(any(), any(), any());
    }

    @Test void nestedUserBindingCannotChangePrincipalOrBypassOwnership() throws Exception {
        PrincipalUserDetails principal = new PrincipalUserDetails(User.builder().id(9L).username("test").build());
        mvc.perform(multipart("/post/update").param("id", "10").param("content", "edited").param("items", "shirt")
                        .param("user.id", "1").param("fileList[0].postId", "99").with(user(principal)))
                .andExpect(status().isForbidden());
        assertThat(principal.getUser().getId()).isEqualTo(9L);
        verify(postService, never()).update(any(), any(), any());
    }

    @Test void authorUpdatePreservesViewAndDeduplicatesDeletionIds() throws Exception {
        when(postService.update(any(), any(), any())).thenReturn(1);
        mvc.perform(multipart("/post/update").param("id", "10").param("content", "edited").param("items", "shirt")
                        .param("delFile", "30", "30").with(user(1)))
                .andExpect(status().isOk()).andExpect(view().name("post/updateOk"));
        verify(postService).update(argThat(p -> p.getContent().equals("edited") && p.getId().equals(10L)), any(), aryEq(new Long[]{30L}));
    }

    @Test void unknownAttachmentAndRemovalOfAllImagesAreRejected() throws Exception {
        mvc.perform(multipart("/post/update").param("id", "10").param("content", "edited").param("items", "shirt")
                        .param("delFile", "999").with(user(1)))
                .andExpect(status().isBadRequest());
        when(attachments.findById(31L)).thenReturn(PostAttachment.builder().id(31L).postId(10L).build());
        mvc.perform(multipart("/post/update").param("id", "10").param("content", "edited").param("items", "shirt")
                        .param("delFile", "30", "31").with(user(1)))
                .andExpect(status().isOk()).andExpect(view().name("post/update"))
                .andExpect(model().attribute("post", org.hamcrest.Matchers.hasProperty("user",
                        org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(1L)))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("수정 완료")));
        verify(postService, never()).update(any(), any(), any());
    }

    @Test void foreignBrandCannotReachReplacementOrValidationErrorView() throws Exception {
        mvc.perform(multipart("/brand/item/update")
                        .file(new MockMultipartFile("itemImage", "new.png", "image/png", new byte[]{1}))
                        .param("id", "20").param("name", "shirt").with(brand(9)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(itemService, itemAttachments);
    }

    @Test void authorCanDeleteAndOtherUserCannot() throws Exception {
        mvc.perform(post("/post/delete").param("id", "10").with(user(9)))
                .andExpect(status().isForbidden());
        verify(postService, never()).deleteById(any());
        when(postService.deleteById(10L)).thenReturn(1);
        mvc.perform(post("/post/delete").param("id", "10").with(user(1)))
                .andExpect(status().isOk()).andExpect(view().name("post/deleteOk"));
        verify(postService).deleteById(10L);
    }
    @Test void publicItemListAndOwningBrandUpdateRemainAvailable() throws Exception {
        when(itemService.list()).thenReturn(List.of());
        mvc.perform(get("/item/list")).andExpect(status().isOk()).andExpect(view().name("item/list"));
        mvc.perform(multipart("/brand/item/update").param("id", "20").param("name", "shirt")
                        .param("description", "cotton").param("category", "top").param("statusItem", "A").with(brand(2)))
                .andExpect(status().isOk()).andExpect(view().name("brand/item/updateOk"));
        verify(itemService).update(argThat(item -> item.getId().equals(20L) && item.getBrand().getId().equals(2L)));
    }

    @Test void owningBrandCanUseDirectAttachmentDeleteEndpoint() throws Exception {
        when(itemAttachments.findById(40L)).thenReturn(ItemAttachment.builder().id(40L).itemId(20L).build());
        mvc.perform(post("/item/attachment/delete").param("id", "40").with(brand(2)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/brand/item/update/20"));
        verify(itemAttachments).deleteById(40L);
    }

}
