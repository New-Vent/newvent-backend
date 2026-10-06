# API 명세

관리자 이벤트·템플릿 API의 현재 구현을 적는다. `/api/admin/**` 는 관리자 로그인(ADMIN)이 필요하다.

> Status: Draft

## 공통

- prefix: `/api`
- JSON 키: camelCase
- 시각: ISO-8601 + 오프셋 (`2026-09-16T00:00:00+09:00`), Asia/Seoul
- Enum: `UPPER_SNAKE`
- 정상 응답: `ApiResponse<T>`
- 오류 응답: `ErrorResponse` (포맷이 다르다. HTTP 상태로 구분)

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

```json
{
  "code": "EVENT404-0",
  "message": "이벤트를 찾을 수 없습니다.",
  "timestamp": "2026-09-21T14:32:10"
}
```

## 아직 안 한 것

- 템플릿 HTML 본문 — 템플릿 API 는 메타데이터만 제공

## 이벤트 상태

| 값 | 의미 |
| --- | --- |
| `DRAFT` | 임시저장 |
| `PUBLISHED` | 게시중 |
| `ENDED` | 게시종료 (종단) |

멤버십 등급: `NORMAL` / `EXCELLENT` / `BEST` (화면 표시명 일반/우수/최우수)

`closingSoon`: 저장 컬럼이 아니다. `PUBLISHED` 이고 지금이 기간 안이며 종료 3일 전부터면 `true`.

### 이벤트 자동 종료

- 기본적으로 1분마다 종료 시각이 지난 게시 이벤트를 자동 종료한다.
- `status = PUBLISHED`, `deletedAt = null`,
  `endDate < 현재 시각`인 이벤트의 상태를 `ENDED`로 변경한다.
- 종료 시각이 없는 이벤트와 `DRAFT`, `ENDED` 이벤트는 제외한다.
- 시작일·종료일은 유지하고 상태와 `updatedAt`만 변경한다.
- 스케줄러 실행 전에도 참여 API는 이벤트 기간을 검증한다.
- 현재 공개 목록은 `PUBLISHED`만 조회하므로,
  자동 종료된 이벤트는 공개 목록에서 제외된다.

---

## `GET /api/admin/events`

관리자 이벤트 목록. 삭제되지 않은 건만. 기본 정렬은 `updatedAt` 내림차순.

### Query

| 이름 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- |
| `name` | X | | 이벤트명 부분 일치 (대소문자 무시) |
| `status` | X | | `DRAFT` / `PUBLISHED` / `ENDED` |
| `periodFrom` | X | | 이벤트 기간과 겹치는 구간 시작 |
| `periodTo` | X | | 이벤트 기간과 겹치는 구간 끝. `periodFrom` 보다 앞서면 400 |
| `page` | X | `0` | 0부터. 목록을 넘으면 빈 `content` |
| `size` | X | `10` | 1~50 |

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| `periodFrom` > `periodTo` | 400 | `EVENT400-1` |
| 잘못된 쿼리 (status, page, size) | 400 | `COMMON400-0` |

