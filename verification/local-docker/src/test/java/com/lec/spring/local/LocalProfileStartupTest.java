package com.lec.spring.local;

import com.lec.spring.config.oauth.PrincipalOauth2UserService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/application.properties,classpath:/application-local.yml",
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unused",
        "MYSQL_PASSWORD=verification-only"
})
class LocalProfileStartupTest {
    private static final Path UPLOAD_ROOT = temporaryDirectory();
    @Autowired ApplicationContext context;
    @Autowired TestRestTemplate http;

    @DynamicPropertySource
    static void uploads(DynamicPropertyRegistry properties) {
        properties.add("app.upload.path.post", () -> UPLOAD_ROOT.resolve("post").toString());
        properties.add("app.upload.path.item", () -> UPLOAD_ROOT.resolve("item").toString());
        properties.add("app.upload.path.brand", () -> UPLOAD_ROOT.resolve("brand").toString());
    }

    @Test void localStartupDoesNotRequireOauthKeysOrPassword() {
        // - DB CRUD 검증이 아니라 OAuth 설정 없이 앱이 시작되는지 확인
        assertThat(context.getBeansOfType(PrincipalOauth2UserService.class)).isEmpty();
        assertThat(context.getBeansOfType(ClientRegistrationRepository.class)).isEmpty();
    }

    @Test void loginPageKeepsFormAndHidesDisabledSocialLogin() {
        var response = http.getForEntity("/login", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("name=\"username\"", "name=\"password\"")
                .doesNotContain("/oauth2/authorization/naver", "/oauth2/authorization/kakao");
    }

    @Test void uploadFoldersAreCreatedAndServedFromTheSameLocation() throws IOException {
        assertThat(UPLOAD_ROOT.resolve("post")).isDirectory();
        assertThat(UPLOAD_ROOT.resolve("item")).isDirectory();
        assertThat(UPLOAD_ROOT.resolve("brand")).isDirectory();
        // - 새 로컬 볼륨의 첫 업로드가 실패하지 않도록 폴더 생성·조회 연결 확인
        Files.writeString(UPLOAD_ROOT.resolve("post/location-check.txt"), "location-ok");
        var response = http.getForEntity("/upload/post/location-check.txt", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("location-ok");
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("wearup-local-test-");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @AfterAll static void cleanup() throws IOException {
        try (var paths = Files.walk(UPLOAD_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
