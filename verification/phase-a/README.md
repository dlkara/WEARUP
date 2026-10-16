# 첫 번째 묶음 — 권한 검사·기본 로그 변경 결과

## 코드 분리

서비스에 필요한 권한 검사·요청 ID·로그 코드는 `src/main`에 남겼다. 이번에 추가한 테스트 3개 클래스는 `verification/phase-a/src/test/java`로 옮겼으며 기본 소스 경로에서는 제외된다. 이 폴더와 실행 스크립트는 서비스 배포에 필요하지 않다. 기존 저장소에 있던 `WearupApplicationTests`와 기존 Gradle 의존성은 변경하지 않았다.

검증 코드는 다음 명령으로만 추가해 실행한다. `verification.gradle`은 명시적으로 전달할 때 테스트 소스만 연결하며 서비스의 소스·의존성·보안 설정을 바꾸지 않는다.

```bash
bash gradlew --init-script verification/phase-a/verification.gradle test --tests 'com.lec.spring.security.*'
```

일반 서비스 빌드는 기존대로 `bash gradlew bootJar`를 사용한다. Spring Boot의 배포 JAR에는 기본적으로 테스트 클래스가 들어가지 않으며, 이번 구조에서는 추가 테스트가 기본 테스트 컴파일 경로에서도 분리된다.

분리 작업에서 테스트 3개 파일은 이동 전과 바이트 단위로 동일함을 확인했고, `src/main`과 `build.gradle`은 이전 커밋에서 바뀌지 않았음을 확인했다. 이전 실행의 `build/` 결과와 프로젝트 `.gradle/` 캐시는 정리했다. 아래 22개 통과 결과는 분리 전 검증 결과이며, 분리 후에는 임시 JDK·Gradle 실행 환경이 없어 테스트와 배포 JAR 빌드를 재실행하지 못했다. 새 init-script 실행 검증은 JDK 17 환경에서 필요하다.

## 상태

기준 코드: `785a2d718157c7d8f58aaa3dc77f3a970a8d1082`.

판정: **blocked — 수정 코드는 구현했고 추가 테스트 22개는 통과했으나, 전체 애플리케이션 검증은 완료하지 못했다.** 운영 적용 또는 완전한 해결을 확정하는 결과가 아니다.

수정 전·후의 서비스/HTTP 계층을 격리된 테스트로 확인했다. 실제 사용자 DB, 로그인 서버, 실제 사용 브라우저와 운영 배포는 사용하지 않았다. SQL 변경은 없다. 소유권은 기존 MyBatis 조회 결과와 인증 주체를 비교한다.

## 수정한 경계

| 문제 | 적용 위치 | 동작 |
|---|---|---|
| 게시글 수정·삭제의 작성자 검사 부족 | `ResourceAccess`, `PostServiceImpl`, `PostController` | DB 작성자와 인증된 일반 사용자의 ID를 비교하고 변경 전에 차단 |
| 요청의 `user.id`가 로그인 User 객체를 변경 | `PostController` | 새 입력 모델에 `id/content/items`만 바인딩, principal User 주입 제거 |
| 다른 게시글의 첨부 ID 삭제 | `ResourceAccess.requirePostAttachments`, `PostServiceImpl.update` | 삭제 목록 전체를 먼저 검사하고 중복 제거, 다른 소속·없는 ID·null ID 거부 |
| 독립 첨부 서비스의 무권한 삭제 | `PostAttachmentServiceImpl` | 공개 삭제 메서드에서도 DB 작성자 검사 |
| 비로그인 상품 첨부 삭제 | `SecurityConfig` | 첨부 경로는 BRAND 권한 필수, 기존 상품 GET 조회는 공개 유지 |
| 직접 삭제와 상품 수정의 이미지 교체 경로 | `ItemAttachmentServiceImpl`, 두 상품 컨트롤러 | DB 브랜드 검사 후 서비스에서 물리 파일 삭제, 상품 수정도 검증·변경 전에 소유권 확인 |
| 중복 삭제 ID로 잘못된 이미지 개수 계산 | `PostController` | 검사·중복 제거된 삭제 목록으로 계산, 1 미만 거부 |

일반 사용자와 브랜드는 서로 다른 principal 유형으로 검사한다. 관리자 우회 권한은 새로 추가하지 않았다. 원래 작성자·소유 브랜드의 수정/삭제와 기존 응답 화면·리다이렉트는 유지한다.

입력 모델 변경으로 오류 폼에서 작성자 정보가 빠지는 회귀를 별도 읽기 전용 검토에서 발견했다. 바인딩과 권한 확인이 끝난 뒤 DB에서 읽은 작성자 정보를 표시 모델에 복원했다. 오류 폼의 실제 Thymeleaf 렌더링과 `수정 완료` 버튼 존재를 테스트한다.

## 기본 로그

`RequestIdFilter`는 클라이언트 헤더 대신 서버 UUID를 생성하고 응답 `X-Request-ID`와 MDC에 연결한다. 요청 종료·예외에서 MDC를 정리/복원한다. 권한 거부와 첨부 소속 오류에는 숫자 ID·사유 코드를 남긴다. 요청/파일 처리 실패에는 단계·예외 클래스명만 남기고, 입력 본문·파일명·경로·비밀번호·토큰·예외 메시지 전체는 새 로그에 포함하지 않는다.