### 200 예시

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "name": "신규 가입 데이터 쿠폰 3GB",
        "status": "PUBLISHED",
        "startAt": "2026-09-16T00:00:00+09:00",
        "endAt": "2026-10-15T23:59:59+09:00",
        "updatedAt": "2026-09-16T10:20:00+09:00",
        "template": "signup",
        "thumbnailUrl": null,
        "grade": "NORMAL",
        "closingSoon": false
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 6,
    "totalPages": 1
  },
  "message": null
}
```

로컬 확인:

```text
http://localhost:8080/api/admin/events
http://localhost:8080/api/admin/events?status=PUBLISHED&name=쿠폰
```

---

## `GET /api/admin/events/{id}`

관리자 이벤트 상세. 목록 필드 + `completedHtml`.

### 200 예시

```json
{
  "success": true,
  "data": {
    "id": 3,
    "name": "지금 긁으면 바로 당첨",
    "status": "PUBLISHED",
    "startAt": "2026-09-16T00:00:00+09:00",
    "endAt": "2026-09-18T23:59:59+09:00",
    "updatedAt": "2026-09-15T09:10:00+09:00",
    "template": "instant",
    "thumbnailUrl": null,
    "grade": "NORMAL",
    "completedHtml": "<section data-block=\"hero\"><h1>지금 긁으면 바로 당첨</h1></section>",
    "closingSoon": true
  },
  "message": null
}
```

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| 없거나 소프트 삭제됨 | 404 | `EVENT404-0` |
| 잘못된 쿼리 (status, page, size) | 400 | `COMMON400-0` |

```text
http://localhost:8080/api/admin/events/3
```

---

## `POST /api/admin/events`

관리자 이벤트 생성. 상태는 항상 `DRAFT`. `completedHtml` 은 null.
인증 없음 (아직).

### Request body

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `name` | O | 이벤트명 (1~100자) |
| `startAt` | O | 시작일시 |
| `endAt` | O | 종료일시 (`startAt` 보다 이후) |
| `templateKey` | X | 헌진 템플릿 키. 있으면 활성 템플릿이어야 함 |
| `grade` | X | 노출 대상 등급 1개 (`NORMAL` / `EXCELLENT` / `BEST`). 없으면 `NORMAL`. `events.grade` 단일 컬럼 기준 |

### 201 예시

```json
{
  "success": true,
  "data": {
    "id": 100,
    "name": "테스트 이벤트",
    "status": "DRAFT",
    "startAt": "2026-10-01T00:00:00+09:00",
    "endAt": "2026-10-15T23:59:59+09:00",
    "updatedAt": "2026-09-16T01:00:00+09:00",
    "template": "sports_cheer",
    "thumbnailUrl": null,
    "grade": "BEST",
    "completedHtml": null,
    "closingSoon": false
  },
  "message": null
}
```

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| 필수값 누락·검증 실패 | 400 | `COMMON400-0` |
| `endAt` ≤ `startAt` | 400 | `EVENT400-0` |
| 없는·비활성 `templateKey` | 404 | `EVENT404-1` |

```text
POST http://localhost:8080/api/admin/events
Content-Type: application/json
```

---

## `PATCH /api/admin/events/{id}`

관리자 이벤트 정보·기간 수정 (REQ-EVT-04, 09, 10). 보낸 필드만 바꾸고, 생략하거나 `null` 인 필드는 기존 값을 유지한다.
상태는 바꾸지 않는다. 종료(`ENDED`)됐거나, 게시(`PUBLISHED`) 중이면서 기존 종료일시가 지난 이벤트는 수정할 수 없다.
`DRAFT` 는 게시 전이라 기간이 지나도 다시 잡을 수 있다.

### Request body

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `name` | X | 1~100자. 생략하면 유지. 값이 오면 공백만 있는 값(`""`, `"   "`)은 400 (`COMMON400-0`). 앞뒤 공백은 제거해 저장 |
| `startAt` | X | 생략하면 유지 |
| `endAt` | X | 생략하면 유지. 수정 후 기간(보낸 값 + 기존 값)이 `endAt` > `startAt` 이어야 함 |
| `templateKey` | X | 생략하면 유지. 빈 문자열(`""`)이면 템플릿 해제. 값이 있으면 활성 템플릿이어야 함. 게시 중에는 지금과 다른 값(해제 포함)을 보내면 409 |
| `grade` | X | `NORMAL` / `EXCELLENT` / `BEST`. 생략하면 유지 |

```json
{ "name": "가을 멤버십 더블 혜택", "endAt": "2026-11-30T23:59:59+09:00" }
```

### 200

생성 API 와 같은 `EventDetailResponse`. `updatedAt` 은 수정 시각으로 바뀐다.

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| 검증 실패 (이벤트명 공백뿐·100자 초과) | 400 | `COMMON400-0` |
| 수정 후 `endAt` ≤ `startAt` | 400 | `EVENT400-0` |
| 없거나 삭제된 이벤트 | 404 | `EVENT404-0` |
| 없는·비활성 `templateKey` | 404 | `EVENT404-1` |
| 종료(`ENDED`)됐거나, 게시 중이면서 기존 종료일시가 지난 이벤트 | 409 | `EVENT409-1` |
| 게시 중인 이벤트의 템플릿 변경·해제 | 409 | `EVENT409-4` |

직접 편집 API(`POST /api/admin/events/{eventId}/versions/direct-edit`)에도 같은 종료 잠금 정책을 적용한다.
`ENDED` 상태이거나, `PUBLISHED` 상태에서 현재 시각이 종료일시 이상이면 `409 / EVENT409-1`을 반환한다.
기준 HTML 조회와 새 버전 저장 전에 거절하므로 직접 편집 결과는 저장되지 않는다.
`DRAFT`는 기간이 지나도 직접 편집할 수 있다. 소유자가 아닌 관리자의 요청은 기존대로 403을 반환한다.

---

## `PATCH /api/admin/events/{id}/status`

관리자 이벤트 종료 (REQ-EVT-07). 지금은 종료(`PUBLISHED` → `ENDED`)만 받는다. 게시는 `POST /{id}/publish` 로 한다.
종료(`ENDED`)는 되돌릴 수 없고, 종료한 이벤트는 수정할 수 없다. 게시 버전(`completedHtml`)은 그대로 남는다. 종료 전에 게시만 내리려면 `POST /{id}/unpublish` 를 쓴다.

### Request body

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `status` | O | `ENDED` 만 허용 |

```json
{ "status": "ENDED" }
```

### 200

`EventDetailResponse`. `status` 는 `ENDED`, `closingSoon` 은 `false`.

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| `status` 누락·없는 값 | 400 | `COMMON400-0` |
| `ENDED` 가 아닌 상태 요청 | 400 | `EVENT400-2` |
| 없거나 삭제된 이벤트 | 404 | `EVENT404-0` |
| 게시 중(`PUBLISHED`)이 아닌 이벤트 | 409 | `EVENT409-6` |

---

## 삭제·휴지통

| Method | Path | 설명 | 오류 |
| --- | --- | --- | --- |
| `DELETE` | `/api/admin/events/{id}` | 휴지통으로 보낸다 (`deletedAt` 기록). 응답 `data` 없음 | 404 `EVENT404-0` · 게시 중 409 `EVENT409-2` · 생성 작업 중 409 `EVENT409-3` |
| `GET` | `/api/admin/events/trash` | 휴지통 목록. `page`·`size` 는 목록 API 와 같음. 삭제일 내림차순 | — |
| `POST` | `/api/admin/events/{id}/restore` | 휴지통에서 복구. 복구된 `EventDetailResponse` | 휴지통에 없으면 404 `EVENT404-0` |
| `DELETE` | `/api/admin/events/{id}/permanent` | 휴지통에서 영구 삭제. 되돌릴 수 없음 | 휴지통에 없으면 404 `EVENT404-0` |

---

## 게시

| Method | Path | 설명 | 오류 |
| --- | --- | --- | --- |
| `POST` | `/api/admin/events/{id}/publish` | 요청 `{ "versionId": 10 }`. 게시(`DRAFT` → `PUBLISHED`) 또는 재게시(다른 버전으로 교체). 고른 버전은 저장 지점으로 표시된다. 게시된 `EventDetailResponse` | `versionId` 누락 400 `COMMON400-0` · 없는 이벤트 404 `EVENT404-0` · 없는 버전 404 `EVENT404-3` · 종료된 이벤트 409 `EVENT409-5` |
| `POST` | `/api/admin/events/{id}/unpublish` | 게시 내리기(`PUBLISHED` → `DRAFT`). 요청 본문 없음. 게시 버전(`publishedVersion`)을 해제해 사용자 화면에서 내리고, 저장된 버전·참여 기록·알림 표시는 그대로 둔다. 내린 뒤에는 `POST /{id}/publish` 로 다시 게시할 수 있다. 내려진 `EventDetailResponse` (`status` 는 `DRAFT`, `closingSoon` 은 `false`) | 없거나 삭제된 이벤트 404 `EVENT404-0` · 게시 중(`PUBLISHED`)이 아닌 이벤트(`DRAFT`·`ENDED`) 409 `EVENT409-7` |

---

## `GET /api/admin/templates`

이헌진 추천 템플릿 5종 목록. `active=true` 만. HTML 본문은 아직 없음.

### 200 예시

```json
{
  "success": true,
  "data": [
    {
      "templateKey": "sports_cheer",
      "name": "스포츠 응원",
      "description": "월드컵 승부예측 투표와 스코어 맞추기. sports_cheer.html",
      "theme": "theme-sports",
      "active": true
    }
  ],
  "message": null
}
```

| templateKey | name | theme | 파일 |
| --- | --- | --- | --- |
| `sports_cheer` | 스포츠 응원 | `theme-sports` | sports_cheer.html |
| `holiday_gift` | 한가위 선물 | `theme-holiday` | holiday_gift.html |
| `member_appreciation` | 회원 감사 | `theme-vip` | member_appreciation.html |
| `flash_sale` | 72h 특가 | `theme-sale` | flash_sale.html |
| `pre_registration` | 사전예약 | `theme-launch` | pre_registration.html |

```text
http://localhost:8080/api/admin/templates
```

---

## `GET /api/admin/templates/{templateKey}`

단건 조회. 없으면 `404` + `EVENT404-1`.

```text
http://localhost:8080/api/admin/templates/sports_cheer
```

---

## `GET /api/admin/llm-calls`

LLM 호출 로그 목록 (관리자). 쓰기는 `LlmCallLogService.record*` 가 담당하므로 이 API 는 조회 전용.
정렬은 생성 시각 내림차순 (최신이 먼저).

### Query

| 이름 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- |
| `eventId` | X | | 이벤트 필터 |
| `callOk` | X | | 호출 성공 여부 필터 (`true` / `false`) |
| `from` | X | | 생성 시각 시작 (ISO-8601 + 오프셋) |
| `to` | X | | 생성 시각 끝 (ISO-8601 + 오프셋) |
| `page` | X | `0` | 0부터. 목록을 넘으면 빈 `content` |
| `size` | X | `10` | 1~50 |

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| 잘못된 쿼리 (page, size) | 400 | `COMMON400-0` |

### 200 예시

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "eventId": 3,
        "requestId": "cf3f3b66-8ef0-4d30-9d25-4b52a9f0ae24",
        "attemptNo": 1,
        "modelName": "bedrock.gemma3",
        "provider": "bedrock",
        "callOk": true,
        "validOk": true,
        "inputTokens": 420,
        "outputTokens": 310,
        "responseTimeMs": 1820,
        "ragUsed": false,
        "createdAt": "2026-09-30T16:30:00+09:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  },
  "message": null
}
```

