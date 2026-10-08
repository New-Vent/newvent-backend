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

수동 재색인. 한 버전만 돌린다. 이벤트 전체 버전을 돌리려면 `/reindex/all` 로 보낸다.
색인은 (묵은 청크 삭제 + 신규 저장) 한 트랜잭션이다.
돌린 뒤의 색인 현황을 돌려준다.

### Request body

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `eventId` | O | 색인할 이벤트 |
| `versionId` | O | 색인할 버전 (한 버전만) |

```json
{ "eventId": 3, "versionId": 5 }
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
| `eventId`·`versionId` 누락 | 400 | `COMMON400-0` |
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

## `GET /api/admin/rag/quality-trend`

주간 품질 추이. 호출 건수·RAG 사용 건수·청크 수를 주별로 묶어 보여준다.
distance 평균은 저장하지 않으므로(쿼리 시점 계산값) 건수 기반으로 추이를 본다.

### Query

| 이름 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- |
| `weeks` | X | `4` | 1~12. 조회할 주 수 |

### 200 예시

```json
{
  "success": true,
  "data": [
    {
      "weekStart": "2026-09-21",
      "totalCalls": 10,
      "ragUsedCalls": 4,
      "chunkCount": 25
    }
  ],
  "message": null
}
```

### 오류

| 상황 | HTTP | code |
| --- | --- | --- |
| `weeks` 가 1 미만·12 초과 | 400 | `COMMON400-0` |

```text
http://localhost:8080/api/admin/rag/quality-trend?weeks=4
```

---

## `POST /api/admin/rag/reindex/all`

전체 재색인 비동기 시작. 삭제된 이벤트·시드(`SEED:`) 제외, 하나가 터져도 멈추지 않고 다음으로 넘어간다.
이벤트당 (삭제 + 저장) 한 트랜잭션이라 중간 실패 시 그 이벤트는 손대기 전 상태로 남는다.
전역 작업은 한 번에 하나 — 이미 돌고 있으면 `409`, 자리를 잡으면 `202` + 시작 시각만 돌려주고
서버에서 `@Async` 로 계속 돈다 (nginx 60초 타임아웃 회피). 야간 스케줄러(매일 03:00 KST)가 같은 자리를 쓴다.

### 202 예시

```json
{
  "success": true,
  "data": {
    "running": true,
    "startedAt": "2026-10-07T03:00:00Z",
    "finishedAt": null,
    "lastResult": null
  },
  "message": null
}
```

### 오류

| 상황 | HTTP |
| --- | --- |
| 이미 전체 재색인이 돌고 있음 | 409 |

```text
POST http://localhost:8080/api/admin/rag/reindex/all
```

## `GET /api/admin/rag/reindex/all/status`

전체 재색인 진행 상태. `running`·시작·종료·직전 결과(`ReindexAllResponse` 모양 그대로).

```text
GET http://localhost:8080/api/admin/rag/reindex/all/status
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
