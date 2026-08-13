# WEARUP

> 지속 가능한 패션 소비를 위한 의류 구독·렌탈 웹 서비스

브랜드와 사용자를 연결해 의류 등록·재고 관리, 구독 기반 대여·반납, 커뮤니티와 관리자 기능을 제공하는 Spring Boot 기반 팀 프로젝트입니다.

## 주요 기능

| 영역 | 기능 |
| --- | --- |
| 사용자 | OAuth2 로그인·회원가입, 마이페이지, 구독·대여·결제 조회 |
| 브랜드 | 상품 등록·수정, 재고 및 배송 상태 관리 |
| 렌탈 | 구독 플랜 기반 대여·반납 처리 |
| 커뮤니티 | 게시글·첨부파일·댓글 CRUD |
| 관리자 | 매출 집계, 회원 관리 |
| 인프라 | AWS EC2 배포, RDS 연동 |

## Tech Stack

| Category | Stack |
| --- | --- |
| Backend | `Java 17` `Spring Boot 3.4` `Spring Security` `MyBatis` |
| Frontend | `Thymeleaf` `JavaScript` `jQuery` `Bootstrap` |
| Database | `MySQL` `AWS RDS` |
| Auth | `Spring Security` `OAuth2 Client` |
| Infra | `AWS EC2` |
| Build | `Gradle` |

## My Role

- 피드 게시글·첨부파일·댓글 CRUD 구현
- 관리자 매출 및 회원 관리 기능 구현
- Spring Security 기반 권한별 접근 제어 적용
- 다음 주소 검색 API 연동
- AWS EC2 배포 및 RDS 연동
- 배포 자동화 스크립트 작성
- GitHub 및 프로젝트 문서 관리

## Service Structure

```text
Browser
  │
  ▼
Spring Boot
  ├─ 사용자 기능
  ├─ 브랜드 기능
  ├─ 렌탈 기능
  ├─ 피드
  └─ 관리자 기능
       │
       ▼
    MyBatis
       │
       ▼
   MySQL / AWS RDS

Application Server: AWS EC2
```

## Implementation Details

### Feed
- 게시글, 첨부파일, 댓글의 등록·조회·수정·삭제 기능을 구현했습니다.
- 게시글과 댓글 기능을 연결해 기본적인 커뮤니티 흐름을 구성했습니다.

### Admin
- 관리자 페이지에서 회원 정보와 매출 현황을 조회·관리할 수 있도록 구현했습니다.
- 일반 사용자 화면과 관리자 화면을 구분해 운영 기능을 분리했습니다.

### Access Control
- Spring Security를 적용해 로그인 사용자와 권한별 접근 범위를 구분했습니다.
- 사용자·브랜드·관리자 역할에 따라 접근 가능한 기능을 나누었습니다.

### Deployment
- AWS EC2에 애플리케이션을 배포하고 AWS RDS의 MySQL과 연동했습니다.
- 반복적인 배포 작업을 줄이기 위한 스크립트를 작성했습니다.

## Security Considerations

- Spring Security를 이용해 인증·인가를 적용했습니다.
- 역할에 따라 접근 가능한 화면과 기능을 구분했습니다.
- DB 연결 정보와 OAuth 설정값은 외부 설정으로 관리합니다.

## ERD

![WEARUP ERD](./ERD/wearup.png)

ERD 원본은 [`/ERD/wearup.erd`](./ERD/wearup.erd)에서 확인할 수 있습니다.

## Run

```bash
./gradlew build
./gradlew bootRun
```

기본 접속 주소:

```text
http://localhost:8080
```

## Demo

[시연 영상 보기](https://youtu.be/tbd1ADUvrsg?t=91)

## Team

| Member | Main Contribution |
| --- | --- |
| 안주경 | 사용자 기능, OAuth2, 마이페이지, 결제·포인트, UI |
| **이현정** | 피드, 관리자 기능, 접근 제어, 주소 API, AWS 배포·문서 관리 |
| 최해훈 | 브랜드·상품, 대여·반납, 배송·상태 관리 |