`ragUsed` 는 RAG 본 구현 전까지 `false` 로 나온다 (정상).

```text
http://localhost:8080/api/admin/llm-calls
http://localhost:8080/api/admin/llm-calls?eventId=3&callOk=true
```

---

## `GET /api/admin/llm-calls/{id}`

LLM 호출 로그 상세 1건. 목록에 없는 실패 분류·잘림·RAG 청크 아이디까지 내려간다.

### 200 예시

```json
{
  "success": true,
  "data": {
    "id": 1,
    "eventId": 3,
    "versionId": 12,
    "requestId": "cf3f3b66-8ef0-4d30-9d25-4b52a9f0ae24",
    "attemptNo": 1,
    "modelName": "bedrock.gemma3",
    "provider": "bedrock",
    "callOk": true,
    "validOk": false,
    "failureType": "VALIDATION_FAIL",
    "failureMessage": null,
    "failCodes": "INVALID_AMOUNT",
    "truncated": false,
    "inputTokens": 420,
    "outputTokens": 310,
    "responseTimeMs": 1820,
    "ragUsed": false,
    "chunkIds": null,
    "createdAt": "2026-09-30T16:30:00+09:00"
  },
  "message": null
}
```

`failureType` 값: `VALIDATION_FAIL` / `TRUNCATED`. `versionId` 는 저장까지 이어진 마지막 성공 시도에만 있다 (선행 실패·실패 결과는 `null`).

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| 없는 로그 | 404 | `LLM404-0` |

