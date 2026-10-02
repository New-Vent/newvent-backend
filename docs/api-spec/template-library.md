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
필수 블록, 블록 단일성, 블록 내용 형태, 기간·CTA 슬롯을 검사한다.
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

V14__add_template_owner.sql이 event_templates.owner_admin_id와 인덱스를 추가한다.
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
