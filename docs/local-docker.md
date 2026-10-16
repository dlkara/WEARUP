# 로컬 Docker 실행

## 1. 구성과 현재 검증 범위

AWS 계정 없이 앱과 MySQL을 로컬 Docker로 실행하는 구성이다. 기존 RDS 접속은 prod 설정에 남기고 local 설정에서 별도 MySQL에 연결한다. 일반 회원·브랜드 로그인은 유지하며 네이버·카카오 로그인은 로컬에서 비활성화한다.

| 항목 | 구성 |
|---|---|
| 앱 | Java 17, Spring Boot, PC의 `127.0.0.1:8080` |
| DB | MySQL 8.4, PC의 `127.0.0.1:3307`, 앱에서는 `mysql:3306` |
| 데이터 | `wearup-local_mysql-data`와 `wearup-local_uploads` 볼륨 |
| 초기화 | 새 DB에 기존 스키마와 Authority·Plan 기준 데이터만 생성 |
| 소셜 로그인 | `app.oauth2.enabled=false`, 로그인 화면에서도 버튼 숨김 |

Docker Engine과 Compose가 필요하다. Docker Desktop 환경에서는 Docker Desktop을 먼저 실행한다. Compose의 `up --wait`와 BuildKit 빌드 캐시를 사용할 수 있는 버전을 사용한다. 이미지·의존성 다운로드에 인터넷 연결이 필요하며 화면의 기존 CDN·주소 검색도 외부 네트워크를 사용할 수 있다. 오프라인 실행 구성은 아니다.

앱 이미지는 JDK 빌드 단계와 JRE 실행 단계를 분리하며, 실행 시에는 일반 사용자 UID 10001을 사용한다. 운영 설정·비밀값·테스트 코드가 빌드 컨텍스트에 들어가지 않도록 `.dockerignore`를 적용한다. Dockerfile은 JAR 빌드만 담당하며 자동 테스트 통과를 대신하지 않는다.

Docker 컨테이너 실제 기동·DB CRUD·재시작 후 데이터 보존은 Docker가 있는 PC에서 확인해야 한다. Compose 구문 검사, Java 17 JAR 빌드, 선택 자동 테스트 25개는 통과했다. 자세한 범위는 [검증 기록](../verification/local-docker/README.md)에 정리한다.

## 2. 시작하기

저장소 루트에서 실행한다. 최초 이미지·의존성 다운로드는 네트워크와 PC에 따라 5~15분 이상 걸릴 수 있다.

1. 로컬 설정 파일을 복사한다.

   ```bash
   cp .env.local.example .env.local
   ```

2. `.env.local`의 `MYSQL_PASSWORD`와 `MYSQL_ROOT_PASSWORD`에 서로 다른 로컬 비밀번호를 입력한다. 빈 값이면 Compose가 시작을 거부한다. `.env.local`은 Git에서 제외되며 Docker 빌드에도 포함하지 않는다. 기본 포트가 사용 중이면 `APP_PORT` 또는 `MYSQL_PORT`를 바꾼다.

3. 앱·DB를 빌드하고 시작한다.

   ```bash
   docker compose --env-file .env.local -f compose.local.yml up --build -d --wait
   ```

4. 브라우저에서 `http://localhost:8080/login`에 접속한다. `APP_PORT`를 바꿨다면 변경한 포트로 접속한다. 미리 생성된 회원·브랜드·관리자 계정은 없다. 화면에서 일반 회원 또는 브랜드를 가입해 사용한다. 브랜드 가입에는 기존 화면의 로고 입력이 필요할 수 있다.

5. 상태와 앱 로그를 확인한다.

   ```bash
   docker compose --env-file .env.local -f compose.local.yml ps
   docker compose --env-file .env.local -f compose.local.yml logs --tail=100 app
   ```

앱 healthcheck는 로그인 페이지의 HTTP 응답만 확인한다. MySQL healthcheck는 앱용 계정의 TCP 연결과 Authority 테이블 조회를 확인한다. 둘 다 healthy라도 실제 회원가입·로그인·게시글·이미지 처리를 완료했다고 볼 수는 없다.

## 3. 종료·재시작·데이터 확인

| 목적 | 명령 |
|---|---|
| 일시 중지 | `docker compose --env-file .env.local -f compose.local.yml stop` |
| 다시 실행 | `docker compose --env-file .env.local -f compose.local.yml up -d --wait` |
| 앱만 재시작 | `docker compose --env-file .env.local -f compose.local.yml restart app` |
| 컨테이너 정리·볼륨 유지 | `docker compose --env-file .env.local -f compose.local.yml down` |
| DB 로그 | `docker compose --env-file .env.local -f compose.local.yml logs --tail=100 mysql` |

`down`에는 `-v`를 붙이지 않는다. `-v`는 DB와 업로드 볼륨까지 삭제하므로 일반 종료·재배포에 필요하지 않다. 예전 SQL의 DELETE/DROP 초기화 명령도 기존 자료가 있는 DB에 수동 실행하지 않는다.