```text
http://localhost:8080/api/admin/llm-calls/1
```

---

# RAG (관리자)

이벤트 버전 HTML을 블록 단위로 청킹해 임베딩 벡터(`rag_chunks`)로 저장하고 검색하는 파이프라인.
임베딩 모델은 `cohere.embed-multilingual-v3` (1024차원, us-east-1) 로 고정되어 있다 (`RagConstants`).

- **자기 이벤트 제외**: 검색은 `eventId` 본인 글을 항상 뺀다. 자기 글을 예시로 가져오면 돌고 돌기 때문.
- **최소 유사도 0.5**: `distance = 1 - similarity` 기준 `maxDistance = 0.5` 보다 멀면 결과에서 잘린다.
- `similar-versions` (버전 비교 RAG)는 미구현. `VersionCompareService` 인터페이스만 정의됨.

## `POST /api/admin/rag/reindex`

수동 재색인. `versionId` 가 있으면 그 버전 1개, 없으면 이벤트 전체 버전을 오래된 것부터 순차로 돌린다.
색인은 버전별로 (묵은 청크 삭제 + 신규 저장) 한 트랜잭션이고, 재색인 전체도 같은 트랜잭션에 묶인다.
돌린 뒤의 색인 현황을 돌려준다.

### Request body

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `eventId` | O | 색인할 이벤트 |
| `versionId` | X | 없으면 이벤트 전체 버전 |

