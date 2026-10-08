// 삭제 예정

# API 명세

관리자 이벤트·템플릿 API의 현재 구현을 적는다. `/api/admin/**` 는 관리자 로그인(ADMIN)이 필요하다.

관리자 등록 템플릿의 조회·등록·미리보기·재사용은 [이벤트 템플릿 라이브러리 API](api-spec/template-library.md)를 참고한다.

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

`closingSoon`: 저장 컬럼이 아니다. `PUBLISHED` 이고 이미 시작했으며 종료 3일 전부터 종료 시각까지(포함)면 `true`. 종료일이 없으면 `false`.

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
| `name` | X | | 이벤트명 부분 일치 (대소문자 무시). 숫자만 보내면 이벤트 ID 가 같은 것도 함께 찾는다 |
| `status` | X | | `DRAFT` / `PUBLISHED` / `ENDED` |
| `progress` | X | | `UPCOMING` / `ONGOING` / `ENDED`. 저장된 상태가 아니라 지금 시각과 기간으로 판정 (공개 목록과 같은 기준) |
| `periodFrom` | X | | 이벤트 기간과 겹치는 구간 시작 |
| `periodTo` | X | | 이벤트 기간과 겹치는 구간 끝. `periodFrom` 보다 앞서면 400 |
| `page` | X | `0` | 0부터. 목록을 넘으면 빈 `content` |
| `size` | X | `10` | 1~50 |

`progress` 판정 (`now` = 요청 시각, 종료 시각 당일 포함):

| 값 | 조건 |
| --- | --- |
| `UPCOMING` | `startAt` > now |
| `ONGOING` | (`startAt` 없음 또는 ≤ now) 그리고 (`endAt` 없음 또는 ≥ now) |
| `ENDED` | `endAt` < now |

`status` 와 같이 쓸 수 있다. 예: 지금 사용자에게 보이는 이벤트는 `status=PUBLISHED&progress=ONGOING`.

### 응답 필드 (목록 전용)

| 필드 | 설명 |
| --- | --- |
| `publishedVersionNo` | 게시 중인 버전 번호. 게시한 적 없으면 `null` |
| `latestVersionNo` | 가장 최근 버전 번호. 페이지가 아직 없으면 `null`. `publishedVersionNo` 보다 크면 게시 후 새 버전이 있다는 뜻 |
| `thumbnailHtml` | 썸네일용 HTML 조각. 버전 HTML 에서 hero 블록만 잘라 기간 슬롯을 채운 것이다. 게시 버전이 있으면 그 버전, 없으면(`DRAFT` 등) 최신 버전을 쓴다. 버전이 없거나 hero 블록이 없으면 `null`. 공개 목록의 `thumbnailHtml` 과 같은 규칙이며, 프론트는 격리된 iframe 으로 그린다 |

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| `periodFrom` > `periodTo` | 400 | `EVENT400-1` |
| 잘못된 쿼리 (status, progress, page, size) | 400 | `COMMON400-0` |

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
        "grade": "NORMAL",
        "closingSoon": false,
        "publishedVersionNo": 1,
        "latestVersionNo": 3,
        "thumbnailHtml": "<div class=\"ev-container event-page theme-sale\"><section data-block=\"hero\">...</section></div>"
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
http://localhost:8080/api/admin/events?status=PUBLISHED&progress=ONGOING
http://localhost:8080/api/admin/events?name=3
```

---

## `GET /api/admin/events/{id}`

관리자 이벤트 상세. 목록 필드(버전 번호 제외) + `completedHtml`.

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
종료(`ENDED`)는 되돌릴 수 없고, 종료한 이벤트는 수정할 수 없다. 게시 버전(`completedHtml`)은 그대로 남는다. 종료 전에 게시만 내리려면 `POST /{id}/unpublish` 를 쓴다(종료 시각이 지난 이벤트는 내릴 수 없다).

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
| `GET` | `/api/admin/events/trash` | 휴지통 목록. `page`·`size`·응답 필드(버전 번호·`thumbnailHtml` 포함)는 목록 API 와 같음. 삭제일 내림차순 | — |
| `POST` | `/api/admin/events/{id}/restore` | 휴지통에서 복구. 복구된 `EventDetailResponse` | 휴지통에 없으면 404 `EVENT404-0` |
| `DELETE` | `/api/admin/events/{id}/permanent` | 휴지통에서 영구 삭제. 되돌릴 수 없음 | 휴지통에 없으면 404 `EVENT404-0` |

---

## 게시

게시·재게시·게시 내리기는 이벤트 소유 관리자만 실행할 수 있다. 다른 관리자가 요청하면
`403 COMMON403-0`을 반환하며 이벤트 상태와 버전을 변경하지 않는다.

같은 소유자 제한은 이벤트 수정·종료·휴지통 이동·복구·영구 삭제에도 적용한다.
관리자 ID는 인증 정보에서 읽으며 요청 본문이나 쿼리로 받지 않는다.

| Method | Path | 설명 | 오류 |
| --- | --- | --- | --- |
| `POST` | `/api/admin/events/{id}/publish` | 요청 `{ "versionId": 10 }`. 게시(`DRAFT` → `PUBLISHED`) 또는 재게시(다른 버전으로 교체). 고른 버전은 저장 지점으로 표시된다. 게시 시각에 종료일시가 지났으면(종료 시각과 같은 순간 포함) 게시할 수 없다 — 기간을 고친 뒤 다시 게시한다. 시작일은 검사하지 않는다(진행 중 이벤트의 게시·재게시, 시작 전 이벤트의 예약 게시 모두 가능). 게시된 `EventDetailResponse` | `versionId` 누락 400 `COMMON400-0` · 없는 이벤트 404 `EVENT404-0` · 없는 버전 404 `EVENT404-3` · 종료된 이벤트 409 `EVENT409-5` · 종료일시가 지난 이벤트 409 `EVENT409-8` |
| `POST` | `/api/admin/events/{id}/unpublish` | 게시 내리기(`PUBLISHED` → `DRAFT`). 요청 본문 없음. 게시 버전(`publishedVersion`)을 해제해 사용자 화면에서 내리고, 저장된 버전·참여 기록·알림 표시는 그대로 둔다. 내린 뒤에는 `POST /{id}/publish` 로 다시 게시할 수 있다. 내려진 `EventDetailResponse` (`status` 는 `DRAFT`, `closingSoon` 은 `false`) | 없거나 삭제된 이벤트 404 `EVENT404-0` · 게시 중(`PUBLISHED`)이 아닌 이벤트(`DRAFT`·`ENDED`) 409 `EVENT409-7` · 종료 시각이 지났지만 아직 `ENDED` 로 바뀌기 전인 이벤트 409 `EVENT409-1` |

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

---

## `GET /api/admin/rag/similar-versions`

유사 버전 탐색. 기준 버전과 비슷한 같은 이벤트 내 다른 버전을 유사도 내림차순으로 돌려준다.
기준 버전의 색인된 청크 벡터를 그대로 쿼리로 쓰므로 임베딩을 새로 부르지 않는다.

### Query

| 이름 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- |
| `eventId` | O | | 기준 버전이 속한 이벤트 |
| `versionId` | O | | 기준 버전. 결과에서 제외된다 |
| `topK` | X | `3` | 1~10. 반환할 버전 수 |

### 200 예시

`similarity` 가 클수록 기준 버전과 비슷하다 (`1` 이면 동일). `topChunks` 는 해당 버전의 대표 청크 최대 2개.
기준 버전이 미색인이면 빈 목록이다.

```json
{
  "success": true,
  "data": [
    {
      "versionId": 5,
      "versionNo": 2,
      "similarity": 0.89,
      "topChunks": [
        {
          "chunkId": 41,
          "blockKey": "hero",
          "chunkIndex": 0,
          "content": "신규 가입 고객 전원에게 데이터 3GB를 드립니다",
          "distance": 0.11
        }
      ]
    }
  ],
  "message": null
}
```

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| `eventId`·`versionId` 누락 | 400 | `COMMON400-0` |
| `versionId` 가 없거나 다른 이벤트 소속 | 404 | `EVENT404-3` |
| `topK` 가 1 미만·10 초과 | 400 | `COMMON400-0` |

```text
http://localhost:8080/api/admin/rag/similar-versions?eventId=3&versionId=10&topK=3
```

---

## `POST /api/admin/events/{eventId}/generate`

백지 이벤트 페이지 생성 시작. 비동기 작업으로 `202 Accepted` 반환, `jobId`로 폴링.

### Path parameters

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `eventId` | O | 생성할 이벤트 ID |

### Request body

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `templateCode` | X | 템플릿 코드. 없으면 백지 생성 |
| `requestText` | X | 생성 요청문 (최대 500자) |
| `privacyConfirmationJobId` | X | 개인정보 확인 작업 ID (이전 단계에서 받은 값) |
| `privacyConfirmed` | X | 개인정보 동의 여부 (`true`면 확인 완료) |

### 202 예시

```json
{
  "success": true,
  "data": {
    "jobId": "01HXK8J7V9F2W1N4M5Q8R7T3Y6",
    "phase": "PREPARING",
    "percent": 0
  },
  "message": null
}
```

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| 필수값 누락·검증 실패 | 400 | `COMMON400-0` |
| 요청문이 너무 김 (500자 초과) | 400 | `COMMON400-0` |
| 템플릿 코드 너무 김 (50자 초과) | 400 | `COMMON400-0` |
| 이벤트 없거나 삭제됨 | 404 | `EVENT404-0` |
| 이벤트 소유 관리자 아님 | 403 | — |
| 템플릿 코드 없거나 비활성 | 404 | `EVENT404-1` |
| 템플릿 경로인데 요청문 있음 | 400 | `GENERATION400-0` |
| 요청문 비었는데 템플릿 없음 | 400 | `GENERATION400-1` |
| 개인정보 확인 필요 | 400 | `GENERATION400-2` |
| 이미 생성 중 | 409 | `GENERATION409-0` |
| 일일 호출 상한 초과 | 429 | `LLM429-0` |

```text
POST http://localhost:8080/api/admin/events/3/generate
Content-Type: application/json

