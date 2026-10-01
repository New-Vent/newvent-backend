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