```json
{ "eventId": 3 }
```

### 200 예시

`index-status` 와 같은 `IndexStatusResponse`. 저장된 청크 개수 합계는 직접 세야 한다 (응답은 현황만).

```json
{
  "success": true,
  "data": {
    "eventId": 3,
    "totalVersions": 5,
    "indexedVersions": 5,
    "pendingVersions": 0,
    "chunkCount": 23,
    "lastIndexedAt": "2026-10-01T01:23:45Z"
  },
  "message": null
}
```

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| `eventId` 누락 | 400 | `COMMON400-0` |
| `versionId` 가 없거나 다른 이벤트 소속 | 404 | `EVENT404-3` |

```text
POST http://localhost:8080/api/admin/rag/reindex
Content-Type: application/json
```

---

## `GET /api/admin/rag/index-status`

색인 현황. 전체 버전 대비 몇 개 버전이 청크를 갖고 있는지.

### Query

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `eventId` | O | 조회할 이벤트 |

### 200 예시

`pendingVersions = totalVersions - indexedVersions` (한 번도 색인 안 된 버전 수).

```json
{
  "success": true,
  "data": {
    "eventId": 3,
    "totalVersions": 5,
    "indexedVersions": 3,
    "pendingVersions": 2,
    "chunkCount": 18,
    "lastIndexedAt": "2026-10-01T01:23:45Z"
  },
  "message": null
}
```

`lastIndexedAt` 은 마지막으로 청크가 저장된 시각 (없으면 `null`). 시각은 `Instant` 라 UTC(`Z`)로 나온다 — 이 문서의 다른 API(+09:00)와 다르다.

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| `eventId` 누락 | 400 | `COMMON400-0` |

```text
http://localhost:8080/api/admin/rag/index-status?eventId=3
```

---

## `GET /api/admin/rag/search-preview`

검색 미리보기. 쿼리를 던지면 유사 청크 상위 후보와 거리(`distance`)를 돌려준다.
`distance` 가 작을수록 질문과 가깝다 (`0` 이면 완전 일치, `0.5` 가 잘림 기준).

### Query

| 이름 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- |
| `eventId` | O | | 검색에서 제외할 자기 이벤트 |
| `query` | O | | 검색어. 공백이면 빈 `results` (임베딩 호출 없음) |
| `topK` | X | `3` | 1~10 |

### 200 예시

```json
{
  "success": true,
  "data": {
    "eventId": 3,
    "query": "회원 혜택 강조",
    "topK": 3,
    "results": [
      {
        "chunkId": 41,
        "blockKey": "hero",
        "chunkIndex": 0,
        "content": "신규 가입 고객 전원에게 데이터 3GB를 드립니다",
        "distance": 0.11
      }
    ]
  },
  "message": null
}
```

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| `eventId` 누락 | 400 | `COMMON400-0` |
| `topK` 가 1 미만·10 초과 | 400 | `COMMON400-0` |

```text
http://localhost:8080/api/admin/rag/search-preview?eventId=3&query=회원%20혜택&topK=5
```

---

## `GET /api/admin/rag/recommend-prompts`

관리자 생성 보조. 유사 청크를 채팅에 넣을 프롬프트 초안으로 바꿔 돌려준다.
프론트는 후보 중 하나를 골라 채팅창에 그대로 입력한다. `prompt` = 블록 가이드(`shape()`) + 청크 예시.

### Query

| 이름 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- |
| `eventId` | O | | 검색에서 제외할 자기 이벤트 |
| `query` | O | | 초안 소재. 공백이면 빈 목록 |
| `topK` | X | `3` | 1~10 |

### 200 예시

```json
{
  "success": true,
  "data": [
    {
      "eventId": 5,
      "eventTitle": "지금 긁으면 바로 당첨",
      "blockKey": "hero",
      "prompt": "메인 배너에 쓸 강렬한 문구. (예시: 지금 긁으면 바로 당첨)"
    }
  ],
  "message": null
}
```

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| `eventId` 누락 | 400 | `COMMON400-0` |
| `topK` 가 1 미만·10 초과 | 400 | `COMMON400-0` |

```text
http://localhost:8080/api/admin/rag/recommend-prompts?eventId=3&query=성과급&topK=3
```