`MutationLog`는 DB 트랜잭션이 있으면 커밋 후 성공을 기록한다. 트랜잭션이 없는 기존 수정 흐름에서는 해당 메서드 완료 시 기록하며 파일과 DB의 원자성을 보장하지 않는다. 기존 브랜드 비밀번호 출력도 제거했다. `U.printFileInfo`는 기존 디코더 호출을 유지하고 위험한 원문 출력만 바꿨다. 이번 변경이 이미지 내용 검증을 새로 완성한 것은 아니다.

처리되지 않은 예외에서는 요청 필터가 컨테이너 오류 처리 이전의 상태 코드를 관찰할 수 있다. 이 경우 `status`와 `failed=true`를 함께 읽어야 한다. 로그 보관·순환·알림과 전체 기능의 로그 정비는 후속 작업이다.

## 검증 결과

| 검증 | 결과 |
|---|---|
| `git diff --check` | 통과 |
| Java 17 컴파일 및 테스트 컴파일 | 통과 |
| `OwnershipServiceTest` 9개 | 통과: 권한·첨부 소속·실제 임시 파일 보존/삭제·정상 소유자 처리 |
| `OwnershipHttpTest` 10개 | 통과: 실제 Security 설정, 비로그인/일반 사용자 차단, 바인딩 위조, 정상 상품 조회·브랜드 수정·삭제, 오류 폼 실제 렌더링 |
| `RequestLoggingTest` 3개 | 통과: UUID·MDC·민감정보 제외·커밋 후 성공 이벤트 |
| 전체 테스트 23개 | 22개 통과, 기존 `WearupApplicationTests.contextLoads` 1개 실패 |

전체 테스트 실패 원인은 설정에 `spring.security.user.password`가 없어 `PrincipalOauth2UserService`를 생성하지 못한 것이다. **수정 전 기준 코드에서도 동일한 contextLoads 실패를 확인했다.** 이 설정 하나를 채우면 전체 실행이 된다고 보장하지 않는다. DB·업로드 경로·OAuth 설정 등 남은 환경 요구사항을 확인해야 한다.

### 수정 전 재현

별도 임시 worktree의 원본 서비스·컨트롤러로 다음 세 동작을 재현했다. 재현 테스트는 각각 취약한 동작이 실제로 발생했는지를 확인했고 3개 모두 그 동작을 관찰했다.

1. 로그인 User ID가 9인 상태에서 `WebDataBinder`로 `user.id=1`을 전달하면 로그인 객체 ID가 1로 바뀜.
2. 일반 사용자 principal로 타인 게시글 수정 서비스를 호출하면 게시글 update 저장소가 호출됨.
3. 게시글 10 수정에 게시글 11의 첨부 ID를 전달하면 해당 첨부 DB 삭제와 임시 폴더 내 실물 파일 삭제가 발생함.

수정본에서는 동일 공격 유형이 403 또는 400으로 거부되고 저장소 변경·실물 삭제가 발생하지 않음을 테스트한다. HTTP의 비로그인 상품 삭제는 로그인 화면으로 이동하며, 로그인한 일반 사용자는 403이다. 정상 작성자와 소유 브랜드의 성공 대조군도 포함한다.

### 실행 명령과 환경

프로젝트 표준 명령은 다음과 같다. JDK 17과 프로젝트 설정이 필요하다.

```bash
bash gradlew --init-script verification/phase-a/verification.gradle test --tests 'com.lec.spring.security.*'
bash gradlew test
```

이번 실행 환경에는 JRE만 있어 임시 JDK 17을 설치했다. Gradle/Maven 다운로드에는 환경 프록시와 시스템 CA 저장소를 지정했고, Mockito의 자체 JVM attach가 제한되어 Byte Buddy agent를 테스트 JVM 시작 시 주입했다. 저장소의 Gradle 빌드 설정이나 보안 설정을 약화해 테스트를 통과시키지는 않았다.

분리 전 실제 실행은 위 환경을 설정한 임시 래퍼 `python3 /tmp/wearup-gradle.py test --tests 'com.lec.spring.security.*'` 및 `python3 /tmp/wearup-gradle.py test`로 수행했다. 원본 대조군은 동일 래퍼로 `test --tests '*OriginalBoundaryReproductionTest' --tests '*WearupApplicationTests'`를 수행했다. 이 임시 경로는 사용자 환경의 실행 필수 요소가 아니다. 분리 후 검증에서는 위 opt-in 명령에 해당하는 init-script 옵션을 추가한다.

## 남은 작업

1. 로컬/테스트용 설정과 DB를 연결하고 전체 contextLoads 및 실제 DB·브라우저 흐름 검증.
2. 이미지 실제 형식·디코딩·재인코딩·저장명·경로·자원 제한과 검증/저장 파일 목록 통일.
3. 본문 HTML 정화, CSRF 복구, 파일·DB 실패 정합성, 브랜드 로고 등 나머지 경로 보완.

2~3은 이후 묶음이며 이번 커밋에서 해결됐다고 간주하지 않는다. 특히 CSRF 전역 비활성화는 그대로이므로 이번 소유권 검사만으로 다른 사용자의 브라우저를 통한 요청까지 보호했다고 볼 수 없다. 동시 수정과 파일/DB 실패의 정합성도 후속 검증 대상이다.