{
  "templateCode": "",
  "requestText": "여름 데이터 대방출 이벤트 만들어줘. 데이터 3GB 혜택 강조해줘."
}
```

---

## `GET /api/admin/events/{eventId}/generate/{jobId}`

생성 작업 진행 상황 폴링. `phase`가 `DONE`이면 `versionId` 포함.

### Path parameters

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `eventId` | O | 이벤트 ID |
| `jobId` | O | 생성 작업 ID (시작 응답의 `jobId`) |

### 200 예시

```json
{
  "success": true,
  "data": {
    "jobId": "01HXK8J7V9F2W1N4M5Q8R7T3Y6",
    "phase": "CALLING",
    "percent": 40,
    "versionId": null,
    "message": null
  },
  "message": null
}
```

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| 없거나 삭제된 작업 | 404 | `GENERATION404-0` |
| 이벤트 소유 관리자 아님 | 403 | — |

```text
GET http://localhost:8080/api/admin/events/3/generate/01HXK8J7V9F2W1N4M5Q8R7T3Y6
```

---

## `DELETE /api/admin/events/{eventId}/generate/{jobId}`

진행 중인 생성 작업 취소 요청. 단계 사이에서 중단됨.

### Path parameters

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `eventId` | O | 이벤트 ID |
| `jobId` | O | 생성 작업 ID |

### 202 Accepted

응답 본문 없음 (공통 성공 응답 스펙에 따름).

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| 없거나 삭제된 작업 | 404 | `GENERATION404-0` |
| 이미 완료된 작업 | 409 | `GENERATION409-1` |
| 이벤트 소유 관리자 아님 | 403 | — |

---

## RAG 연동 동작 (생성 시 자동 적용)

백지 생성(`templateCode` 빈 값 또는 생략) 시, 요청문(`requestText`)을 쿼리로 RAG 검색을 수행해 유사 청크 상위 3개를 프롬프트에 `## 참고 예시` 섹션으로 자동 주입한다.

- 검색 실패·빈 결과 시 RAG 없이 생성 진행 (`rag_used=false`)
- 검색 성공 시 청크 상위 3개를 `## 참고 예시` 섹션으로 프롬프트에 주입 (`rag_used=true`, `chunk_ids` 기록)
- 검색 파라미터: `eventId`(자기 제외), `query=requestText`, `topK=3`, `maxDistance=0.5`
- 템플릿 경로(`templateCode` 지정)에서는 RAG 미수행 (`rag_used=false`)

생성 완료 시 `llm_call_logs`에 `rag_used`(boolean), `chunk_ids`(CSV, 예: `"41,42,43"`) 기록됨.


# 이벤트 참여

로그인한 사용자가 이벤트에 참여한다. 이벤트에 설정된 참여 방식에 따라 입력값을 검증하고, 제출 데이터와 결과 데이터를 저장한다.

| 항목         | 내용                                            |
|--------------|-------------------------------------------------|
| 메서드       | `POST`                                          |
| 경로         | `/api/users/me/events/{eventId}/participations` |
| 인증         | 사용자 Access Token 필요                        |
| 경로 변수    | `eventId`: 참여할 이벤트 ID (`Long`)            |
| Content-Type | 본문이 있는 경우 `application/json`             |
| 성공 상태    | `201 Created`                                   |

## 요청 헤더

```http
Authorization: Bearer {accessToken}
```

사용자 ID는 JWT에서 가져온다. 요청 본문으로 사용자 ID나 참여 방식, 당첨 결과를 받지 않는다.

## 참여 조건

- 삭제되지 않은 공개 접근 가능 이벤트이며, 참여 기간 안이어야 한다.
- 이벤트 상태가 `PUBLISHED`여야 한다.
- 사용자 멤버십 등급이 이벤트의 요구 등급 이상이어야 한다.
- 이벤트당 사용자 1회만 참여할 수 있다.
- 이벤트에 참여 방식 설정이 정확히 하나 존재해야 한다.
- 참여 방식이 활성화되어 있고 서버에서 지원하는 방식이어야 한다.

## 요청 본문

이벤트의 `games.code`에 따라 필요한 필드가 달라진다.

| 필드          | 타입      | 사용 방식     | 검증                             |
|---------------|-----------|---------------|----------------------------------|
| `prediction`  | `String`  | 스포츠 예측형 | 필수. 설정된 선택지 중 하나      |
| `phoneNumber` | `String`  | 사전예약형    | 필수. 국내 휴대전화 번호 형식    |
| `pouchIndex`  | `Integer` | 복주머니형    | 필수. `1` 이상 `pouchCount` 이하 |

해당 참여 방식과 무관한 위 필드를 함께 보내면 `400 Bad Request`를 반환한다.

### 기본형 — `BASIC`

요청 본문 없이 참여하거나 빈 객체를 보낸다.

```json
{}
```

### 스포츠 예측형 — `SPORTS_PREDICTION`

```json
{
  "prediction": "HOME_WIN"
}
```

앞뒤 공백을 제거한 값이 이벤트 설정의 `predictionOptions`에 포함되어야 한다.

### 사전예약형 — `PRE_REGISTRATION`

```json
{
  "phoneNumber": "010-1234-5678"
}
```

숫자만 있는 형식도 허용한다. 앞뒤 공백과 하이픈을 제거하여 저장한다.

```json
{
  "phoneNumber": "01012345678"
}
```

### 복주머니형 — `LUCKY_POUCH`

```json
{
  "pouchIndex": 2
}
```

번호는 1부터 시작한다. 설정된 복주머니 개수를 초과할 수 없다.

## 성공 응답

| 필드                        | 타입      | 설명                                      |
|-----------------------------|-----------|-------------------------------------------|
| `success`                   | `boolean` | 성공 여부                                 |
| `data.participationId`      | `Long`    | 생성된 참여 기록 ID                       |
| `data.eventId`              | `Long`    | 참여한 이벤트 ID                          |
| `data.resultData`           | `Object`  | 서버가 생성하고 저장한 결과               |
| `data.resultData.status`    | `String`  | 결과가 있는 경우 `PENDING`, `WON`, `LOST` |
| `data.resultData.prizeName` | `String`  | 당첨 결과에 포함되는 경품명               |

