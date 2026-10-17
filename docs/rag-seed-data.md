# RAG 시드 데이터 명세

개발·QA·시연용 시드 데이터를 적재하는 기능이다.
`RagSeedDataGenerator`가 이벤트+버전을 저장하고 버전마다 청크까지 적재한다.

## 실행

```bash
./gradlew seedRagData

# 소유 관리자 지정 (없으면 id 첫 관리자)
./gradlew seedRagData -PseedAdmin=admin1
```

- `--seed-rag-data` 인자로 부팅 → 적재 후 종료 (서버로 띄워 두지 않음)
- `prod` 프로파일에서는 시작 자체를 막는다 (`RagSeedDataRunner.rejectProdIfActive`)
- 제목 단위 멱등: 삭제되지 않은 `SEED: {제목}`이 이미 있으면 그 건만 건너뛰고 없는 것만 적재
- 휴지통(soft delete)에 넣은 시드는 없는 것으로 보고 다시 만든다. 같은 제목이 휴지통에만 있으면 새로 적재된다
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

## 전체 목록 (40건 → 이벤트 40·버전 40·청크 약 189)

### 스포츠 응원 7종 (theme-sports · highlight 상단형)

| # | 제목 | 블록 | 청크 | 등급 |
|---|---|---|---|---|
| 1 | 프로야구 승부예측 이벤트 | hero·highlight·benefits·steps·cta | 5 | NORMAL |
| 2 | 축구 응원 스코어 맞히기 | hero·highlight·benefits·steps·cta | 5 | NORMAL |
| 3 | 이달의 응원왕 투표 | hero·highlight·steps·cta | 4 | NORMAL |
| 4 | 농구 3점슛 챌린지 | hero·highlight·benefits·faq·cta | 5 | NORMAL |
| 5 | e스포츠 승자 예측 | hero·highlight·benefits·steps·cta | 5 | NORMAL |
| 6 | 마라톤 응원 메시지 | hero·highlight·steps·cta | 4 | NORMAL |
| 7 | 야구 올스타 투표 | hero·highlight·benefits·cta | 4 | NORMAL |

### 시즌·명절 9종 (theme-holiday · 일정 상단형)

| # | 제목 | 블록 | 청크 | 등급 |
|---|---|---|---|---|
| 8 | 한가위 출석체크 복주머니 | hero·steps·benefits·cta | 4 | NORMAL |
| 9 | 설날 세뱃돈 뽑기 | hero·steps·benefits·cta | 4 | NORMAL |
| 10 | 추석 선물대전 | hero·steps·benefits·faq·cta | 5 | NORMAL |
| 11 | 크리스마스 데이터 선물 | hero·steps·benefits·cta | 4 | NORMAL |
| 12 | 연말 감사 인사 이벤트 | hero·highlight·steps·benefits·cta | 5 | NORMAL |
| 13 | 신년 카운트다운 | hero·steps·faq·cta | 4 | NORMAL |
| 14 | 봄맞이 축제 | hero·steps·benefits·cta | 4 | NORMAL |
| 15 | 설 연휴 로밍 혜택 | hero·steps·benefits·cta | 4 | NORMAL |
| 16 | 어린이날 가족 이벤트 | hero·steps·benefits·faq·cta | 5 | NORMAL |

### 멤버십·요금제 10종 (theme-vip · 등급 상단형)

| # | 제목 | 블록 | 청크 | 등급 |
|---|---|---|---|---|
| 17 | 최우수 등급 감사 쿠폰팩 | hero·audience·benefits·cta | 4 | BEST |
| 18 | 우수 등급 무료배송 | hero·audience·benefits·faq·cta | 5 | EXCELLENT |
| 19 | 로열티 리워드 | hero·intro·benefits·cta | 4 | NORMAL |
| 20 | 여름 데이터 대방출 | hero·intro·benefits·steps·cta | 5 | NORMAL |
| 21 | 신년 무제한 요금제 세일 | hero·compare·benefits·faq·cta | 5 | NORMAL |
| 22 | 데이터 충전소 | hero·intro·benefits·steps·cta | 5 | NORMAL |
| 23 | 장기가입 감사 프로그램 | hero·intro(2분할)·benefits·cta | 5 | NORMAL |
| 24 | 생일 축하 쿠폰 | hero·intro·benefits·steps·cta | 5 | NORMAL |
| 25 | 친구 초대 리워드 | hero·intro·benefits·steps·faq·cta | 6 | NORMAL |
| 26 | 멤버십 데이 | hero·intro·benefits·cta | 4 | NORMAL |

### 특가·할인 8종 (theme-sale · 마감·가격 상단형)

| # | 제목 | 블록 | 청크 | 등급 |
|---|---|---|---|---|
| 27 | 72시간 타임딜 특가 | hero·highlight·benefits·steps·cta | 5 | NORMAL |
| 28 | 선착순 클리어런스 | hero·highlight·benefits·steps·cta | 5 | NORMAL |
| 29 | 게릴라 반짝세일 | hero·highlight·benefits·cta | 4 | NORMAL |
| 30 | 가을 할인 쿠폰 프로모션 | hero·highlight·benefits·steps·cta | 5 | NORMAL |
| 31 | 블랙프라이데이 빅세일 | hero·highlight·benefits·steps·cta | 5 | NORMAL |
| 32 | 신학기 준비 패키지 | hero·highlight·benefits·steps·faq·cta | 6 | NORMAL |
| 33 | 여름 워터페스티벌 | hero·highlight·benefits·steps·faq·cta | 6 | NORMAL |
| 34 | 블랙프라이데이 플래시딜 | hero·highlight·benefits·steps·cta | 5 | NORMAL |

