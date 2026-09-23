# New-Vent Backend

AI를 활용해 이벤트 페이지를 생성하고, 게시부터 사용자 참여까지 관리하는 New-Vent 백엔드 프로젝트입니다.

## Tech Stack

| 구분        | 기술                                 |
| --------- | ---------------------------------- |
| Language  | Java 21                            |
| Framework | Spring Boot 4.1.1, Spring Data JPA |
| Database  | PostgreSQL 17, Flyway              |
| Build     | Gradle                             |
| Infra     | Docker Compose, GitHub Actions     |

## 주요 기능

* 관리자 및 사용자 인증·인가
* 이벤트 생성·수정·게시 관리
* LLM 기반 이벤트 페이지 생성
* 채팅 기반 페이지 수정 및 버전 관리
* 공개 이벤트 조회 및 사용자 참여
* 이벤트 생성 이력을 활용한 RAG 확장

## 패키지 구조

```text
com.newvent
├── auth             # 인증·인가
├── event            # 이벤트 및 템플릿
├── registry         # 블록 레지스트리와 검증
├── generation       # LLM 페이지 생성
├── editor           # 채팅 기반 페이지 수정
├── participation    # 이벤트 참여
├── discovery        # 공개 이벤트 조회
├── user             # 사용자와 멤버십
├── rag              # 검색 및 임베딩
├── common           # 공통 응답과 예외 처리
└── infra            # 외부 서비스 연동
```

## Getting Started

### Requirements

* JDK 21
* Docker Desktop
* Git

### Clone

```bash
git clone https://github.com/New-Vent/newvent-backend.git
cd newvent-backend
git switch develop
```

### Local configuration

DB 접속 정보와 환경변수는 저장소에 포함하지 않습니다.

로컬 환경 설정 방법은 [`docs/setup.md`](docs/setup.md)를 참고하세요.

### Run PostgreSQL

```bash
docker compose up -d
docker compose ps
```

### Run application

Windows:

```bash
gradlew.bat bootRun
```

macOS/Linux:

```bash
./gradlew bootRun
```

애플리케이션 실행 시 Flyway가 DB 마이그레이션을 자동으로 적용합니다.

### Test

Windows:

```bash
gradlew.bat test
```

macOS/Linux:

```bash
./gradlew test
```

## Documentation

| 문서        | 경로                                                   |
| --------- | ---------------------------------------------------- |
| 로컬 환경 설정  | [`docs/setup.md`](docs/setup.md)                     |
| API 공통 규약 | [`docs/api-conventions.md`](docs/api-conventions.md) |
| API 명세    | [`docs/api-spec.md`](docs/api-spec.md)               |
| ERD       | [`docs/erd.md`](docs/erd.md)                         |
| 담당 영역     | [`docs/OWNERSHIP.md`](docs/OWNERSHIP.md)             |
| 주요 결정 기록  | [`docs/decisions/`](docs/decisions/)                 |