### 즉시 추첨 당첨 또는 전원 지급 대상

기본형의 `GUARANTEED` 설정은 추첨 없이 모든 참여자에게 아래 결과를 반환한다.

`WON`은 당첨 또는 전원 지급 대상이라는 결과 기록이며, 실제 쿠폰 발급 완료를 의미하지 않는다.

```json
{
  "success": true,
  "data": {
    "participationId": 30,
    "eventId": 10,
    "resultData": {
      "status": "WON",
      "prizeName": "커피 쿠폰"
    }
  }
}
```

### 즉시 추첨 미당첨

```json
{
  "success": true,
  "data": {
    "participationId": 31,
    "eventId": 10,
    "resultData": {
      "status": "LOST"
    }
  }
}
```

미당첨도 참여 기록이 정상적으로 생성되므로 `201 Created`를 반환한다.

### 결과 대기

기본형의 `DELAYED` 설정과 스포츠 예측형은 결과 대기 상태를 저장·반환한다.

```json
{
  "success": true,
  "data": {
    "participationId": 32,
    "eventId": 10,
    "resultData": {
      "status": "PENDING"
    }
  }
}
```

`PENDING`은 추후 결과 확정이 필요한 참여를 의미한다. 실제 결과 확정 기능은 별도 구현 범위다.

### 단순 참여 완료

기본형의 `resultMode`가 없거나 사전예약형인 경우에는 빈 결과를 저장·반환한다.

```json
{
  "success": true,
  "data": {
    "participationId": 33,
    "eventId": 10,
    "resultData": {}
  }
}
```

이 경우 `{}`는 단순 참여 기록을 의미하며, 결과 대기나 미당첨으로 분류하지 않는다.

## 서버 참여 설정

아래 설정은 `event_game_configs.config`에 저장한다. 사용자 참여 요청 본문에 보내는 데이터가 아니다.

| `games.code` 및 처리 방식     | 설정 예시                                                                |
|-------------------------------|--------------------------------------------------------------------------|
| `BASIC` — 참여 기록만 저장    | `{}`                                                                     |
| `BASIC` — 결과 대기           | `{"resultMode":"DELAYED"}`                                               |
| `BASIC` — 즉시 추첨           | `{"resultMode":"IMMEDIATE","winProbability":30,"prizeName":"커피 쿠폰"}` |
| `BASIC` — 전원 지급 결과 기록 | `{"resultMode":"GUARANTEED","prizeName":"커피 쿠폰"}`                    |
| `SPORTS_PREDICTION`           | `{"predictionOptions":["HOME_WIN","DRAW","AWAY_WIN"]}`                   |
| `PRE_REGISTRATION`            | `{}`                                                                     |
| `LUCKY_POUCH`                 | `{"pouchCount":3,"winProbability":30,"prizeName":"커피 쿠폰"}`           |

상세 설정 규칙은 [이벤트 참여 설정 규칙](event-participation-config.md)을 참고한다.

### 설정 규칙

- `predictionOptions`: 비어 있지 않은 문자열 배열
- `pouchCount`: 1 이상의 정수
- `winProbability`: 0~100 사이 정수. 단위는 `%`
- `prizeName`: 즉시 추첨 또는 전원 지급 결과 기록에 필요한 경품명. 공백만 있는 값은 허용하지 않음
- 기본형의 `resultMode`가 없으면 단순 참여 기록과 빈 결과 저장
- 기본형의 `resultMode`가 `DELAYED`이면 `PENDING` 저장
- 기본형의 `resultMode`가 `IMMEDIATE`이면 즉시 추첨
- 기본형의 `resultMode`가 `GUARANTEED`이면 난수 생성 없이 모든 참여자에게 `WON`과 경품명 저장
- `GUARANTEED`에서는 `winProbability`가 필요하지 않음
- 기본형의 `resultMode`는 생략하거나 `DELAYED`, `IMMEDIATE`, `GUARANTEED` 중 하나로 설정
- 그 외 `resultMode` 값은 설정 오류로 처리
- 복주머니형은 입력값 검증 후 즉시 추첨
- 스포츠 예측형은 `PENDING` 저장
- 사전예약형은 빈 결과 저장

즉시 추첨은 서버에서 0~99 사이 난수를 생성하고, 난수가 `winProbability`보다 작으면 당첨으로 판정한다.

복주머니 선택 번호에 따른 확률 차이는 없다.

## 데이터 저장

### 제출 데이터

| 참여 방식     | `submitted_data` 예시           |
|---------------|---------------------------------|
| 기본형        | `{}`                            |
| 스포츠 예측형 | `{"prediction":"HOME_WIN"}`     |
| 사전예약형    | `{"phoneNumber":"01012345678"}` |
| 복주머니형    | `{"pouchIndex":2}`              |

### 결과 데이터

| 참여 방식 및 결과     | `result_data` 예시                         |
|-----------------------|--------------------------------------------|
| 기본형 — 설정 없음    | `{}`                                       |
| 기본형 — `DELAYED`    | `{"status":"PENDING"}`                     |
| 스포츠 예측형         | `{"status":"PENDING"}`                     |
| 사전예약형            | `{}`                                       |
| 즉시 추첨 — 당첨      | `{"status":"WON","prizeName":"커피 쿠폰"}` |
| 즉시 추첨 — 미당첨    | `{"status":"LOST"}`                        |
| 기본형 — `GUARANTEED` | `{"status":"WON","prizeName":"커피 쿠폰"}` |

응답의 `resultData`는 저장한 참여 엔티티의 결과 데이터와 동일하다.

결과 대기 집계는 `status = PENDING`, 당첨 혜택 집계는 `status = WON`을 기준으로 사용할 수 있다.

기존에 `{}`로 저장된 참여 기록은 이번 변경으로 자동 변환되지 않는다.

## 오류

| HTTP 상태 | 오류 코드            | 발생 조건                                                        |
|-----------|----------------------|------------------------------------------------------------------|
| `400`     | `PARTICIPATION400-0` | 필수 입력 누락, 잘못된 값, 참여 방식과 무관한 필드 제출          |
| `400`     | 공통 요청 오류       | JSON 파싱 또는 요청 필드 타입 오류                               |
| `401`     | 공통 인증 오류       | 사용자 인증 실패                                                 |
| `403`     | 공통 권한 오류       | 관리자 토큰으로 사용자 API 호출                                  |
| `403`     | `PARTICIPATION403-0` | 참여에 필요한 멤버십 등급 부족                                   |
| `404`     | 이벤트 접근 오류     | 이벤트가 없거나 공개 접근 불가, 참여 기간 밖 또는 게시 상태 아님 |
| `409`     | `PARTICIPATION409-0` | 이미 참여한 이벤트                                               |
| `409`     | `PARTICIPATION409-1` | 참여 설정 없음·여러 개 존재 또는 설정값 오류                     |
| `409`     | `PARTICIPATION409-2` | 지원하지 않거나 비활성화된 참여 방식                             |

동시 요청이 DB의 사용자·이벤트 유일 제약에 걸리는 경우에도 중복 참여 오류로 처리한다.

## 구현 범위

- 기존에 저장된 참여 설정을 조회하며, 설정을 생성하거나 수정하지 않는다.
- 즉시 추첨과 전원 지급 결과 기록을 지원한다.
- 결과와 경품명을 저장·반환하며, 실제 쿠폰 발급이나 경품 재고 차감은 수행하지 않는다.
- 기본형 `DELAYED`와 스포츠 예측형은 결과 대기 상태를 저장한다.
- 결과 대기 상태를 당첨·미당첨으로 확정하는 기능은 별도 구현 범위다.
- 참여 목록 및 상세 조회는 별도 API에서 제공한다.
- 복주머니도 현재는 이벤트당 1회 참여만 허용한다. 매일 참여는 지원하지 않는다.

---
# 이벤트 참여 설정 규칙