MySQL 초기화 SQL과 `.env.local`의 최초 계정 생성 설정은 빈 DB 볼륨에서만 적용된다. 기존 볼륨이 있으면 비밀번호 파일만 바꾸어도 DB 계정 비밀번호가 자동 변경되지는 않는다. 접속 오류가 나면 기존 볼륨 여부와 실제 DB 계정을 먼저 확인한다. 데이터를 지우는 초기화는 정상 실행 문제 해결의 기본 단계로 사용하지 않는다.

DB를 직접 살펴볼 때는 아래 명령을 사용하고 프롬프트에 `.env.local`의 앱용 비밀번호를 입력한다.

```bash
docker compose --env-file .env.local -f compose.local.yml exec mysql sh -c 'mysql -u"$MYSQL_USER" -p "$MYSQL_DATABASE"'
```

입력 예:

```sql
SELECT grade FROM Authority;
SELECT type, price, count FROM Plan;
SELECT COUNT(*) FROM Post;
SELECT COUNT(*) FROM Attachment;
```

업로드 파일은 다음 명령으로 확인한다.

```bash
docker compose --env-file .env.local -f compose.local.yml exec app ls -l upload/post upload/item upload/brand
```

이미지를 등록한 뒤 `restart app` 또는 볼륨을 유지한 `down`→`up`을 수행하고 화면과 파일이 유지되는지 확인한다. 파일과 DB를 각각 볼륨에 저장하는 것이 두 저장소의 트랜잭션 원자성을 의미하지는 않는다.

## 4. 선택 검증과 호스트 개발 실행

테스트 코드는 `verification/`에 분리한다. JDK 17 환경에서 아래 명령으로 기존 권한 검사와 로컬 프로필 검증을 연결한다.

```bash
SPRING_CONFIG_LOCATION=classpath:/application.properties \
bash gradlew -I verification/local-docker/verification.gradle test \
  --tests 'com.lec.spring.security.*' --tests 'com.lec.spring.local.*'
```

이 명령은 로컬 프로필 시작·소셜 로그인 숨김·업로드 폴더 생성/제공과 기존 권한·로그 회귀를 검사한다. 로컬 프로필 시작 테스트는 실제 MySQL 없이 실행하도록 구성했으므로 실제 DB CRUD 테스트를 대체하지 않는다. 전체 테스트에는 기존 contextLoads의 실행 설정 정비가 별도로 필요하다.

MySQL만 Docker로 실행하고 앱은 IDE/JDK에서 실행할 수도 있다.

```bash
docker compose --env-file .env.local -f compose.local.yml up -d --wait mysql
```

IDE 실행 환경에 `SPRING_CONFIG_LOCATION=classpath:/application.properties,classpath:/application-local.yml`, `SPRING_PROFILES_ACTIVE=local`, `MYSQL_PASSWORD`를 설정한다. DB명·계정·포트를 바꿨다면 `MYSQL_DATABASE`, `MYSQL_USER`, `DB_PORT`도 맞춘다. `.env.local`은 Compose가 읽는 파일이며 IDE가 자동으로 읽는다고 가정하지 않는다.

호스트 기본 업로드 경로는 작업 폴더의 `upload/`다. Docker 앱의 업로드 볼륨과 자동으로 공유되지 않는다. 동일 DB를 연결하더라도 실행 방식에 따라 파일 위치가 다를 수 있으므로 호스트 실행과 Docker 실행을 섞을 때는 업로드 저장소를 구분한다.

업로드 경로는 `UPLOAD_POST_PATH`, `UPLOAD_ITEM_PATH`, `UPLOAD_BRAND_PATH`로 지정할 수 있다. 상대 경로는 앱의 작업 폴더 기준이며, 폴더가 없으면 앱 시작 시 생성한다. Compose에서 별도 경로를 지정한다면 해당 경로의 볼륨 마운트도 같이 맞춰야 한다.

## 5. 다음 확인

| 순서 | PC에서 확인할 동작 | 기대 결과 |
|---|---|---|
| 1 | 최초 실행·로그인 페이지 접속 | 앱·DB healthy, 소셜 로그인 버튼 숨김 |
| 2 | 일반 회원·브랜드 가입 및 로그인 | DB 저장·비밀번호 확인·역할별 이동 정상 |
| 3 | 게시글·상품 이미지 등록·수정·삭제 | DB와 파일·화면에 같은 결과 반영 |
| 4 | 다른 계정·브랜드로 변경 요청 | 권한 없는 변경 차단 |
| 5 | 앱/컨테이너 재생성 | 기존 DB와 업로드 파일 유지 |

현재 로컬 구성에서도 PR #2의 후속 보안 과제는 남아 있다. CSRF·실제 이미지 검증·HTML 정화·DB/파일 실패 정합성이 모두 해결된 상태는 아니다. 포트는 PC 루프백에만 바인딩하므로 이 구성을 그대로 외부 서비스 공개용으로 사용하지 않는다.

참고 문서:

- [Docker Compose 시작 순서](https://docs.docker.com/compose/how-tos/startup-order/)
- [MySQL 공식 이미지 초기화·환경 변수](https://hub.docker.com/_/mysql)
- [Spring Boot 3.4 외부 설정](https://docs.spring.io/spring-boot/3.4/reference/features/external-config.html)
