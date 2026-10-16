package com.lec.spring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
	
	@Value("${app.upload.path.post}")
	private String uploadDir;   // "/upload/post"
	
	@Value("${app.upload.path.brand}")
	private String uploadDirBrand;
	
	@Value("${app.upload.path.item}")
	private String uploadDirItem;
	
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		System.out.println("✅ MvcConfig.addResourceHandlers() 호출");
		
		// 설정한 URL 로 request 받으면 "upload/" 경로의 static resource 가 응답되도록 함.
		registry
				.addResourceHandler("/upload/post/**")
				.addResourceLocations(uploadLocation(uploadDir));
		
		registry.addResourceHandler("/upload/item/**")
				.addResourceLocations(uploadLocation(uploadDirItem));
		
		registry.addResourceHandler("/upload/brand/**")
				.addResourceLocations(uploadLocation(uploadDirBrand));
		
		// Chrome DevTools 가 가져가려는
		// .well-known/appspecific/com.chrome.devtools.json 파일의 위치를 정적 리소스로 처리
		//      최근 Chrome 에서 “앱으로 설치”(Add to Home screen) 등 PWA 관련 기능이 강화되면서,
		//      브라우저가 자동으로 /.well-known/appspecific/com.chrome.devtools.json
		//      (또는 /appspecific/com.chrome.devtools.json) 를 확인하게 됨)
		registry.addResourceHandler("/.well-known/**", "/appspecific/**")
				.addResourceLocations("classpath:/static/.well-known/",
						"classpath:/static/.well-known/appspecific/");
	}

    // - PC와 컨테이너에서 같은 경로를 사용하도록 절대 경로로 변환
    // - 게시글 첫 업로드 전에 필요한 폴더도 생성
    private String uploadLocation(String directory) {
        try {
            Path path = Path.of(directory).toAbsolutePath().normalize();
            Files.createDirectories(path);
            return path.toUri().toString();
        } catch (IOException ex) {
            throw new UncheckedIOException("업로드 폴더를 준비할 수 없습니다", ex);
        }
    }

}