참여 API가 조회하는 `games.code`와 `event_game_configs.config`의 형식을 정의한다.

이 문서는 설정 데이터의 작성 규칙이며, 설정 생성·수정 API를 정의하지 않는다.

## 저장 구조

`event_game_configs`는 이벤트와 참여 방식을 연결한다.

| 필드       | 의미                                   |
|------------|----------------------------------------|
| `event_id` | 설정 대상 이벤트 ID                    |
| `game_id`  | 참여 방식을 나타내는 `games.id`        |
| `config`   | 해당 이벤트의 입력 검증·결과 처리 설정 |

현재 참여 API는 이벤트에 설정이 정확히 하나 존재할 때만 참여를 허용한다.

참여 방식은 `games.code`로 판단한다. `config` 안에 참여 방식 코드를 다시 넣지 않는다.

## 기본형 — `BASIC`

추가 입력 없이 참여한다. `resultMode`에 따라 결과 처리가 달라진다.

### 참여 기록만 저장

```json
{}
```

제출 데이터와 결과 데이터를 모두 `{}`로 저장한다.

결과 대기나 미당첨으로 분류하지 않는다.

### 결과를 나중에 처리

```json
{
  "resultMode": "DELAYED"
}
```

참여 시 다음 결과를 저장·반환한다.

```json
{
  "status": "PENDING"
}
```

이후 결과 확정 기능은 별도 구현이 필요하다.

### 확률 추첨

```json
{
  "resultMode": "IMMEDIATE",
  "winProbability": 30,
  "prizeName": "커피 쿠폰"
}
```

참여 시 서버에서 추첨하여 `WON` 또는 `LOST`를 저장·반환한다.

당첨 결과에는 `prizeName`을 포함한다.

### 전원 지급 결과 기록

```json
{
  "resultMode": "GUARANTEED",
  "prizeName": "커피 쿠폰"
}
```

추첨 없이 모든 참여자에게 `WON`과 경품명을 저장·반환한다.

`winProbability`는 필요하지 않다.

실제 쿠폰 발급 완료를 의미하지는 않는다.

## 스포츠 예측형 — `SPORTS_PREDICTION`

```json
{
  "predictionOptions": [
    "HOME_WIN",
    "DRAW",
    "AWAY_WIN"
  ]
}
```

- `predictionOptions`는 비어 있지 않은 문자열 배열이어야 한다.
- 선택지 값은 이벤트에 맞게 정할 수 있다. 위 세 값으로 고정된 것은 아니다.
- 사용자가 보낸 `prediction`의 앞뒤 공백을 제거한 뒤 선택지와 비교한다.
- 대소문자를 구분하므로 요청값과 설정값이 정확히 일치해야 한다.
- 참여 시 결과 대기 상태를 저장·반환한다.

```json
{
  "status": "PENDING"
}
```

예측 결과를 확정하는 기능은 별도 구현이 필요하다.

## 사전예약형 — `PRE_REGISTRATION`

```json
{}
```

별도 설정 없이 사용자 요청의 `phoneNumber`를 검증한다.

전화번호 형식 검증은 서버 코드에 정의되어 있다.

참여 시 결과 데이터는 `{}`로 저장하며, 결과 대기로 분류하지 않는다.

## 복주머니형 — `LUCKY_POUCH`

```json
{
  "pouchCount": 3,
  "winProbability": 30,
  "prizeName": "커피 쿠폰"
}
```

- 사용자가 선택할 수 있는 번호는 `1`부터 `pouchCount`까지다.
- 입력 검증 후 즉시 확률 추첨을 수행한다.
- 선택한 복주머니 번호와 관계없이 동일한 당첨 확률을 적용한다.
- 기본형의 `resultMode` 설정은 복주머니형에 적용되지 않는다.
- 당첨이면 `WON`과 경품명, 미당첨이면 `LOST`를 저장·반환한다.

## 필드 규칙

| 필드                | 타입        | 규칙                                           | 사용 방식                     |
|---------------------|-------------|------------------------------------------------|-------------------------------|
| `resultMode`        | `String`    | 생략 또는 `DELAYED`, `IMMEDIATE`, `GUARANTEED` | 기본형                        |
| `predictionOptions` | 문자열 배열 | 최소 1개, 각 값은 공백만 있는 문자열 불가      | 스포츠 예측형                 |
| `pouchCount`        | 정수        | 1 이상                                         | 복주머니형                    |
| `winProbability`    | 정수        | 0~100, 단위 `%`                                | 기본형 즉시 추첨·복주머니형   |
| `prizeName`         | `String`    | 필수, 공백만 있는 문자열 불가                  | 즉시 추첨·전원 지급 결과 기록 |

숫자 설정은 JSON 숫자로 작성한다. `"30"`처럼 문자열로 작성하지 않는다.

`winProbability`가 0이면 항상 미당첨이고, 100이면 항상 당첨이다.

## 결과 상태 기준

| 참여 방식 및 설정                    | 결과 데이터                                | 의미           |
|--------------------------------------|--------------------------------------------|----------------|
| 기본형 — `resultMode` 없음           | `{}`                                       | 단순 참여 완료 |
| 기본형 — `DELAYED`                   | `{"status":"PENDING"}`                     | 결과 대기      |
| 스포츠 예측형                        | `{"status":"PENDING"}`                     | 결과 대기      |
| 사전예약형                           | `{}`                                       | 단순 참여 완료 |
| 기본형 즉시 추첨·복주머니형 — 당첨   | `{"status":"WON","prizeName":"커피 쿠폰"}` | 당첨           |
| 기본형 즉시 추첨·복주머니형 — 미당첨 | `{"status":"LOST"}`                        | 미당첨         |
| 기본형 — `GUARANTEED`                | `{"status":"WON","prizeName":"커피 쿠폰"}` | 전원 지급 대상 |

빈 결과 데이터와 결과 대기 상태를 구분한다.

결과 대기 집계는 `status = PENDING`, 당첨 혜택 집계는 `status = WON`을 기준으로 사용할 수 있다.

기존에 `{}`로 저장된 기록은 설정을 변경하거나 코드를 배포해도 자동으로 `PENDING`으로 변환되지 않는다.

## 설정 오류 처리

참여 요청 시 설정을 검증한다.

- 설정이 없거나 여러 개 존재: `409`, `PARTICIPATION409-1`
- 필요한 설정 누락 또는 잘못된 설정값: `409`, `PARTICIPATION409-1`
- 지원하지 않거나 비활성화된 참여 방식: `409`, `PARTICIPATION409-2`

설정 JSON이 저장되어 있다는 사실만으로 참여 가능한 설정임이 보장되지는 않는다.

## 현재 지원 범위

이 설정은 서버 코드에 구현된 참여 방식의 값만 조정한다.

JSON에 임의의 키를 추가한다고 새로운 입력 방식이나 추첨 규칙이 만들어지지는 않는다.

현재는 단일 경품명과 정수 확률을 사용한다.

여러 경품별 확률, 경품 수량 제한, 복주머니별 다른 경품, 매일 참여는 지원하지 않는다.

`PENDING`을 최종 결과로 변경하는 기능은 별도 구현 범위다.

`WON` 결과 기록은 실제 쿠폰 발급 완료를 의미하지 않는다.

---
# 이벤트 버전 상세 및 미리보기 HTML 조회

## API

| 항목 | 내용 |
| --- | --- |
| 메서드 | `GET` |
| 경로 | `/api/admin/events/{eventId}/versions/{versionId}` |
| 경로 변수 | `eventId`: 이벤트 ID, `versionId`: 조회할 버전 ID (`Long`) |
| 요청 본문 | 없음 |
| 성공 상태 | `200 OK` |

관리자가 버전 이력에서 선택한 **버전의 HTML과 버전 정보**를 조회한다.
저장 지점(`checkpoint`) 여부와 관계없이 해당 이벤트에 속한 버전을 조회할 수 있다.

조회만 수행하며 저장된 버전, 현재 작업 내용, 게시 버전은 변경하지 않는다.

## 성공 응답 예시

