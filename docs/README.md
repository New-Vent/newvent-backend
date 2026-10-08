# 문서 지도

무엇을 찾는지에 따라 읽을 곳이 다릅니다. **먼저 여기서 고르세요.**

| 알고 싶은 것 | 읽을 곳 |
| --- | --- |
| 이 API 가 **무엇을 주고 무엇을 받나** | **Swagger UI** — 아래 「API 문서」 |
| **왜** 이렇게 설계했나 | [`api-design.md`](api-design.md) · [`decisions/`](decisions/) |
| 응답 봉투·오류 코드·페이지네이션 **규칙** | [`api-conventions.md`](api-conventions.md) |
| 오류 코드가 무슨 뜻인가 | [`error-codes.md`](error-codes.md) |
| 로컬에서 **띄우는 법** | [`setup.md`](setup.md) |
| 서버·배포가 **어떻게 돌아가나** | [`infra.md`](infra.md) |
| 서버에 **접속하는 법** · DB 를 로컬에서 보는 법 | [`infra.md`](infra.md) §3 |
| 개인정보 필터링 | [`chat-filtering.md`](chat-filtering.md) |
| 임베딩 모델을 왜 그걸로 골랐나 | [`rag-embedding-eval.md`](rag-embedding-eval.md) |

---

## API 문서는 코드가 만듭니다

엔드포인트의 경로·요청·응답은 **손으로 적지 않습니다.** springdoc 이 컨트롤러에서 자동 생성합니다.

```
http://localhost:8080/swagger-ui.html    사람이 보는 화면 (Try it out 으로 바로 호출)
http://localhost:8080/v3/api-docs        OpenAPI 3 JSON (프론트 타입 생성·목 서버용)
```

> **배포 서버에는 없습니다.** `application-prod.yaml` 이 springdoc 을 꺼서 공개 서버에 내부
> 구조가 노출되지 않게 합니다. 문서는 로컬·dev 에서만 봅니다. (`OpenApiDocsProfileTest` 가 고정)

### 왜 손으로 안 쓰나

`/api/public/events`, `/api/users/me`, `/api/admin/events/counts` 처럼 **프론트가 매일 쓰는 것들**이
빠지고, 관리자 RAG 디버깅용은 상세히 적혀 있었습니다.

손 문서는 조용히 낡습니다. 코드가 바뀌어도 아무도 안 고칩니다.

### 그래서 사람이 쓰는 문서는 "왜" 만 맡습니다

OpenAPI 가 절대 못 적는 것들입니다.

> 저장된 HTML 의 슬롯은 비어 있고 **보여줄 때** 채운다. 저장할 때 채우면 값이 굳어서
> 이벤트 기간만 고쳐도 화면이 바뀌지 않기 때문이다.

이런 문장이 [`api-design.md`](api-design.md) 와 [`decisions/`](decisions/) 에 있습니다.

---

## 설명을 코드 옆에 두고 싶을 때

컨트롤러에 애너테이션을 붙이면 Swagger UI 에 그대로 실립니다. **문서를 따로 안 고쳐도 됩니다.**

```java
@Operation(
    summary = "공개 이벤트 상세",
    description = "publishedHtml 은 슬롯(기간·참여링크)을 채운 게시 HTML. 게시 버전이 없으면 null")
@ApiResponse(responseCode = "404", description = "없거나 공개 기간 밖 (EVENT404-0 · EVENT404-2)")
@GetMapping("/{eventId}")
```

**한 줄 설명은 여기에, 여러 문단짜리 배경은 `api-design.md` 에** 두는 것이 기준입니다.

---

## 무엇을 고쳤으면 무엇을 따라 고치나

**PR 을 올리기 전에 왼쪽 열을 훑으세요.** 해당하는 게 없으면 문서를 안 고쳐도 됩니다.

### API

