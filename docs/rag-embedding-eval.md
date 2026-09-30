# RAG 임베딩 모델 평가

## 목적

모델을 근거 없이 고르면 그 벡터를 계속 재사용하게 됩니다. 임베딩을 바꾸면 5,000건 전부 재색인이라 교체 비용이 큽니다. 5종을 같은 기준으로 비교해서 1종을 확정합니다.

## 후보 (5종, 1024차원 통일)

```text
로컬: bge-m3, mxbai-embed-large, snowflake-arctic-embed
AWS:  amazon.titan-embed-text-v2:0, cohere.embed-multilingual-v3
```

Bedrock 텍스트 임베딩은 4종이지만 영어 전용(Cohere English)과 구형 1536차원(Titan G1)은 제외합니다.

## 평가 기준

```text
합격: 10개 중 7개 이상
  - 일반 질문: 기대 블록이 상위 3개 안에 있으면 O
  - 무관 질문: 아무것도 안 나오면 O
동점: 한국어 질문(Q1·Q5·Q10) 맞는 쪽
그래도 동점: 싼 쪽 (Titan $0.00002 vs Cohere $0.0001)
```

7의 근거는 함정 2개 + 일반 5개입니다. 함정(Q8·Q9)은 모르는 것에 입을 다무는지 보는 최소 조건이라 2개 다 맞아야 합니다. 일반 8개는 과반(5개)을 넘겨야 실용입니다. 상위 3개 기준인 이유는 실제 RAG가 LLM에 청크 여러 개를 주기 때문입니다. top-1이 아니라 top-3 재현율로 봅니다. 동점은 한국어가 우선입니다. 서비스가 한국어라 한국어 질문을 더 맞히는 쪽을 택합니다. 비용은 마지막입니다. 둘 다 사실상 공짜라 변별력이 없습니다.

## 질문 세트 (10개)

`src/test/resources/rag/eval-questions.yaml`

| 번호 | 질문 | 기대 |
|---|---|---|
| Q1 | 신규 가입 혜택 | benefits |
| Q2 | 참여 방법 | steps |
| Q3 | 이벤트 기간 | hero |
| Q4 | 참여 버튼 문구 | cta |
| Q5 | 할인 쿠폰 | benefits |
| Q6 | 경품 응모 방법 | steps |
| Q7 | 이벤트 제목 추천 | hero |
| Q8 | 유의사항 | 없음 |
| Q9 | 존재하지 않는 내용 xyz123 | 없음 |
| Q10 | 무료 배송 | benefits |

Q8·Q9는 함정입니다. `notices`는 색인 대상이 아니라서 나오면 틀린 것이고, 무관 질문에 답이 나오면 억지로 가져오는 것입니다. 하네스 `indexTemplates()`가 `notices` 섹션을 건너뜁니다.

## 실행 방법

```bash
# WSL에서 IP 확인 (재부팅 시 바뀔 수 있음)
hostname -I | awk '{print $1}'

# Ollama 서버 기동 (이 터미널은 끄지 말고 둠)
OLLAMA_MODELS=/usr/share/ollama/.ollama/models OLLAMA_HOST=0.0.0.0:11435 ollama serve

# 다른 터미널에서 서버 확인 (첫 호출은 모델 로딩으로 30초 이상 걸릴 수 있음)
curl -m 300 http://localhost:11435/api/embed -d '{"model":"bge-m3","input":"test"}' | head -c 100

# 로컬 3종
ollama pull bge-m3 mxbai-embed-large snowflake-arctic-embed
docker compose up -d postgres
OLLAMA_URL=http://<WSL_IP>:11435 RAG_EVAL=1 ./gradlew cleanTest test --tests "*RagEvalSmoke*"

# AWS 2종 추가 (모델 접근 승인 + 키 후)
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=us-east-1
OLLAMA_URL=http://<WSL_IP>:11435 RAG_EVAL=1 RAG_EVAL_AWS=1 ./gradlew cleanTest test --tests "*RagEvalSmoke*"
```

## 결과 위치

```text
콘솔 끝: == 모델: ?/10 (5줄)
파일: build/rag-eval/eval-*.md (같은 내용)
```

## 환경 변수

```text
RAG_EVAL=1        하네스 실행 (없으면 스킵, CI 스킵)
RAG_EVAL_AWS=1    Bedrock 2종 포함 (키 필요)
OLLAMA_URL        로컬 Ollama 주소 (기본 localhost:11434)
AWS_REGION        us-east-1
```

## 판정 순서

```text
1. 5종 점수 비교 → 최고점 채택
2. 미달(7개 미만)이면 min-score 0.6 → 0.5로 재실행
3. 그래도 미달이면 모델 교체 검토
```

## 유사도 (min-score)가 의미하는 것

하네스는 코사인 거리로 자릅니다 (`RagChunkRepository.findSimilar`).

```text
거리 0.4 이하만 통과 = 유사도 0.6 이상만 통과
거리 0.5 이하만 통과 = 유사도 0.5 이상만 통과
```

