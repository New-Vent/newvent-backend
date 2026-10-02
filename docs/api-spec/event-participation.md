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