# RAG 시드 데이터 명세

개발·QA·시연용 시드 데이터를 적재하는 기능이다.
`RagSeedDataGenerator`가 이벤트+버전을 저장하고 버전마다 청크까지 적재한다.

## 실행

```bash
./gradlew seedRagData
```

- `--seed-rag-data` 인자로 부팅 → 적재 후 종료 (서버로 띄워 두지 않음)
- `prod` 프로파일에서는 시작 자체를 막는다 (`RagSeedDataRunner.rejectProdIfActive`)
- 제목 단위 멱등: `SEED: {제목}`이 이미 있으면 그 건만 건너뛰고 없는 것만 적재
- 적재 중 실패한 건은 이벤트·버전을 삭제하고(버전·청크는 FK CASCADE) 다음 실행 때 재시도

## 적재 흐름 (시드 1건당)

```
1. existsByTitleStartingWith("SEED: {제목}") → 있으면 skip
2. Event.createDraft(owner, null, "SEED: {제목}", start-7일, end+30일, grade) → 저장
3. EventVersion.create(event, 1, html, null) → 저장
4. event.publish(version) → 저장 (시연에 바로 보이게 PUBLISHED)
5. embedding.reindex(eventId, versionId) → 청크 적재
6. 실패 시 events.deleteById(eventId) 로 정리 + failedSeeds 에 기록
```

- 소유 관리자: `admins` 첫 1행 사용. 없으면 시작 전에 실패한다 (FK `owner_admin_id` NOT NULL)
- `template_id`는 넣지 않는다 (nullable, 템플릿 행 불필요)
- 등급: 지정 없으면 NORMAL, 2건만 지정 (아래 표 참고)

## 전체 목록 (29건 → 이벤트 29·버전 29·청크 약 101)

### 템플릿 변형 14종

| # | 제목 | 블록 | 청크 | 등급 |
|---|---|---|---|---|
| 1 | 프로야구 승부예측 이벤트 | hero·benefits·steps·cta | 4 | NORMAL |
| 2 | 축구 응원 스코어 맞히기 | hero·benefits·steps·cta | 4 | NORMAL |
| 3 | 시즌 응원 투표 | hero·highlight·cta | 3 | NORMAL |
| 4 | 한가위 출석체크 복주머니 | hero·benefits·steps·cta | 4 | NORMAL |
| 5 | 설날 세뱃돈 뽑기 | hero·steps·cta | 3 | NORMAL |
| 6 | 추석 선물대전 | hero·benefits·cta | 3 | NORMAL |
| 7 | 최우수 등급 감사 쿠폰팩 | hero·benefits·audience·cta | 4 | BEST |
| 8 | 우수 등급 무료배송 | hero·benefits·cta | 3 | EXCELLENT |
| 9 | 로열티 리워드 | hero·intro·benefits·cta | 4 | NORMAL |
| 10 | 72시간 타임딜 특가 | hero·highlight·benefits·cta | 4 | NORMAL |
| 11 | 선착순 클리어런스 | hero·benefits·steps·cta | 4 | NORMAL |
| 12 | 게릴라 반짝세일 | hero·cta | 2 | NORMAL |
| 13 | 신규 서비스 사전예약 | hero·benefits·steps·cta | 4 | NORMAL |
| 14 | 얼리버드 대기자 모집 | hero·steps·faq·cta | 4 | NORMAL |

### 실제형 10종

| # | 제목 | 블록 | 청크 | 등급 |
|---|---|---|---|---|
| 15 | 여름 데이터 대방출 | hero·benefits·cta | 3 | NORMAL |
| 16 | 가을 할인 쿠폰 프로모션 | hero·benefits·steps·cta | 4 | NORMAL |
| 17 | 연말 감사 경품 이벤트 | hero·cta | 2 | NORMAL |
| 18 | 신년 무제한 요금제 세일 | hero·benefits·compare·cta | 4 | NORMAL |
| 19 | 봄맞이 축제 | hero·benefits·steps·cta | 4 | NORMAL |
| 20 | 블랙프라이데이 빅세일 | hero·benefits·steps·cta | 4 | NORMAL |
| 21 | 신학기 준비 패키지 | hero·benefits·steps·faq·cta | 5 | NORMAL |
| 22 | 여름 워터페스티벌 | hero·benefits·steps·cta | 4 | NORMAL |
| 23 | 블랙프라이데이 플래시딜 | hero·highlight·benefits·cta | 4 | NORMAL |
| 24 | 신년 카운트다운 | hero·steps·cta | 3 | NORMAL |