```json
{
  "success": true,
  "data": {
    "versionId": 102,
    "versionNo": 2,
    "createdAt": "2026-09-21T14:20:00+09:00",
    "htmlContent": "<!doctype html><html><body>이벤트 화면</body></html>"
  },
  "message": null
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.versionId` | number | 조회한 버전 ID |
| `data.versionNo` | number | 이벤트 내 버전 번호 |
| `data.createdAt` | string | 버전 생성 일시 (`OffsetDateTime`) |
| `data.htmlContent` | string | 해당 버전에 저장된 HTML 원문 |

`htmlContent`는 JSON 문자열로 반환한다.
프론트엔드는 이를 읽기 전용 미리보기에 표시한다.

이 API의 응답에는 `checkpoint` 필드를 포함하지 않는다.
저장 지점 여부는 버전 이력 목록 응답의 `checkpoint` 필드로 확인한다.

## 실패 응답

| 상황 | HTTP 상태 | 오류 코드 |
| --- | --- | --- |
| 이벤트가 없거나 삭제된 경우 | `404 Not Found` | `EVENT404-0` (`EVENT_NOT_FOUND`) |
| 버전이 없거나 다른 이벤트에 속하는 경우 | `404 Not Found` | `EVENT404-3` (`VERSION_NOT_FOUND`) |

저장 지점으로 지정되지 않은 버전이나 저장 지점이 해제된 버전도 조회할 수 있다.
실패 응답의 JSON 구조는 공통 예외 응답 규약을 따른다.

## 화면 연결 및 구현 범위

- `/api/admin/**` 경로에는 관리자(`ROLE_ADMIN`) 인증·인가 규칙이 적용된다.
- 버전 이력 목록에서 **미리보기**를 누르면 선택한 `versionId`로 이 API를 호출한다.
- 자동 저장 버전과 저장 지점으로 지정된 버전 모두 미리보기할 수 있다.
- 저장 지점을 해제한 버전도 전체 목록에 남으며, 이 API로 조회할 수 있다.
- 프론트엔드는 반환된 HTML을 읽기 전용으로 표시한다. `iframe srcdoc`을 사용할 경우 `sandbox`를 설정하고 `allow-same-origin`은 부여하지 않는다.
- 이 API는 채팅 수정, 새 버전 저장, 게시 상태 변경을 수행하지 않는다.
- 해당 버전을 기준으로 작업하려면 별도의 수정 API를 호출한다.

화면 시안의 수정 내용 요약 문구는 현재 응답에 포함되지 않는다.
버전 이력 목록 응답의 `requestContent`는 수정 요청 원문이므로
요약 문구와 동일한 데이터로 취급하지 않는다.

# 이벤트 버전 이력 조회·저장 지점 지정·해제

## 버전 이력 목록 조회

| 항목 | 내용 |
| --- | --- |
| 메서드 | `GET` |
| 경로 | `/api/admin/events/{eventId}/versions` |
| 경로 변수 | `eventId`: 조회할 이벤트 ID (`Long`) |
| 요청 본문 | 없음 |
| 성공 상태 | `200 OK` |

해당 이벤트의 **전체 버전**을 `versionNo` 내림차순으로 반환한다.
자동 저장 버전과 저장 지점으로 지정된 버전을 모두 포함하며,
응답 항목의 `checkpoint` 필드로 저장 지점 여부를 구분한다.

버전이 없는 경우 `versions`는 빈 배열이다.

### 성공 응답 예시

```json
{
  "success": true,
  "data": {
    "eventId": 12,
    "title": "2026 월드컵 응원 이벤트",
    "versions": [
      {
        "versionId": 103,
        "versionNo": 3,
        "createdAt": "2026-09-21T15:00:00+09:00",
        "checkpoint": false,
        "published": false,
        "sourceVersionNo": 2,
        "requestContent": "제목을 변경해 줘"
      },
      {
        "versionId": 102,
        "versionNo": 2,
        "createdAt": "2026-09-21T14:20:00+09:00",
        "checkpoint": true,
        "published": true,
        "sourceVersionNo": 1,
        "requestContent": "소개 문구를 친근하게 정리해 줘"
      },
      {
        "versionId": 101,
        "versionNo": 1,
        "createdAt": "2026-09-20T10:00:00+09:00",
        "checkpoint": false,
        "published": false,
        "sourceVersionNo": null,
        "requestContent": null
      }
    ]
  },
  "message": null
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.eventId` | number | 이벤트 ID |
| `data.title` | string | 이벤트 제목 |
| `data.versions` | array | 전체 버전 목록. 버전이 없으면 `[]` |
| `versions[].versionId` | number | 버전 ID. 미리보기·저장 지점 지정·해제 등 후속 동작에 사용할 식별자 |
| `versions[].versionNo` | number | 이벤트 내 버전 번호 |
| `versions[].createdAt` | string | 버전 생성 일시 (`OffsetDateTime`) |
| `versions[].checkpoint` | boolean | 저장 지점으로 지정된 버전이면 `true`, 아니면 `false` |
| `versions[].published` | boolean | 이벤트 상태가 `PUBLISHED`이고 해당 버전이 `events.publishedVersion`인 경우 `true` |
| `versions[].sourceVersionNo` | number 또는 null | 이어서 작업한 원본 버전 번호. 출처가 없으면 `null` |
| `versions[].requestContent` | string 또는 null | 버전에 연결된 수정 요청 원문. 연결된 요청이 없으면 `null` |

`requestContent`는 화면용 요약 문구가 아니라 저장된 요청 원문이다.
최초 생성 버전도 저장 지점 여부와 관계없이 목록에 포함되며,
연결된 요청 메시지가 없을 경우 `null`을 반환한다.

프론트엔드는 `checkpoint = true`인 항목만 필터링하여 저장 지점 목록을 표시할 수 있다.

### 버전이 없는 경우

이벤트가 존재하지만 조회할 버전이 없으면 `200 OK`와 빈 배열을 반환한다.
저장 지점이 없더라도 자동 저장 버전이 존재하면 해당 버전들을 반환한다.

```json
{
  "success": true,
  "data": {
    "eventId": 12,
    "title": "2026 월드컵 응원 이벤트",
    "versions": []
  },
  "message": null
}
```

## 저장 지점 지정

| 항목 | 내용 |
| --- | --- |
| 메서드 | `PUT` |
| 경로 | `/api/admin/events/{eventId}/versions/{versionId}/checkpoint` |
| 경로 변수 | `eventId`: 이벤트 ID, `versionId`: 해당 이벤트에 속한 버전 ID (`Long`) |
| 요청 본문 | 없음 |
| 성공 상태 | `200 OK` |

해당 버전의 `is_checkpoint`를 `true`로,
`checkpointed_at`을 현재 시각으로 설정한다.
이미 저장 지점으로 지정된 버전에 다시 요청하면 성공을 반환하고,
기존 `checkpointed_at`은 유지한다.

새 버전을 생성하지 않으며, 버전의 HTML, 생성 시각, 수정 요청 이력은 변경하지 않는다.
이후 목록 조회 시 해당 버전의 `checkpoint`는 `true`로 반환된다.

## 저장 지점 해제

| 항목 | 내용 |
| --- | --- |
| 메서드 | `DELETE` |
| 경로 | `/api/admin/events/{eventId}/versions/{versionId}/checkpoint` |
| 경로 변수 | `eventId`: 이벤트 ID, `versionId`: 해당 이벤트에 속한 버전 ID (`Long`) |
| 요청 본문 | 없음 |
| 성공 상태 | `200 OK` |

이벤트 상태가 `PUBLISHED`이고 요청한 `versionId`가 현재
`published_version_id`와 같으면 저장 지점을 해제할 수 없으며
`409 Conflict`를 반환한다.
다른 버전을 재게시해 게시 버전이 변경되면 이전 버전의 저장 지점은 해제할 수 있다.

해제 가능한 버전은 `is_checkpoint`를 `false`로,
`checkpointed_at`을 `null`로 변경한다.
이미 해제된 버전에 다시 요청해도 성공을 반환한다.

