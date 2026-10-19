# New-Vent Backend

AI를 활용해 이벤트 페이지를 생성하고, 게시부터 사용자 참여까지 관리하는 New-Vent 백엔드 프로젝트입니다.

---

## 목차

1. [Tech Stack](#tech-stack)
2. [주요 기능](#주요-기능)
3. [패키지 구조](#패키지-구조)
4. [LLM 파이프라인](#llm-파이프라인)
5. [Getting Started](#getting-started)
6. [프로파일과 LLM 설정](#프로파일과-llm-설정)
7. [Test](#test)
8. [CI / 배포](#ci--배포)
9. [Documentation](#documentation)

---

## Tech Stack

| 구분        | 기술                                                       |
| --------- | -------------------------------------------------------- |
| Language  | Java 21                                                  |
| Framework | Spring Boot 4.1.1 · Spring Security · Spring Data JPA    |
| Database  | PostgreSQL 17 + pgvector · Flyway (V1~V23)               |
| AI        | AWS Bedrock (`gemma-3-27b` 생성·수정 · `cohere.embed-multilingual-v3` 임베딩) · Ollama (로컬) |
| HTML 검증   | jsoup                                                    |
| API 문서    | springdoc-openapi (Swagger UI)                           |
| Build     | Gradle · Spotless                                        |
| Infra     | Docker Compose · AWS EC2 · ECR · SSM · GitHub Actions (OIDC) |

## 주요 기능

| 기능 | 내용 |
| --- | --- |
| **인증·인가** | JWT Access Token + 회전형 Refresh Token, 사용자/관리자 쿠키 경로 분리, Origin 검사 |
| **이벤트 관리** | 생성 · 수정 · 게시 · 게시 내리기 · 종료 · 휴지통 · 복원, 종료일 자동 종료 |
| **페이지 생성** | 템플릿 또는 요청문으로 LLM 생성, 비동기 작업 · 폴링, 재시도, 호출 장부 · 일일 상한 |
| **AI 채팅 수정** | 라우터가 요청을 작업 단위로 나눠 해당 블록만 다시 생성, 못 알아들으면 되묻기 |
| **직접 수정** | 문구 · 서식 · 블록 순서 · 배치 · 모양 · 블록/항목 삭제를 모델 없이 반영 |
| **버전 관리** | 모든 변경을 버전으로 저장, 체크포인트 · 되돌리기 |
| **이미지** | 업로드 (png · jpeg · gif · webp, 3MB, 이벤트당 20개) · 공개 제공 |
| **템플릿 라이브러리** | 기본 템플릿 5종 + 관리자 템플릿 등록 · 수정 · 비활성화 |
| **참여** | 기본(즉시 · 확정 · 추첨) · 스포츠 승부 예측 · 사전 등록 · 복주머니, 등급 제한, 추첨 · 발표 스케줄러 |
| **RAG** | 버전 문구 임베딩, 유사 사례 검색 · 요청문 추천, 생성 프롬프트 참고 예시, 품질 추이 |
| **알림** | 이벤트 시작 · 마감 임박 · 종료 관리자 알림 |
| **사용자** | 회원가입 · 내 정보 · 요금제 · 멤버십 등급 |

## 패키지 구조

```text
com.newvent
├── registry         # ★ LLM 출력 계약 — 블록 · 변형 · 팔레트 · 테마 · 배치 · 이미지 · 동작 · 슬롯
│                    #   정화 · 검증 · 병합 · 프롬프트 빌더
├── generation       # 페이지 생성 · 작업 관리 · 재시도 · 버전 저장 · LLM 호출 장부
├── editor           # 채팅 기반 수정 · 라우터 · 게이트(되묻기)
├── event            # 이벤트 · 버전 · 직접 수정 · 템플릿 · 공개 조회 · 자동 종료
├── participation    # 참여 · 참여 설정 · 추첨 · 발표
├── rag              # 청크 · 임베딩 · 유사도 검색 · 시드 데이터
├── asset            # 이미지 업로드 · 저장 · 공개 제공
├── filtering        # 요청문 개인정보 감지
├── notification     # 관리자 알림
├── auth             # 인증·인가 · 토큰 · 쿠키 · 기동 안전장치
├── user             # 사용자와 멤버십
├── admin            # 관리자 계정
├── common           # 공통 응답 · 예외 처리 · 보안 설정
└── infra            # 외부 서비스 연동 (LLM 클라이언트: mock · ollama · bedrock)
```

```text
src/main/resources
├── db/migration     # Flyway V1~V23
├── templates        # 기본 이벤트 템플릿 5종 + templates.json
├── notices          # 서버가 붙이는 공통 유의사항
└── application*.yaml
```

## LLM 파이프라인

**생성**

```
요청문 → 필터링(길이 · 개인정보 · 모호함) → RAG 검색 → PromptBuilder
      → LLM → 정화 → 검증(BlockValidator · ValueCheck) ─ 실패 시 이유를 붙여 재시도 (최대 4회)
      → 슬롯 · 동작 · 배치 · 공통 유의사항 → 버전 저장
```

**채팅 수정**

```
요청문 (+ 고른 블록) → 라우터(작업 최대 3개) → 게이트(되묻기 판단)
      → 작업마다: 삭제 · 배치 · 이미지는 모델 없이 / 나머지는 그 블록만 LLM → 검증 → 병합
      → 버전 저장
```

레지스트리는 기본 항목만 늘 프롬프트에 싣고, 확장 변형 · 팔레트 · 테마는 요청문이 부를 때만 넣습니다.
프롬프트 문장의 이유는 [`docs/prompt-builder.md`](docs/prompt-builder.md)에 있습니다.

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
docker compose up -d      # PostgreSQL 17 + pgvector
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
API는 `http://localhost:8080`, Swagger UI는 `http://localhost:8080/swagger-ui.html`입니다.

### RAG 시드 데이터 (선택)

```bash
./gradlew seedRagData      # 개발·QA·시연용 시드 적재 (prod에서는 차단)
```

## 프로파일과 LLM 설정

| 프로파일 | 용도 |
| --- | --- |
| `dev` (기본) | 로컬 개발. 개발용 JWT 키, `localhost:5173 · 5174` CORS 허용, Swagger 켜짐 |
| `prod` | 운영. `JWT_SECRET` · `CORS_ALLOWED_ORIGINS` · `ASSET_PUBLIC_BASE_URL` 필수, Swagger 꺼짐, 위험한 설정이면 기동 차단 |
| `ollama` | 로컬 Ollama로 LLM 호출 |

| 환경변수 | 값 |
| --- | --- |
| `LLM_PROVIDER` | `mock`(기본) · `ollama` · `bedrock` |
| `LLM_MODEL` | 예: `google.gemma-3-27b-it` |
| `llm.daily-limit` (설정 키) | 하루 LLM 호출 상한 (기본 200) |

`mock`이면 실제 모델을 부르지 않아 비용이 들지 않습니다. 자세한 값은 [`docs/setup.md`](docs/setup.md) §11 · §12를 참고하세요.

## Test

Windows:

```bash
gradlew.bat test
```

macOS/Linux:

```bash
./gradlew test
```

테스트는 로컬 PostgreSQL을 사용하므로 `docker compose up -d`가 먼저 필요합니다.
실제 모델로 스모크 테스트를 하려면 [`docs/setup.md`](docs/setup.md) §11을 참고하세요.

포맷 검사는 `./gradlew spotlessCheck`, 자동 수정은 `./gradlew spotlessApply`입니다.

## CI / 배포

| 단계 | 내용 |
| --- | --- |
| CI | Flyway 버전 중복 검사 → `./gradlew build` (테스트 · Spotless 포함) → Docker 이미지 빌드 |
| 배포 (main) | OIDC → ECR 푸시 → SSM으로 EC2에 설치 → 헬스체크, 실패 시 이전 이미지로 롤백 → 공개 API 응답 확인 |

서버·배포 구조는 [`docs/infra.md`](docs/infra.md)를 참고하세요.

## Documentation

**먼저 [`docs/README.md`](docs/README.md) 를 보세요.** 무엇을 찾는지에 따라 읽을 곳이 다릅니다.

| 문서           | 경로                                                   |
| ------------ | ---------------------------------------------------- |
| **문서 지도**    | [`docs/README.md`](docs/README.md)                   |
| API 명세       | **Swagger UI** — `http://localhost:8080/swagger-ui.html` (코드가 생성, prod 는 꺼짐) |
| API 설계 배경    | [`docs/api-design.md`](docs/api-design.md)           |
| API 공통 규약    | [`docs/api-conventions.md`](docs/api-conventions.md) |
| 오류 코드        | [`docs/error-codes.md`](docs/error-codes.md)         |
| 로컬 환경 설정     | [`docs/setup.md`](docs/setup.md)                     |
| 서버 · 배포      | [`docs/infra.md`](docs/infra.md)                     |
| 프롬프트 빌더      | [`docs/prompt-builder.md`](docs/prompt-builder.md)   |
| 개인정보 필터링     | [`docs/chat-filtering.md`](docs/chat-filtering.md)   |
| 직접 수정        | [`docs/direct-edit-text-format.md`](docs/direct-edit-text-format.md) · [`docs/direct-edit-notices.md`](docs/direct-edit-notices.md) |
| RAG          | [`docs/rag-embedding-eval.md`](docs/rag-embedding-eval.md) · [`docs/rag-seed-data.md`](docs/rag-seed-data.md) · [`docs/rag-seed-eval-result.md`](docs/rag-seed-eval-result.md) |
| 주요 결정 기록     | [`docs/decisions/`](docs/decisions/)                 |