### 엣지 케이스 5종

| # | 제목 | 블록 | 청크 | 검증 포인트 |
|---|---|---|---|---|
| 25 | 초긴 안내문 이벤트 | hero·cta | 3 (hero 2개로 분할) | 800자 초과 시 청크 분할 |
| 26 | 특수문자 대잔치 | hero·benefits·cta | 3 | 이모지·★♪ 임베딩 |
| 27 | 최소 구성 이벤트 | hero·cta | 2 | 최소 블록 구성 |
| 28 | Welcome Global Event | hero·benefits·cta | 3 | 영문 임베딩 품질 |
| 29 | 요금제 비교표 이벤트 | hero·compare·cta | 3 | 중첩 `<div>`+`<table>` 파싱 |

## HTML 작성 규칙 (새 시드 추가 시)

1. `data-block` 섹션으로만 구성한다. 허용 키 10종(CHECK 제약과 동일):
   `hero·highlight·intro·benefits·compare·audience·steps·faq·notices·cta`
2. `stats·prize·coupon·schedule` 키를 쓰면 CHECK 위반으로 적재가 터진다.
3. `compare·audience·faq`는 트리거 단어가 본문에 있어야 청킹된다:
   비교표(`비교표`·`vs`) / 대상(`참여 대상` 등) / 질문(`자주 묻는 질문`·`FAQ`)
4. `cta()` 헬퍼 사용 — `<a class="btn">` 형태 (event.css 스타일 적용 대상)
5. 등급이 필요하면 `new SeedDefinition(제목, html, 등급)` 3인자 사용

## 검증

```bash
# 단건 재색인으로 특정 시드 버전 다시 적재
curl -X POST http://localhost:8080/api/admin/rag/reindex \
  -H 'Content-Type: application/json' \
  -d '{"eventId":{id},"versionId":{versionId}}'

# 적재 현황 (버전별 청크 수)
curl "http://localhost:8080/api/admin/rag/index-status?eventId={id}"

# 검색 품질 (상위 청크·거리)
curl "http://localhost:8080/api/admin/rag/search-preview?eventId={id}&query=데이터+혜택&topK=3"

# 평가 스모크 (RAG_EVAL=1, DB·Bedrock 필요)
RAG_EVAL=1 ./gradlew test --tests "com.newvent.rag.RagEvalSmokeTest"
```

시드용 평가 문항 40개는 `src/test/resources/rag/eval-questions.yaml`에 있다
(일반 8·negative 5·블록별 11·특정 문구 13·엣지 3).

## 문제 해결

| 증상 | 원인·대처 |
|---|---|
| `시드 적재용 관리자가 없다` | `admins`가 비어 있음. 관리자 계정을 먼저 만든다 |
| `RAG 시드는 prod 에서 실행할 수 없다` | prod 프로파일 차단. dev/local에서 실행한다 |
| 이미 있는데 다시 넣고 싶다 | `SEED:` 이벤트 삭제 후 재실행 (제목 단위 멱등이라 전체 삭제 불필요) |
| 실패 건이 `failedSeeds`에 남았다 | 해당 이벤트는 자동 삭제됨. 로그 확인 후 재실행하면 다시 시도된다 |

## 주의

- 제목 prefix `SEED:` 필수 — 운영 데이터와 구분하기 위해
- `prod`에서 실행 금지 (`RagSeedDataRunner`가 차단)
- 임베딩 호출 비용: 약 101청크분 Bedrock 호출 (29건 기준)

## 관련 파일

- 생성기: `src/main/java/com/newvent/rag/seed/RagSeedDataGenerator.java`
- 실행기: `src/main/java/com/newvent/rag/seed/RagSeedDataRunner.java`
- 태스크: `build.gradle` (`seedRagData`)
- 테스트: `src/test/java/com/newvent/rag/seed/RagSeedDefinitionsTest.java`,
  `src/test/java/com/newvent/rag/seed/RagSeedDataRunnerTest.java`
- 평가 문항: `src/test/resources/rag/eval-questions.yaml`
