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
	
	
	// - 첨부 ID로 파일 정보 조회
	@Override
	public PostAttachment findById(Long id) {
		return attachmentRepository.findById(id);
	}
	
	// - 첨부가 속한 게시글의 작성자만 삭제 가능
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
	
	// - 게시글 작성자를 확인한 뒤 연결된 첨부 삭제
	@Transactional  // - DB 작업을 묶는 설정이며 물리 파일 삭제까지 롤백되지는 않음
	@Override
	public void deleteByPostId(Long postId) {
		resourceAccess.requirePostOwner(postId);
		List<PostAttachment> fileList = attachmentRepository.findByPostId(postId);
		if (fileList != null) {
			fileList.forEach(attachment -> deleteAttachment(attachment.getId()));
		}
	}
	
}
