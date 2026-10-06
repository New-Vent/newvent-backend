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

## 관련 API

- 이벤트 참여: `POST /api/users/me/events/{eventId}/participations`
- 참여 요청·응답 명세: [이벤트 참여](event-participation.md)
- 결과 저장 기준: [이벤트 참여 설정 규칙](event-participation-config.md)