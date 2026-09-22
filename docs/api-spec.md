# API 명세

관리자 이벤트 조회 API의 현재 구현을 적는다. 인증/인가·JPA·쓰기 API는 이 문서 범위 밖이다.

> Status: Draft — `feat/event-admin-query`

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

- 인증/인가 (Security 미사용. `/api/admin/**` 도 토큰 없이 호출됨)
- JPA/Flyway — 지금은 메모리 더미 (이벤트 6건 · 템플릿 5종)
- 템플릿 `baseContent`(HTML 조각) — 메타데이터만 제공
- 생성·수정·삭제·상태변경·게시
- 공개 조회 `/api/public/events`

## 이벤트 상태

| 값 | 의미 |
| --- | --- |
| `DRAFT` | 임시저장 |
| `PUBLISHED` | 게시중 |
| `CLOSED` | 게시종료 (종단) |

멤버십 등급: `NORMAL` / `EXCELLENT` / `PREMIUM` (화면 표시명 일반/우수/최우수)

`closingSoon`: 저장 컬럼이 아니다. `PUBLISHED` 이고 지금이 기간 안이며 종료 3일 전부터면 `true`.

---

## `GET /api/admin/events`

관리자 이벤트 목록. 삭제되지 않은 건만. 기본 정렬은 `updatedAt` 내림차순.

### Query

| 이름 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- |
| `name` | X | | 이벤트명 부분 일치 (대소문자 무시) |
| `status` | X | | `DRAFT` / `PUBLISHED` / `CLOSED` |
| `periodFrom` | X | | 이벤트 기간과 겹치는 구간 시작 |
| `periodTo` | X | | 이벤트 기간과 겹치는 구간 끝 |
| `page` | X | `0` | 0부터 |
| `size` | X | `10` | 1~50 |

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
        "targetGrades": ["NORMAL", "EXCELLENT", "PREMIUM"],
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
    "targetGrades": ["NORMAL"],
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
      "description": "월드컵 승부예측 투표와 스코어 맞추기. template_1_sports_cheer.html",
      "theme": "theme-sports",
      "active": true
    }
  ],
  "message": null
}
```

| templateKey | name | theme | 파일 |
| --- | --- | --- | --- |
| `sports_cheer` | 스포츠 응원 | `theme-sports` | template_1_sports_cheer.html |
| `holiday_gift` | 한가위 선물 | `theme-holiday` | template_2_holiday_gift.html |
| `member_appreciation` | 회원 감사 | `theme-vip` | template_3_member_appreciation.html |
| `flash_sale` | 72h 특가 | `theme-sale` | template_4_flash_sale.html |
| `pre_registration` | 사전예약 | `theme-launch` | template_5_pre_registration.html |

```text
http://localhost:8080/api/admin/templates
```

---

## `GET /api/admin/templates/{templateKey}`

단건 조회. 없으면 `404` + `EVENT404-1`.

```text
http://localhost:8080/api/admin/templates/sports_cheer
```
