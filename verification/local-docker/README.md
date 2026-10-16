# 로컬 Docker 구성 검증

## 실행

JDK 17 환경에서 저장소 루트 기준으로 실행한다.

```bash
SPRING_CONFIG_LOCATION=classpath:/application.properties \
bash gradlew -I verification/local-docker/verification.gradle bootJar test \
  --tests 'com.lec.spring.security.*' --tests 'com.lec.spring.local.*'
```

- 서비스 JAR은 `src/main`으로 빌드하며, 추가 검증은 선택한 init script에서만 연결한다.
- 기존 `build.gradle`과 `src/test`는 변경하지 않는다.
- 로컬 검증은 임시 업로드 폴더를 사용하고 완료 후 삭제한다.

## 확인 결과

| 검사 | 결과 |
|---|---|
| Compose 설정 파싱·환경 변수 치환 | 통과, 실제 비밀번호 대신 검사 전용 값 사용 |
| Java 17 `bootJar` | 통과 |
| 로컬 프로필 시작·로그인 화면·업로드 경로 | 3개 통과 |
| 기존 권한 HTTP 검사 | 10개 통과 |
| 기존 소유권 서비스 검사 | 9개 통과 |
| 기존 요청·변경 로그 검사 | 3개 통과 |
| 테스트 클래스의 JAR 포함 여부 | 포함되지 않음 |
| `git diff --check` | 통과 |

로컬 시작 검사는 실제 내장 HTTP 서버를 실행한다. OAuth 키 없이 시작하고, 일반 로그인 입력란이 렌더링되며 소셜 로그인 링크는 숨겨지는지 확인한다. 임시 업로드 폴더 생성과 해당 경로의 파일 HTTP 응답도 확인한다.

## 아직 확인하지 않은 범위

- 이 환경에는 Docker 데몬이 없어 이미지 빌드와 Compose 컨테이너 기동은 실행하지 못했다.
- 테스트의 MySQL 주소는 사용하지 않는 포트로 지정한다. DB 접속·스키마 초기화·회원가입·로그인 인증·실제 이미지 처리·볼륨 보존을 검증한 결과가 아니다.
- 기존 전체 `contextLoads` 검사는 이번 선택 실행 대상에 포함하지 않았다. 전체 테스트 통과로 해석하지 않는다.
- Mockito 실행을 위해 이 환경에서만 사용한 임시 Java agent·JDK·실행 스크립트는 저장소에 포함하지 않는다.

컨테이너 실행과 나중에 할 수동 확인은 [로컬 실행 안내](../../docs/local-docker.md)를 따른다.