| 코드에서 한 일 | 고칠 문서 |
| --- | --- |
| 엔드포인트 **추가·경로 변경·삭제** | **없음** — Swagger 가 생성합니다. 동작이 자명하지 않으면 `@Operation(summary, description)` |
| 요청·응답 **DTO 필드 추가·삭제** | **없음** — 스키마 자동. 필드 뜻이 자명하지 않으면 `@Schema(description = "…")` |
| **응답 필드의 의미**가 바뀜 (예: `thumbnailUrl` → `thumbnailHtml`) | `@Schema` + **프론트에 알리기**. PR 「참고 사항」에 적습니다 |
| `*ErrorCode` enum 에 **코드 추가·문구 변경** | [`error-codes.md`](error-codes.md) — 재추출 명령이 문서 맨 위에 있습니다 |
| **인가 규칙** 변경 (`SecurityConfig`) | `SecurityConfig` 클래스 javadoc + [`api-design.md`](api-design.md) §5·§6 |
| **소유자 검사** 추가·제거 | [`api-design.md`](api-design.md) §6 |

### 도메인 규칙

| 코드에서 한 일 | 고칠 문서 |
| --- | --- |
| 이벤트 **상태 전이** 규칙 변경 | [`api-design.md`](api-design.md) §1 |
| `closingSoon` **창(3일) 변경** | §2 — ⚠️ **구현이 두 곳입니다**(`Event` · `PublicEventService`). 둘 다 고치세요 |
| `Slot` enum 에 **슬롯 추가** | [`decisions/0001`](decisions/0001-슬롯은-보여줄-때-채운다.md) 비고 + 채우는 경로 전부 확인 |
| `result_data` 의 **`status` 값 추가** | §10 — 집계(`rewardCount`·`pendingCount`)에 어떻게 들어가는지 같이 |
| **멤버십 등급** 산정·재계산 시점 변경 | §9 |
| 알림 **종류·발송 조건** 변경 | §8 |

### 인프라·배포

| 코드에서 한 일 | 고칠 문서 |
| --- | --- |
| **nginx 설정** 변경 | [`infra.md`](infra.md) §4 |
| **compose 파일** 추가·변경 | §5 |
| **IAM 역할·정책** 변경 | §7 |
| **배포 워크플로**(`ci.yml` deploy 잡) 변경 | §9 |
| **환경변수 추가** | [`setup.md`](setup.md) + [`infra.md`](infra.md) §5 |
| **스케줄러 추가** | §11 — `spring.task.scheduling.pool-size` 가 셋을 한 스레드로 돌립니다 |
| Flyway **시드 데이터** 관련 변경 | §11 |

### 그 외

| 상황 | 할 일 |
| --- | --- |
| **되돌리기 어려운 선택**을 했다 | [`decisions/`](decisions/) 에 ADR 한 장 — 판단 기준은 [`decisions/README.md`](decisions/README.md) |
| 팀 전체에 적용되는 **작성 규칙** 추가 | [`api-conventions.md`](api-conventions.md) |
| **실험·측정** 기록 | `rag-embedding-eval.md` 처럼 별도 파일 |
| **담당자**가 바뀜 | 아래 「기능별 구현 담당」 |

> **왼쪽에 없는데 고쳐야 할 것 같으면** 그 자체가 이 표에 줄을 추가할 신호입니다.
> PR 에 같이 넣어주세요.

---

## 기능별 구현 담당 (2026-10 기준)

문서에 안 적힌 동작을 물어볼 사람을 찾을 때 씁니다.

| 영역 | 담당 |
| --- | --- |
| 로그인·JWT·리프레시 토큰 (`auth/`) | ddb27 (UHO99) |
| 이벤트 생성·수정·종료, 관리자 목록 필터 | lee-suhyeon |
| 버전 이력·저장 지점·미리보기 | tnqlsqkr |
| 직접 편집, 템플릿 라이브러리, `/e/{id}` 공개 페이지 | jetleetop |
| 관리자 휴지통·게시·알림, 공개 이벤트 API, 회원·멤버십 등급 | kug6109 |
| LLM 호출 계층(`infra/llm`), CI/CD, 인프라 | kwondoha |
| RAG 관련 | kyd4947 |