유사도는 두 문장의 벡터가 얼마나 같은 방향을 보는지입니다. 1에 가까울수록 같은 뜻입니다. 기준을 0.6에서 0.5로 낮추면 문이 넓어집니다. 버려지던 게 들어오니 일반 질문이 맞을 확률이 오르고, 반대로 함정(Q8·Q9)에 잡동사니가 들어올 위험도 생깁니다. 2차 실행에서 함정이 그대로 O이므로 0.5를 씁니다.

## 차원을 1024로 고정한 이유

```text
위로: 후보 5종 전부 1024차원이 최대라 올릴 수 없음
  (1536 Titan G1은 처음에 제외)
아래로(512): 가능은 하나 아끼는 게 저장공간 약 10MB뿐
  (Bedrock은 토큰당 과금이라 차원과 무관, 5,000건 규모 체감 없음)
  + cohere는 512 네이티브 미지원이라 공평 비교도 안 됨
```

품질이 합격선을 넘은 상태라 위로 갈 이유가 없고, 아래로 갈 실익이 없어 1024를 유지합니다.

## 실행 결과 (2026-09-30)

### 1차: 유사도 0.6 (`build/rag-eval/eval-20260930-1039.md`)

| 모델 | 점수 | 합격 |
|---|---|---|
| bge-m3 | 6/10 | X |
| mxbai-embed-large | 2/10 | X |
| snowflake-arctic-embed | 3/10 | X |
| amazon.titan-embed-text-v2:0 | 2/10 | X |
| cohere.embed-multilingual-v3 | 6/10 | X |

전원 미달이라 판정 순서 2번으로 갑니다. 틀린 문항의 모양이 둘로 갈립니다.

```text
bge-m3·cohere: 빈 결과로 틀림 (Q1·Q4·Q7·Q10 등) → 임계값이 빡셀 가능성
mxbai·arctic:  잡동사니 반환으로 틀림 (cta 편향) → 모델 자체가 한국어에 약함
titan:         일반 8개 전부 빈 결과 → 함정 2개만 맞음
```

### 2차: 유사도 0.5 (`build/rag-eval/eval-20260930-1049.md`)

`RagEvalSmokeTest.java`의 `0.4`를 주석 보존하고 `0.5`로 완화했습니다.

| 모델 | 점수 | 0.6 대비 | 합격 |
|---|---|---|---|
| cohere.embed-multilingual-v3 | 9/10 | +3 (Q3·Q4·Q7 뒤집음) | O |
| bge-m3 | 8/10 | +2 (Q1·Q10 뒤집음) | O |
| snowflake-arctic-embed | 3/10 | 변동 없음 | X |
| mxbai-embed-large | 2/10 | 변동 없음 | X |
| amazon.titan-embed-text-v2:0 | 2/10 | 변동 없음 | X |

```text
좋은 소식: 함정 Q8·Q9는 0.5에서도 그대로 O입니다. 관문을 넓혀도 억지 반환이 안 생깁니다.
나쁜 소식 (titan): 0.5에서도 일반 8개 전부 빈 결과입니다. 거리 분포 자체가 다른
스케일이라 0.5 가지고 안 됩니다. 더 낮추면 함정이 깨질 수 있어 titan은 탈락입니다.
mxbai·arctic: 임계값과 무관하게 cta 편향이라 탈락입니다.
```

### 테스트로 인한 확정

```text
cohere.embed-multilingual-v3 (9/10) 1위
bge-m3 (8/10) 2위
```

동점 규정이 필요 없습니다 (9 vs 8). cohere가 유일하게 틀린 Q10(무료 배송)은 bge-m3가 맞힌 문항이라, 한국어 표현 보강(동의어·청킹) 시 따라올 여지가 있습니다.

```text
탈락: titan (스케일 불일치), mxbai·arctic (cta 편향)
```

### 비용까지 고려한 확정

```text
cohere.embed-multilingual-v3 최종 채택
```

운영 규모 환산 (색인 5,000건 × 200토큰 = 1M토큰 기준):

```text
cohere 색인 일회성: 약 $0.10
cohere 질의 1회:    약 $0.000001
bge-m3:             로컬 무료라 0원
```

둘 다 사실상 공짜라 비용이 순위를 안 뒤집습니다. 남은 차이는 돈이 아니라 운영 형태(AWS 의존 vs 로컬)뿐이고, 점수가 높은 쪽을 택합니다. AWS 의존을 피해야 한다는 조건이 생기면 차선 bge-m3 (8/10)로 내립니다.

## 비용

평가 실행 1회는 $0.01 미만입니다. (색인 20×5 + 검색 10×5 = 150회 호출, notices 제외)
실측: AWS 포함 7회 실행에 약 $0.0035 (5원 미만)입니다.

운영 예상은 위 "비용까지 고려한 확정"을 보세요. (색인 일회성 약 $0.10, 질의 1회 약 $0.000001)

## 주의

- `@DataJpaTest`라 끝나면 롤백됩니다. 평가용 벡터가 남지 않습니다.
- 단언이 없습니다. 점수표 보고 사람이 정합니다.
- Recall@3 같은 통계는 내지 않습니다. 20개 블록으로 통계가 안 나옵니다.
