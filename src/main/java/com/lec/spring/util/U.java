package com.lec.spring.util;

import com.lec.spring.config.PrincipalUserDetails;
import com.lec.spring.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class U {
	
	// 현재 request 구하기
	public static HttpServletRequest getRequest() {
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
		return attrs.getRequest();
	}
	
	// 현재 session 구하기
	public static HttpSession getSession() {
		return getRequest().getSession();
	}
	
	// 현재 로그인한 사용자 User 구하기
	public static User getLoggedUser() {
		// 현재 로그인한 사용자
		// Security ContextHolder 라는 키값에다가 session 정보를 저장함
		PrincipalUserDetails principalUserDetails = (PrincipalUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		User user = principalUserDetails.getUser();
		return user;
	}

	// 첨부파일 정보(MultipartFile) 출력하기
	public static void printFileInfo(MultipartFile file) {
		
		String originalFilename = file.getOriginalFilename();
		if (originalFilename == null || originalFilename.isEmpty()) return;
		// Preserve existing decoder behavior without logging names, paths or supplied MIME.
		try (var input = file.getInputStream()) {
			BufferedImage image = ImageIO.read(input);
			log.debug("event=POST_FILE_INSPECTED sizeBytes={} decoded={}", file.getSize(), image != null);
		} catch (IOException ex) {
			log.warn("event=POST_FILE_INSPECTION_FAILED reasonCode=IMAGE_READ_FAILED exceptionType={}", ex.getClass().getSimpleName());
			throw new RuntimeException(ex);
		}
	}
}