**저장 지점 해제는 버전 삭제가 아니다.**
버전 자체와 HTML, 수정 요청 이력, 게시 상태는 유지된다.
해제한 버전도 전체 버전 목록에 남으며 `checkpoint: false`로 반환된다.
해제 후에도 상세 HTML을 조회할 수 있다.

### 지정·해제 성공 응답

두 API 모두 동일한 공통 응답 형식을 사용한다.

```json
{
  "success": true,
  "data": null,
  "message": null
}
```

## 실패 응답

| 상황 | HTTP 상태 | 오류 코드 |
| --- | --- | --- |
| 이벤트가 없거나 삭제된 경우 | `404 Not Found` | `EVENT404-0` (`EVENT_NOT_FOUND`) |
| 지정·해제할 버전이 없거나 해당 이벤트에 속하지 않는 경우 | `404 Not Found` | `EVENT404-3` (`VERSION_NOT_FOUND`) |
| 현재 게시 중인 버전의 저장 지점 해제 요청 | `409 Conflict` | `EVENT409-0` (`PUBLISHED_VERSION_CHECKPOINT_UNMARK_FORBIDDEN`) |

버전 관련 오류는 저장 지점 **지정·해제 API**에 적용된다.
`EVENT409-0`은 **해제 API에만** 적용된다.
실패 응답의 JSON 필드 구조는 공통 예외 응답 규약을 따른다.

## 구현 범위

- `/api/admin/**` 경로에는 관리자(`ROLE_ADMIN`) 인증·인가 규칙이 적용된다.
- 전체 버전 목록에는 자동 저장 버전과 저장 지점으로 지정된 버전이 모두 포함된다.
- 저장 지점을 해제해도 버전은 삭제되지 않으며, 목록에 `checkpoint: false`로 반환된다.
- 프론트엔드는 `checkpoint` 값으로 저장 지점 표시 및 필터링을 처리할 수 있다.
- 선택한 버전의 페이지 내용은 [버전 상세 및 미리보기 HTML 조회](event-version-detail.md) API에서 제공한다.
- 해당 버전을 기준으로 한 채팅 수정은 별도 API에서 처리한다.
- 

---
# 내 이벤트 참여 상세 조회

로그인한 사용자가 본인의 이벤트 참여 기록을 상세 조회한다.

이벤트 정보, 참여 시각, 제출 데이터와 저장된 결과 데이터를 반환한다.

| 항목      | 내용                                             |
|-----------|--------------------------------------------------|
| 메서드    | `GET`                                            |
| 경로      | `/api/users/me/participations/{participationId}` |
| 인증      | 사용자 Access Token 필요                         |
| 경로 변수 | `participationId`: 참여 기록 ID (`Long`), 1 이상 |
| 요청 본문 | 없음                                             |
| 성공 상태 | `200 OK`                                         |

## 요청 헤더

```http
Authorization: Bearer {accessToken}
```

사용자 ID는 JWT 인증 정보에서 가져온다. 요청으로 사용자 ID를 받지 않는다.

## 요청 예시

```http
GET /api/users/me/participations/30
Authorization: Bearer {accessToken}
```

## 조회 기준

- 참여 기록 ID와 JWT 사용자 ID를 함께 조건으로 조회한다.
- 본인의 참여 기록만 조회할 수 있다.
- 기록이 없거나 다른 사용자의 기록이면 동일한 `404` 오류를 반환한다.
- 이벤트가 종료되거나 소프트 삭제되어도 참여 기록이 남아 있으면 조회할 수 있다.
- 이벤트의 현재 게시 상태, 참여 기간 및 사용자 멤버십 등급으로 조회를 제한하지 않는다.
- 조회 시 추첨이나 결과 계산을 다시 수행하지 않는다.

## 성공 응답

| 필드                   | 타입             | 설명                       |
|------------------------|------------------|----------------------------|
| `success`              | `boolean`        | 성공 여부                  |
| `data.participationId` | `Long`           | 참여 기록 ID               |
| `data.eventId`         | `Long`           | 참여한 이벤트 ID           |
| `data.eventTitle`      | `String`         | 이벤트의 현재 제목         |
| `data.participatedAt`  | `OffsetDateTime` | 참여 기록 생성 시각        |
| `data.submittedData`   | `Object`         | 참여 시 저장된 제출 데이터 |
| `data.resultData`      | `Object`         | 저장된 결과 데이터         |

`submittedData`와 `resultData`의 구조는 참여 방식에 따라 달라진다. 저장된 데이터가 빈 객체이면 `{}`로 반환한다.

### 당첨 결과 예시

```json
{
  "success": true,
  "data": {
    "participationId": 30,
    "eventId": 10,
    "eventTitle": "복주머니 이벤트",
    "participatedAt": "2026-10-02T11:00:00+09:00",
    "submittedData": {
      "pouchIndex": 2
    },
    "resultData": {
      "status": "WON",
      "prizeName": "커피 쿠폰"
    }
  },
  "message": null
}
```

### 결과 대기 예시

```json
{
  "success": true,
  "data": {
    "participationId": 31,
    "eventId": 11,
    "eventTitle": "스포츠 예측 이벤트",
    "participatedAt": "2026-10-02T11:00:00+09:00",
    "submittedData": {
      "prediction": "HOME_WIN"
    },
    "resultData": {
      "status": "PENDING"
    }
  },
  "message": null
}
```

### 미당첨 결과 예시

```json
{
  "success": true,
  "data": {
    "participationId": 32,
    "eventId": 10,
    "eventTitle": "복주머니 이벤트",
    "participatedAt": "2026-10-02T11:00:00+09:00",
    "submittedData": {
      "pouchIndex": 1
    },
    "resultData": {
      "status": "LOST"
    }
  },
  "message": null
}
```

### 단순 참여 완료 예시

```json
{
  "success": true,
  "data": {
    "participationId": 33,
    "eventId": 12,
    "eventTitle": "기본 참여 이벤트",
    "participatedAt": "2026-10-02T11:00:00+09:00",
    "submittedData": {},
    "resultData": {}
  },
  "message": null
}
```

## 제출 데이터

| 참여 방식     | `submittedData` 예시            |
|---------------|---------------------------------|
| 기본형        | `{}`                            |
| 스포츠 예측형 | `{"prediction":"HOME_WIN"}`     |
| 사전예약형    | `{"phoneNumber":"01012345678"}` |
| 복주머니형    | `{"pouchIndex":2}`              |

사전예약형의 전화번호는 참여 시 정규화하여 저장한 값을 반환한다.

## 결과 데이터

| 저장된 결과                                | 의미                           |
|--------------------------------------------|--------------------------------|
| `{"status":"PENDING"}`                     | 결과 대기                      |
| `{"status":"WON","prizeName":"커피 쿠폰"}` | 당첨 또는 전원 지급 대상       |
| `{"status":"LOST"}`                        | 미당첨                         |
| `{}`                                       | 결과 상태가 저장되지 않은 기록 |

- 현재 참여 처리에서 기본형 `DELAYED`와 스포츠 예측형은 `PENDING`을 저장한다.
- 설정 없는 기본형과 사전예약형은 `{}`를 저장한다.
- 빈 결과를 결과 대기나 미당첨으로 임의 변환하지 않는다.
- 기존에 `{}`로 저장된 기록도 그대로 반환한다.
- `WON`은 실제 쿠폰 발급 완료를 의미하지 않는다.

## 오류

| HTTP 상태          | 오류 코드            | 발생 조건                                           |
|--------------------|----------------------|-----------------------------------------------------|
| `400 Bad Request`  | 공통 요청 오류       | 참여 ID가 0 이하이거나 `Long`으로 변환할 수 없는 값 |
| `401 Unauthorized` | 공통 인증 오류       | 사용자 인증 실패                                    |
| `403 Forbidden`    | 공통 권한 오류       | 관리자 토큰으로 사용자 API 호출                     |
| `404 Not Found`    | `PARTICIPATION404-0` | 참여 기록이 없거나 본인의 기록이 아님               |

`PARTICIPATION404-0`의 메시지는 `참여 기록을 찾을 수 없습니다.`이다.

