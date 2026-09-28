# 이벤트 저장 지점 목록 조회

## API

| 항목 | 내용 |
| --- | --- |
| 메서드 | `GET` |
| 경로 | `/api/admin/events/{eventId}/versions` |
| 경로 변수 | `eventId`: 조회할 이벤트 ID (`Long`) |
| 요청 본문 | 없음 |
| 성공 상태 | `200 OK` |

해당 이벤트의 버전 중 **저장 지점(`checkpoint = true`)으로 지정된 버전만** `versionNo` 내림차순으로 반환한다. 저장 지점이 없는 경우 `versions`는 빈 배열이다. 응답 항목의 `checkpoint` 필드는 항상 같은 값이 되므로 제공하지 않는다.

## 성공 응답 예시

```json
{
  "success": true,
  "data": {
    "eventId": 12,
    "title": "2026 월드컵 응원 이벤트",
    "versions": [
      {
        "versionId": 102,
        "versionNo": 2,
        "createdAt": "2026-09-21T14:20:00+09:00",
        "published": true,
        "sourceVersionNo": 1,
        "requestContent": "소개 문구를 친근하게 정리해 줘"
      },
      {
        "versionId": 101,
        "versionNo": 1,
        "createdAt": "2026-09-20T10:00:00+09:00",
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
| `data.versions` | array | 저장 지점 목록. 저장 지점이 없으면 `[]` |
| `versions[].versionId` | number | 버전 ID. 상세 조회 등 후속 동작에서 사용할 식별자 |
| `versions[].versionNo` | number | 이벤트 내 버전 번호 |
| `versions[].createdAt` | string | 버전 생성 일시 (`OffsetDateTime`) |
| `versions[].published` | boolean | 이벤트 상태가 `PUBLISHED`이고 해당 버전이 `events.publishedVersion`인 경우 `true` |
| `versions[].sourceVersionNo` | number 또는 null | 이어서 작업한 원본 버전 번호. 출처가 없으면 `null` |
| `versions[].requestContent` | string 또는 null | 버전에 연결된 수정 요청 원문. 연결된 요청이 없으면 `null` |

`requestContent`는 화면용 요약 문구가 아니라 저장된 요청 원문이다. 최초 생성 버전도 저장 지점으로 지정되어 있으면 목록에 포함되며, 연결된 요청 메시지가 없을 경우 `null`을 반환한다.

### 저장 지점이 없는 경우

이벤트가 존재하지만 조회할 저장 지점이 없으면 `200 OK`를 반환한다.

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

## 실패 응답

| 상황 | HTTP 상태 | 오류 코드 |
| --- | --- | --- |
| 이벤트가 없거나 삭제된 경우 | `404 Not Found` | `EVENT404-0` (`EVENT_NOT_FOUND`) |

실패 응답의 JSON 필드 구조는 공통 예외 응답 규약을 따른다.

## 구현 범위

- 현재 Controller에는 관리자 인증·인가 적용 TODO가 남아 있다. 관리자 접근 제한은 인증·인가 구현 시 연결한다.
- 이 API는 목록만 반환한다. 선택한 버전의 페이지 내용 조회와 해당 버전을 기준으로 한 채팅 수정은 별도 API에서 처리한다.
