package com.lec.spring.security;

import com.lec.spring.config.PrincipalBrandDetails;
import com.lec.spring.config.PrincipalUserDetails;
import com.lec.spring.domain.Item;
import com.lec.spring.domain.Post;
import com.lec.spring.domain.PostAttachment;
import com.lec.spring.repository.ItemRepository;
import com.lec.spring.repository.PostAttachmentRepository;
import com.lec.spring.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * - 요청에 담긴 작성자·브랜드 정보는 권한 판단에 사용하지 않음
 * - 로그인한 계정과 DB에 저장된 소유자를 비교
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceAccess {
    private final PostRepository postRepository;
    private final PostAttachmentRepository postAttachmentRepository;
    private final ItemRepository itemRepository;

    // - 일반 사용자 계정으로 본인 게시글을 변경하는지 확인
    public Post requirePostOwner(Long postId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof PrincipalUserDetails principal
                && principal.getUser() != null ? principal.getUser().getId() : null;
        if (actorId == null) throw denied("POST", postId, null);
        Post stored = postId == null ? null : postRepository.findById(postId);
        if (stored == null) throw missing("POST", postId, actorId);
        if (stored.getUser() == null || !actorId.equals(stored.getUser().getId())) {
            throw denied("POST", postId, actorId);
        }
        return stored;
    }

    // - 브랜드 계정으로 본인 상품을 변경하는지 확인
    public Item requireItemOwner(Long itemId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof PrincipalBrandDetails principal
                && principal.getBrand() != null ? principal.getBrand().getId() : null;
        if (actorId == null) throw denied("ITEM", itemId, null);
        Item stored = itemId == null ? null : itemRepository.findById(itemId);
        if (stored == null) throw missing("ITEM", itemId, actorId);
        if (stored.getBrand() == null || !actorId.equals(stored.getBrand().getId())) {
            throw denied("ITEM", itemId, actorId);
        }
        return stored;
    }

    /**
     * - 중복 삭제 ID는 한 번만 처리
     * - 다른 게시글의 첨부나 없는 ID가 섞이면 전체 요청 거부
     * - 일부만 삭제되는 일이 없도록 변경 전에 전체 목록 확인
     */
    public List<PostAttachment> requirePostAttachments(Long postId, Long[] ids) {
        Post stored = requirePostOwner(postId);
        List<PostAttachment> attachments = new ArrayList<>();
        if (ids == null) return attachments;
        for (Long id : new LinkedHashSet<>(Arrays.asList(ids))) {
            PostAttachment attachment = id == null ? null : postAttachmentRepository.findById(id);
            if (attachment == null || !postId.equals(attachment.getPostId())) {
                log.warn("event=ACCESS_DENIED resource=POST_ATTACHMENT resourceId={} actorId={} reasonCode=ATTACHMENT_SCOPE_INVALID",
                        id, stored.getUser().getId());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ATTACHMENT_SCOPE_INVALID");
            }
            attachments.add(attachment);
        }
        return attachments;
    }

    private ResponseStatusException denied(String resource, Long id, Long actorId) {
        log.warn("event=ACCESS_DENIED resource={} resourceId={} actorId={} reasonCode=RESOURCE_ACCESS_DENIED",
                resource, id, actorId);
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "RESOURCE_ACCESS_DENIED");
    }

    private ResponseStatusException missing(String resource, Long id, Long actorId) {
        log.info("event=RESOURCE_MISSING resource={} resourceId={} actorId={} reasonCode=RESOURCE_NOT_FOUND",
                resource, id, actorId);
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
}
