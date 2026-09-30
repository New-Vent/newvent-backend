# 이벤트 버전 저장 지점 조회·지정·해제

## 저장 지점 목록 조회

| 항목 | 내용 |
| --- | --- |
| 메서드 | `GET` |
| 경로 | `/api/admin/events/{eventId}/versions` |
| 경로 변수 | `eventId`: 조회할 이벤트 ID (`Long`) |
| 요청 본문 | 없음 |
| 성공 상태 | `200 OK` |

해당 이벤트의 버전 중 **저장 지점(`checkpoint = true`)으로 지정된 버전만** `versionNo` 내림차순으로 반환한다. 저장 지점이 없는 경우 `versions`는 빈 배열이다. 응답 항목의 `checkpoint` 필드는 항상 `true`이므로 제공하지 않는다.

### 성공 응답 예시

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
| `versions[].versionId` | number | 버전 ID. 지정·해제 등 후속 동작에 사용할 식별자 |
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

## 저장 지점 지정

| 항목 | 내용 |
| --- | --- |
| 메서드 | `PUT` |
| 경로 | `/api/admin/events/{eventId}/versions/{versionId}/checkpoint` |
| 경로 변수 | `eventId`: 이벤트 ID, `versionId`: 해당 이벤트에 속한 버전 ID (`Long`) |
| 요청 본문 | 없음 |
| 성공 상태 | `200 OK` |

해당 버전의 `is_checkpoint`를 `true`로, `checkpointed_at`을 현재 시각으로 설정한다. 이미 저장 지점으로 지정된 버전에 다시 요청하면 성공을 반환하고, 기존 `checkpointed_at`은 유지한다.

버전의 HTML, 생성 시각, 수정 요청 이력은 변경하지 않는다.

## 저장 지점 해제

| 항목 | 내용 |
| --- | --- |
| 메서드 | `DELETE` |
| 경로 | `/api/admin/events/{eventId}/versions/{versionId}/checkpoint` |
| 경로 변수 | `eventId`: 이벤트 ID, `versionId`: 해당 이벤트에 속한 버전 ID (`Long`) |
| 요청 본문 | 없음 |
| 성공 상태 | `200 OK` |

이벤트 상태가 `PUBLISHED`이고 요청한 `versionId`가 현재 `published_version_id`와 같으면 저장 지점을 해제할 수 없으며 `409 Conflict`를 반환한다. 다른 버전을 재게시해 게시 버전이 변경되면 이전 버전의 저장 지점은 해제할 수 있다.

해제 가능한 버전은 `is_checkpoint`를 `false`로, `checkpointed_at`을 `null`로 변경한다. 이미 해제된 버전에 다시 요청해도 성공을 반환한다.

**저장 지점 해제는 버전 삭제가 아니다.** 버전 자체와 HTML, 수정 요청 이력, 게시 상태는 유지된다. 해제한 버전은 위 저장 지점 목록에서 보이지 않는다.

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

버전 관련 오류는 저장 지점 **지정·해제 API**에 적용된다. `EVENT409-0`은 **해제 API에만** 적용된다. 실패 응답의 JSON 필드 구조는 공통 예외 응답 규약을 따른다.

## 구현 범위

- `/api/admin/**` 경로에는 관리자(`ROLE_ADMIN`) 인증·인가 규칙이 적용된다.
- 저장 지점을 해제해도 버전은 삭제되지 않으며, 해당 버전은 저장 지점 목록에서만 제외된다.
- 선택한 저장 지점의 페이지 내용은 [버전 상세 및 미리보기 HTML 조회](event-version-detail.md) API에서 제공한다. 해당 버전을 기준으로 한 채팅 수정은 별도 작업에서 처리한다.