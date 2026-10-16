package com.lec.spring.service;

import com.lec.spring.domain.PostAttachment;
import com.lec.spring.security.ResourceAccess;
import lombok.extern.slf4j.Slf4j;
import com.lec.spring.repository.PostAttachmentRepository;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
public class PostAttachmentServiceImpl implements PostAttachmentService {
	
	@Value("${app.upload.path.post}")
	private String uploadDir;
	
	private final PostAttachmentRepository attachmentRepository;
	private final ResourceAccess resourceAccess;
	
	public PostAttachmentServiceImpl(SqlSession sqlSession, ResourceAccess resourceAccess) {
		this.attachmentRepository = sqlSession.getMapper(PostAttachmentRepository.class);
		this.resourceAccess = resourceAccess;
	}
	
	
	// 특정 게시글에 연결된 첨부파일 가져오기
	@Override
	public PostAttachment findById(Long id) {
		return attachmentRepository.findById(id);
	}
	
	// 특정 첨부파일 삭제
	@Override
	public void deleteAttachment(Long id) {
		PostAttachment attachment = attachmentRepository.findById(id);
		
		if (attachment != null) {
			resourceAccess.requirePostOwner(attachment.getPostId());
			Path file = Paths.get(uploadDir, attachment.getFilename());
			try {
				Files.deleteIfExists(file);
			} catch (IOException e) {
				log.error("event=POST_FILE_DELETE_FAILED resourceId={} reasonCode=CLEANUP_FAILED exceptionType={}", id, e.getClass().getSimpleName());
				throw new RuntimeException(e);
			}
			attachmentRepository.delete(attachment);
		}
	}
	
	// 특정 게시글에 연결된 모든 첨부파일 삭제
	@Transactional  // 하나의 트랜잭션으로 묶기 (모두 다 commit 되거나 rollback 되거나)
	@Override
	public void deleteByPostId(Long postId) {
		resourceAccess.requirePostOwner(postId);
		List<PostAttachment> fileList = attachmentRepository.findByPostId(postId);
		if (fileList != null) {
			fileList.forEach(attachment -> deleteAttachment(attachment.getId()));
		}
	}
	
}