오류 응답은 공통 예외 응답 형식을 따른다.

## 구현 범위

- 본인 참여 기록의 읽기 전용 조회를 제공한다.
- 제출값과 결과 데이터를 변경하지 않는다.
- 결과 대기 상태를 당첨·미당첨으로 확정하지 않는다.
- 쿠폰 발급, 혜택 사용 및 참여 취소 기능은 포함하지 않는다.

---

# 내 이벤트 참여 목록 및 요약 조회

로그인한 사용자의 이벤트 참여 목록과 참여 현황 요약을 조회한다.

`filter`로 전체 참여 내역과 당첨 혜택 내역을 구분하며, 목록은 페이징하여 반환한다.

| 항목      | 내용                           |
|-----------|--------------------------------|
| 메서드    | `GET`                          |
| 경로      | `/api/users/me/participations` |
| 인증      | 사용자 Access Token 필요       |
| 요청 본문 | 없음                           |
| 성공 상태 | `200 OK`                       |

## 요청 헤더

```http
Authorization: Bearer {accessToken}
```

사용자 ID는 JWT 인증 정보에서 가져온다. 요청으로 사용자 ID를 받지 않으며, 본인의 참여 기록만 조회한다.

## 쿼리 파라미터

| 필드     | 타입     | 기본값 | 설명 및 제한                            |
|----------|----------|--------|-----------------------------------------|
| `filter` | `String` | `ALL`  | `ALL`, `REWARDS` 중 하나. 대소문자 구분 |
| `page`   | `int`    | `0`    | 0부터 시작하는 페이지 번호. 0 이상      |
| `size`   | `int`    | `10`   | 페이지 크기. 1 이상 50 이하             |

### 전체 참여 목록

```http
GET /api/users/me/participations?filter=ALL&page=0&size=10
```

결과 상태와 관계없이 본인의 전체 참여 기록을 조회한다.

### 받은 혜택 목록

```http
GET /api/users/me/participations?filter=REWARDS&page=0&size=10
```

본인의 참여 기록 중 `result_data.status = WON`인 기록만 조회한다.

“받은 혜택”은 실제 쿠폰 발급 완료 여부가 아닌 저장된 당첨 결과를 기준으로 한다.

## 조회 및 정렬 기준

- 참여일(`created_at`) 내림차순으로 조회한다.
- 참여일이 같으면 참여 기록 ID 내림차순으로 조회한다.
- 이벤트의 현재 게시 상태나 소프트 삭제 여부로 참여 기록을 제외하지 않는다.
- 필터에 해당하는 기록이 없거나 요청 페이지에 기록이 없으면 빈 목록을 반환한다.

## 성공 응답

### 요약 정보

| 필드                                   | 타입      | 설명                          |
|----------------------------------------|-----------|-------------------------------|
| `success`                              | `boolean` | 성공 여부                     |
| `data.summary.totalParticipationCount` | `long`    | 본인의 전체 참여 기록 수      |
| `data.summary.rewardCount`             | `long`    | 본인의 `WON` 참여 기록 수     |
| `data.summary.pendingCount`            | `long`    | 본인의 `PENDING` 참여 기록 수 |

상단 요약 수치 3개는 필터와 페이지에 관계없이 본인의 전체 기록을 기준으로 계산한다.

### 목록 및 페이지 정보

| 필드                                | 타입    | 설명                                |
|-------------------------------------|---------|-------------------------------------|
| `data.participations.content`       | `Array` | 현재 페이지의 참여 목록             |
| `data.participations.page`          | `int`   | 현재 페이지 번호                    |
| `data.participations.size`          | `int`   | 요청한 페이지 크기                  |
| `data.participations.totalElements` | `long`  | 현재 필터에 해당하는 전체 기록 수   |
| `data.participations.totalPages`    | `int`   | 현재 필터에 해당하는 전체 페이지 수 |

`ALL`의 `totalElements`는 전체 참여 기록 수이며, `REWARDS`의 `totalElements`는 당첨 참여 기록 수다.

### 목록 항목

| 필드              | 타입                 | 설명                            |
|-------------------|----------------------|---------------------------------|
| `participationId` | `Long`               | 참여 기록 ID                    |
| `eventId`         | `Long`               | 참여한 이벤트 ID                |
| `eventTitle`      | `String`             | 이벤트의 현재 제목              |
| `participatedAt`  | `OffsetDateTime`     | 참여 기록 생성 시각             |
| `resultStatus`    | `String` 또는 `null` | 저장된 결과 상태                |
| `prizeName`       | `String` 또는 `null` | 저장된 경품명. 없는 경우 `null` |

제출값과 저장된 결과 데이터 전체는 이 API에서 반환하지 않는다. 참여 상세 조회는 후속 API 범위다.

## 결과 상태 및 집계 기준

| 저장된 결과                                | 목록의 `resultStatus` | 당첨 혜택 집계 | 결과 대기 집계 |
|--------------------------------------------|-----------------------|----------------|----------------|
| `{"status":"WON","prizeName":"커피 쿠폰"}` | `WON`                 | 포함           | 제외           |
| `{"status":"LOST"}`                        | `LOST`                | 제외           | 제외           |
| `{"status":"PENDING"}`                     | `PENDING`             | 제외           | 포함           |
| `{}`                                       | `null`                | 제외           | 제외           |

전체 참여 기록 수에는 위 모든 기록을 포함한다.

- 기본형 `DELAYED`와 스포츠 예측형은 참여 시 `PENDING`을 저장한다.
- 설정 없는 기본형과 사전예약형은 참여 시 `{}`를 저장한다.
- 빈 결과 데이터만으로 결과 대기나 미당첨을 판단하지 않는다.
- 기존에 `{}`로 저장된 기록은 자동으로 `PENDING`으로 변환되지 않으며, 대기 수에 포함되지 않는다.
- `REWARDS`는 목록 조회 필터 이름이며, 참여 결과 상태가 아니다.

## 응답 예시

### 전체 참여 목록

```json
{
  "success": true,
  "data": {
    "summary": {
      "totalParticipationCount": 4,
      "rewardCount": 1,
      "pendingCount": 1
    },
    "participations": {
      "content": [
        {
          "participationId": 34,
          "eventId": 14,
          "eventTitle": "단순 참여 이벤트",
          "participatedAt": "2026-10-02T10:00:00+09:00",
          "resultStatus": null,
          "prizeName": null
        },
        {
          "participationId": 33,
          "eventId": 13,
          "eventTitle": "스포츠 예측 이벤트",
          "participatedAt": "2026-10-02T09:00:00+09:00",
          "resultStatus": "PENDING",
          "prizeName": null
        },
        {
          "participationId": 32,
          "eventId": 12,
          "eventTitle": "복주머니 이벤트",
          "participatedAt": "2026-10-01T16:00:00+09:00",
          "resultStatus": "LOST",
          "prizeName": null
        },
        {
          "participationId": 31,
          "eventId": 11,
          "eventTitle": "커피 쿠폰 이벤트",
          "participatedAt": "2026-10-01T15:00:00+09:00",
          "resultStatus": "WON",
          "prizeName": "커피 쿠폰"
        }
      ],
      "page": 0,
      "size": 10,
      "totalElements": 4,
      "totalPages": 1
    }
  },
  "message": null
}
```

### 받은 혜택 목록

같은 사용자가 `filter=REWARDS`로 조회하면 당첨 기록만 반환한다. 요약 수치는 유지된다.

```json
{
  "success": true,
  "data": {
    "summary": {
      "totalParticipationCount": 4,
      "rewardCount": 1,
      "pendingCount": 1
    },
    "participations": {
      "content": [
        {
          "participationId": 31,
          "eventId": 11,
          "eventTitle": "커피 쿠폰 이벤트",
          "participatedAt": "2026-10-01T15:00:00+09:00",
          "resultStatus": "WON",
          "prizeName": "커피 쿠폰"
        }
      ],
      "page": 0,
      "size": 10,
      "totalElements": 1,
      "totalPages": 1
    }
  },
  "message": null
}
```

### 참여 기록이 없는 경우

