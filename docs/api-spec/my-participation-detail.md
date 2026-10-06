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

## 관련 API

- 이벤트 참여: `POST /api/users/me/events/{eventId}/participations`
- 내 참여 목록: `GET /api/users/me/participations`
- 참여 요청·응답 명세: [이벤트 참여](event-participation.md)
- 결과 저장 기준: [이벤트 참여 설정 규칙](event-participation-config.md)