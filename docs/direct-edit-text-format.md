# 직접 수정: 문구별 서식

이번 브랜치는 문구별 색상·크기·굵기 및 초기화만 추가한다. 최신 develop의 블록·항목 삭제(removeBlocks, removeItems)는 그대로 사용한다. 별도 문구 요소 삭제(delete), deleteDepth 응답과 문구 삭제 UI는 제거했다.

## API

기존 POST /api/admin/events/{eventId}/versions/direct-edit의 edits 항목에 선택적 format을 추가한다.

```json
{"sourceVersionId":12,"edits":[{"index":1,"before":"기존 문구","after":"새 문구","format":{"color":"#d60076","size":24,"bold":true}}],"removeItems":{"benefits":[{"index":0,"before":"삭제할 혜택"}]}}
```

- color: HEX 6자리. size: 10~96px 정수. bold: true 700 / false 400.
- 생략한 속성은 유지한다. reset: true는 문구 전용 서식만 초기화한다.
- 서식만 변경할 때 after에는 before와 같은 문구를 보낸다.
- editableTexts의 format은 문구 전용 span의 color, font-size, font-weight를 반환한다.
- 기존 문구 수정 JSON과 호환된다. HTML이나 임의 CSS는 받지 않는다.

## 저장과 삭제의 결합

원본 문구 인덱스를 검증하고 문구와 서식을 반영한 뒤 기존 블록·항목 삭제를 수행한다. 서식 적용은 카드·블록을 제거하지 않으므로 삭제 항목 인덱스를 이동시키지 않는다. 모든 변경은 기존 서비스에서 새 버전 하나로 저장된다.

삭제 보호 정책과 최소 개수 검사는 기존 구현을 따른다. 빈 문자열 저장과 줄바꿈 보존도 유지한다. 저장 후 미리보기와 editableTexts를 재조회한다.

## 프론트

문구 선택 시 서식 도구와 미리보기 강조를 제공한다. 서식을 미리보기에 즉시 반영하고 변경 저장 시 전송한다. 블록·항목 삭제는 기존 순서·삭제 도구를 사용한다. 부분 글자 선택과 AI 수정 이후 서식 보존 강화는 범위 밖이다.

DB 스키마와 환경변수 변경은 없다. 백엔드 반영 후 프론트를 배포한다.