```json
{
  "success": true,
  "data": {
    "summary": {
      "totalParticipationCount": 0,
      "rewardCount": 0,
      "pendingCount": 0
    },
    "participations": {
      "content": [],
      "page": 0,
      "size": 10,
      "totalElements": 0,
      "totalPages": 0
    }
  },
  "message": null
}
```

요청 페이지가 전체 페이지 범위를 벗어나도 `200 OK`와 빈 `content`를 반환한다. 이 경우 실제 전체 기록 수와 요약 수치는 유지된다.

## 오류

| HTTP 상태          | 발생 조건                                                                           |
|--------------------|-------------------------------------------------------------------------------------|
| `400 Bad Request`  | 지원하지 않는 `filter`, 음수 `page`, 1 미만 또는 50 초과 `size`, 파라미터 타입 오류 |
| `401 Unauthorized` | 사용자 인증 실패                                                                    |
| `403 Forbidden`    | 관리자 토큰으로 사용자 API 호출                                                     |

오류 응답은 공통 예외 응답 형식을 따른다.

--

# 이벤트 템플릿 라이브러리 API

모든 경로는 관리자 로그인(ADMIN)이 필요하다. 기본 제공 템플릿은 공통이며,
관리자 등록 템플릿은 등록자만 조회·사용·관리한다. 이 공개 범위는 이번 구현의 초기 정책이다.

외부 HTML·파일 업로드는 지원하지 않는다. 등록은 본인 이벤트의 저장된 버전 ID로만 한다.
원본 HTML은 별도 복사하여 보관한다. 원본 이벤트를 수정하거나 삭제해도 등록본은 유지된다.

## API 목록

| 메서드 | 경로 | 기능 |
| --- | --- | --- |
| GET | /api/admin/template-library | 목록·검색·필터·페이지네이션 |
| GET | /api/admin/template-library/{code}/preview | 미리보기 HTML 조각 |
| POST | /api/admin/template-library | 저장 버전을 템플릿으로 등록 |
| PATCH | /api/admin/template-library/{code} | 이름·설명 변경 |
| DELETE | /api/admin/template-library/{code} | 비활성화 (물리 삭제 없음) |
| POST | /api/admin/template-library/{code}/events | 새 DRAFT 이벤트와 첫 버전 생성 |

기존 /api/admin/templates 및 /api/admin/templates/{templateKey}는
호환성을 위해 기본 제공 템플릿 메타데이터만 반환한다.
관리자 등록본은 새 라이브러리 API에서 조회한다.

## 목록

GET /api/admin/template-library?keyword=가을&builtin=false&page=0&size=12

- keyword: 이름에 포함되는 문자열, 최대 100자. %, _, !도 문자 그대로 검색한다.
- builtin: 생략 시 전체, true 기본 제공, false 관리자 등록.
- includeInactive: 기본 false. true이면 비활성 템플릿도 조회한다.
- page: 0 이상, size: 1~50 (기본 12).
- id 내림차순. HTML 본문은 목록 응답에 포함하지 않는다.

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "templateKey": "custom_자동생성UUID",
        "name": "가을 프로모션",
        "description": "설명",
        "builtin": false,
        "active": true,
        "thumbnailPath": null
      }
    ],
    "page": 0,
    "size": 12,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 저장 버전 등록

POST /api/admin/template-library

```json
{
  "eventId": 2,
  "sourceVersionId": 7,
  "name": "가을 프로모션",
  "description": "다시 사용할 디자인"
}
```

201 + 템플릿 메타데이터. name 필수(공백 제외, 최대 100자), description 선택(최대 255자).
기준 버전은 지정 이벤트에 속해야 한다. 삭제된 이벤트는 등록할 수 없다.
소유자가 다르면 403, 이벤트/버전이 없으면 404.

기간·참여 링크 슬롯은 비운다. 버튼 문구는 유지한다.
필수 블록, 블록 단일성, 블록 내용 형태, 기간 슬롯을 검사한다.
참여 링크 슬롯은 템플릿에 있을 때 보존하지만, 백지 생성 버전에는 없을 수 있다.
서버 승인 유의사항을 다시 삽입한다. 기존 슬롯/ID/테마를 유지하며
스크립트는 현재 기본 제공 템플릿의 원본과 완전히 일치하는 것만 남긴다.
임의 스크립트/인라인 이벤트 핸들러는 복사하지 않는다.
추가 위젯 스크립트가 필요하면 이 허용 정책을 별도로 검토해야 한다.

## 미리보기

GET /api/admin/template-library/{code}/preview

```json
{
  "success": true,
  "data": {
    "templateKey": "sports_cheer",
    "html": "<div class=\"ev-container event-page theme-sports\">...</div>"
  }
}
```

완성 문서가 아닌 HTML 조각이다. 기간/참여 링크는 비어 있다.
프론트는 기존 이벤트 미리보기와 같은 공통 event.css·iframe 렌더링을 사용한다.
응답을 관리자 화면 DOM에 바로 삽입하지 말고 격리된 iframe으로 보여준다.
기본 제공본은 파일의 실제 HTML을 사용한다 (DB 시드의 주석 문자열 사용 안 함).
비활성 등록본도 소유자는 미리보기 가능하다.

## 정보 수정·비활성화

PATCH /api/admin/template-library/{code}

```json
{"name":"변경된 이름","description":"변경된 설명"}
```

200 + 메타데이터. name 필수, description 생략/null이면 설명을 비운다.
HTML은 변경하지 않는다.

DELETE /api/admin/template-library/{code}

200 + 기존 ApiResponse의 데이터 없는 성공 응답. 반복 비활성화도 성공한다.
기본 제공 템플릿 수정/비활성화는 403 EVENT403-0.
다른 관리자의 등록본은 조회·관리·신규 선택 시 404 EVENT404-1.

비활성 템플릿은 신규 이벤트에 선택할 수 없다.
기존 이벤트의 버전·게시 상태는 바뀌지 않는다.
이미 연결된 템플릿의 재생성(templateCode 생략)은 기존 계약대로 가능하다.

## 새 이벤트에 사용

POST /api/admin/template-library/{code}/events

```json
{
  "name":"새 이벤트",
  "startAt":"2026-10-10T00:00:00+09:00",
  "endAt":"2026-10-20T00:00:00+09:00",
  "grade":"NORMAL"
}
```

```json
{"success":true,"data":{"eventId":10,"versionId":11,"versionNo":1}}
```

201. 이벤트 생성과 첫 버전 저장은 같은 트랜잭션이다.
     실패하면 둘 다 롤백된다. LLM 호출 없이 템플릿을 복사하며 게시하지 않는다.
     이벤트명은 이벤트 메타데이터이고 HTML의 제목 문구를 자동으로 치환하지 않는다.
     복사된 제목은 기존 직접 편집/채팅 수정으로 변경한다.
     프론트는 eventId로 에디터를 열고 versionId를 수정 기준으로 사용한다.
     별도의 generate 요청을 추가로 보내지 않아도 첫 버전이 존재한다.

기존 POST /api/admin/events의 templateKey 선택도 관리자 등록본을 지원한다.
기존 생성 요청의 명시적 templateCode도 소유자·활성 여부를 검사한다.

## DB 변경·검증

V15__add_template_owner.sql이 event_templates.owner_admin_id와 인덱스를 추가한다.
기본 제공 템플릿은 owner가 null이다. 기존 데이터의 소유자 없는 비내장 템플릿은
자동으로 다른 관리자에게 공개하지 않는다. 해당 데이터가 있다면 소유자를 지정해야 한다.

단위·MockMvc 테스트:

```powershell
.\gradlew.bat test --tests '*TemplateLibraryServiceTest' --tests '*TemplateLibraryControllerTest' --tests '*SavedTemplateHtmlTest' --tests '*DbTemplateLoaderTest'
```

Docker PostgreSQL 실행 후 DB 검색/마이그레이션 검증:

```powershell
.\gradlew.bat test --tests '*EventTemplateLibraryRepositoryTest'
```

단위 테스트 통과와 실제 DB·브라우저 연결 검증은 구분한다.