### 출시·예약 6종 (theme-launch · 안내 상단형)

| # | 제목 | 블록 | 청크 | 등급 |
|---|---|---|---|---|
| 35 | 신규 서비스 사전예약 | hero·intro·benefits·steps·cta | 5 | NORMAL |
| 36 | 얼리버드 대기자 모집 | hero·intro·steps·faq·cta | 5 | NORMAL |
| 37 | 신규 요금제 출시 | hero·intro·benefits·compare·cta | 5 | NORMAL |
| 38 | 앱 리뉴얼 기념 이벤트 | hero·intro·benefits·steps·cta | 5 | NORMAL |
| 39 | 5G 커버리지 확대 기념 | hero·intro·benefits·cta | 4 | NORMAL |
| 40 | 키즈 요금제 출시 | hero·intro·benefits·faq·cta | 5 | NORMAL |

### 청킹 엣지 (주제형 2건으로 대체)

| # | 제목 | 블록 | 검증 포인트 |
|---|---|---|---|
| 23 | 장기가입 감사 프로그램 | intro 900자 초과 → 2분할 | 800자 초과 시 청크 분할 |
| 25 | 친구 초대 리워드 | faq 질문 3개 | 중첩 `<dl>` 파싱 |

## HTML 작성 규칙 (새 시드 추가 시)

1. `data-block` 섹션으로만 구성한다. 허용 키 14종(CHECK 제약과 동일):
   `hero·highlight·intro·stats·benefits·prize·coupon·compare·audience·steps·schedule·faq·notices·cta`
2. 허용 키는 V19 CHECK와 `RagSeedDefinitionsTest.ALLOWED_BLOCKS`가 함께 검증한다
3. `compare·audience·faq`는 트리거 단어가 본문에 있어야 청킹된다:
   비교표(`비교표`·`vs`) / 대상(`참여 대상` 등) / 질문(`자주 묻는 질문`·`FAQ`)
4. `cta()` 헬퍼 사용 — `<a class="btn">` 형태 (event.css 스타일 적용 대상)
5. 카테고리별 겉모습은 `secV(블록, 변형, 내용)` + `60-variants` 클래스로 구분한다.
   `data-block` 값은 그대로 두는 것이 조건이다 (바꾸면 청크 누락).
   현재 배정: 스포츠(center·badge·grid·numbered) / 시즌(left·glow·checklist·timeline·qa) /
   멤버십(minimal·cards·lead·pricing·checklist) / 특가(poster·danger·coupon·cards·leftbar) /
   출시(split·boxed·spotlight·arrows·flush)
6. 등급이 필요하면 `new SeedDefinition(제목, html, 등급)` 3인자 사용

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
| 이미 있는데 다시 넣고 싶다 | `SEED:` 이벤트 영구 삭제 후 재실행. 휴지통에만 있으면 자동으로 다시 만들어진다 |
| 실패 건이 `failedSeeds`에 남았다 | 해당 이벤트는 자동 삭제됨. 로그 확인 후 재실행하면 다시 시도된다 |

## 주의

- 제목 prefix `SEED:` 필수 — 운영 데이터와 구분하기 위해
- `prod`에서 실행 금지 (Runner + Generator 이중 차단).
  시연 등 시간 제한 예외는 `-PseedAllowProd`로 명시적 허용. 시연 후 `SEED:` 데이터를 반드시 삭제할 것
- 동시에 두 번 실행하지 않는다 — 멱등 검사가 통과 순간에만 유효해서 중복 적재될 수 있다 (1인 수동 실행 전제)
- 임베딩 호출 비용: 약 189청크분 Bedrock 호출 (40건 기준)

## 어디서 테스트하나 (배포 서버 포함)

| 환경 | 실행 가능 | 방법 |
|---|---|---|
| 로컬 dev DB | ○ | `SPRING_PROFILES_ACTIVE=dev ./gradlew seedRagData` |
| 스테이징 서버 | ○ | prod가 아닌 프로파일(dev/staging)로 실행. 스테이징 전용 DB 필수 |
| 운영 prod 서버 | × | 차단됨 (의도). prod DB에 시드를 넣는 경로는 두지 않는다 |

배포된 서버에서 시드를 테스트하려면 **스테이징 환경을 따로 두고** 거기서 실행한다.
운영 DB를 가리킨 채 프로파일만 바꿔 실행하는 것은 금지한다 — 실수로 시드가 운영 검색에 섞인다.
스테이징 Infra(서버·DB·키) 준비는 별도 작업으로 필요하다.

## 관련 파일

- 생성기: `src/main/java/com/newvent/rag/seed/RagSeedDataGenerator.java`
- 실행기: `src/main/java/com/newvent/rag/seed/RagSeedDataRunner.java`
- 태스크: `build.gradle` (`seedRagData`)
- 테스트: `src/test/java/com/newvent/rag/seed/RagSeedDefinitionsTest.java`,
  `src/test/java/com/newvent/rag/seed/RagSeedDataRunnerTest.java`
- 평가 문항: `src/test/resources/rag/eval-questions.yaml`
