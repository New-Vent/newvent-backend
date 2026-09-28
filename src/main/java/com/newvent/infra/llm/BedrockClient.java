package com.newvent.infra.llm;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

/**
 * AWS Bedrock 호출.
 *
 *  bedrock-runtime + Converse API 를 쓴다 (bedrock-mantle 아님)
 *  모델 : google.gemma-3-27b-it 이다
 *  이 파일을 고칠 때는 벤치마크(model-benchmark/versions/v11/provider.py)도 참고해야 함
 */
public class BedrockClient implements LlmClient {


    private static final float TEMPERATURE = 0.2f;
    public static final String DEFAULT_REGION = "us-east-1";


    // Bedrock 모델 id 의 모양 — <b>벤더 접두사는 영문자만</b>이다.
    private static final java.util.regex.Pattern MODEL_ID =
            java.util.regex.Pattern.compile("^[a-z]+\\.[A-Za-z0-9.:\\-]+$");

    // Ollama 이름
    public static boolean looksLikeModelId(String model) {
        return model != null && MODEL_ID.matcher(model).matches();
    }

    private final BedrockRuntimeClient bedrock;
    private final String model;
    private final String region;

    public BedrockClient(String region, String model, int timeoutSeconds) {
        this.region = (region == null || region.isBlank()) ? DEFAULT_REGION : region;
        this.model = model;
        this.bedrock = BedrockRuntimeClient.builder()
                .region(Region.of(this.region))
                // 기본 자격증명 체인 — 환경변수 · ~/.aws/credentials · IAM 역할 순
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(timeoutSeconds))
                        .build())
                .build();
    }

    @Override
    public String providerName() {
        return "bedrock";
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public void close() {
        bedrock.close();
    }

    // ══════════════════════════════════════════════════════════════

    @Override
    public Response chat(Request r) {
        long t0 = System.nanoTime();
        ConverseResponse res;
        try {
            res = bedrock.converse(build(r));
        } catch (BedrockRuntimeException e) {
            throw new LlmCallException(
                    "Bedrock 호출이 실패했습니다 (model=" + model + ", region=" + region + "). "
                    + "모델 접근 권한·Marketplace 구독·사용 사례 제출을 확인하세요. "
                    + "교차 리전 추론 프로파일이 필요한 모델은 id 에 us. 접두사가 붙습니다. — "
                    + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new LlmCallException(
                    "Bedrock 에 연결하지 못했습니다 (region=" + region + "). "
                    + "자격증명(AWS_PROFILE·AWS_ACCESS_KEY_ID)이 설정돼 있나요? — " + e.getMessage(), e);
        }
        long wallMs = (System.nanoTime() - t0) / 1_000_000;

        return new Response(textOf(res), inputTokens(res), outputTokens(res),
                wallMs, isTruncated(res.stopReason(), outputTokens(res), r.mode().maxTokens));
    }

    private ConverseRequest build(Request r) {
        InferenceConfiguration.Builder cfg = InferenceConfiguration.builder()
                .maxTokens(r.mode().maxTokens)        // HTML 1536 · ROUTER 256
                .temperature(TEMPERATURE);

        ConverseRequest.Builder req = ConverseRequest.builder()
                .modelId(model)
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(r.user() == null ? "" : r.user()))
                        .build())
                .inferenceConfig(cfg.build());

        if (r.system() != null && !r.system().isBlank()) {
            req.system(SystemContentBlock.fromText(r.system()));
        }

        // ★ seed 는 Converse 에 없다
        //   Ollama 는 options.seed 로 재현성을 줬지만 Bedrock 에는 대응 필드가 없다.
        //   같은 입력이라도 출력이 흔들릴 수 있다 — 회귀 테스트를 짤 때 기억할 것.
        //
        // ★ 라우터 JSON 강제도 없다
        //   Ollama 는 format:"json" 으로 강제했는데 Converse 에는 그게 없다.
        //   toolConfig 로 스키마를 강제하는 방법이 있지만 아직 안 쓴다 —
        //   v11 실측에서 gemma 가 스키마를 10/10 지켰다. 필요해지면 그때 넣는다.
        return req.build();
    }

    // ── 응답 읽기 ─────────────────────────────────────────────────

    /** content 블록이 여러 개로 쪼개져 올 수 있다. 텍스트만 이어 붙인다. */
    private static String textOf(ConverseResponse res) {
        if (res.output() == null || res.output().message() == null) return "";
        List<ContentBlock> blocks = res.output().message().content();
        if (blocks == null) return "";
        return blocks.stream()
                .map(ContentBlock::text)
                .filter(t -> t != null && !t.isEmpty())
                .collect(Collectors.joining());
    }

    /**
     * ★★ 잘림 판정 — 안 보면 잘린 HTML 을 정상으로 받는다
     *
     *   Ollama   done_reason == "length"
     *   Bedrock  stopReason  == MAX_TOKENS
     *
     * 이 매핑이 틀리면 조용히 깨진다. Jsoup 이 끊긴 태그를 자동으로 닫아버려서
     * **검증까지 통과**하고, 잘린 페이지가 그대로 저장·게시된다.
     * LlmSmokeTest ⑤ 가 일부러 잘리게 해서 이 줄을 검증한다.
     *
     * ★ 2차 방어선을 같이 둔다
     *   출력 토큰이 <b>그 호출의 상한</b>에 닿았는데 stopReason 이 다른 값이면 의심한다.
     *   오탐의 대가는 재시도 1회(gemma 기준 1원 미만)이고, 미탐의 대가는
     *   **잘린 페이지 게시**다. 비대칭이 크다.
     *   실제로 v8 에서 gpt-oss-120b 가 캡 256 을 추론 토큰으로 다 쓰고
     *   빈 응답을 낸 적이 있다.
     *
     * ★ 상한은 **호출한 모드의 것**이어야 한다
     *   처음엔 Mode.values() 를 돌며 "둘 중 하나에 닿았나" 로 짰는데,
     *   그러면 HTML 호출(상한 1536)이 256 토큰만 내도 ROUTER 상한에 걸려
     *   **거의 모든 HTML 호출이 잘린 것으로 찍힌다.** 실측 HTML 출력이 600~800 토큰이다.
     *
     * @param cap 이 호출에 쓴 maxTokens (HTML 1536 · ROUTER 256)
     */
    static boolean isTruncated(StopReason stopReason, Integer outputTokens, int cap) {
        if (stopReason == StopReason.MAX_TOKENS) return true;
        return outputTokens != null && cap > 0 && outputTokens >= cap;
    }

    /**
     * ★ outputTokens 에는 **추론 토큰이 포함**된다
     *   차액은 전부 추론 토큰이고 Converse 가 content 로 안 돌려주는데 과금은 된다.
     *   비용 추적 관점에서는 이 값이 맞다. 다만 로그를 볼 때
     *   "출력 토큰이 많다 = 긴 답" 으로 읽으면 틀린다.
     */
    private static int outputTokens(ConverseResponse res) {
        TokenUsage u = res.usage();
        return (u == null || u.outputTokens() == null) ? 0 : u.outputTokens();
    }

    private static int inputTokens(ConverseResponse res) {
        TokenUsage u = res.usage();
        return (u == null || u.inputTokens() == null) ? 0 : u.inputTokens();
    }
}
