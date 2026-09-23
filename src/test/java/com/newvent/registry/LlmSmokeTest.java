package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newvent.infra.llm.LlmClient;
import com.newvent.infra.llm.OllamaClient;

/**
 * 실제 모델로 한 바퀴 돌려본다. **CI 에서는 돌지 않는다.**
 *
 *   LLM_SMOKE=1 ./gradlew test --rerun                          기본 (ollama)
 *   LLM_SMOKE=1 LLM_MODEL=exaone3.5:7.8b ./gradlew test --rerun
 *   LLM_SMOKE=1 LLM_PROVIDER=bedrock ./gradlew test --rerun     (BedrockClient 생기면)
 *   OLLAMA=1 ./gradlew test --rerun                             예전 명령도 그대로 됨
 *
 * ★ provider 중립이다
 *   구현체를 client() 한 곳에서만 고른다. Bedrock 으로 바꿀 때 이 파일에서
 *   고칠 곳은 switch 한 줄뿐이고, 테스트 본문은 그대로 유효하다.
 *   본문이 주장하는 건 "결과가 검증을 통과한다" 이지 "Ollama 가 어떻다" 가 아니기 때문이다.
 *
 * ★ 왜 CI 에 넣지 않는가
 *   깃허브 러너에는 GPU 도 Ollama 도 없다. 그리고 있다 해도 넣으면 안 된다 —
 *   벤치마크에서 회차 간 편차가 16.7pp 였다. 이유 없이 빨간불이 나는 테스트는
 *   사람들이 무시하기 시작하고, 그때부터 진짜 실패도 같이 묻힌다.
 *
 * ★ 실제 파이프라인과 같은 순서로 돈다
 *   extract → sanitize → validate. 생성은 sanitizeGenerated, 수정은 sanitizeEdited 다.
 *   여기서 순서를 다르게 하면 "스모크는 통과하는데 서비스는 깨지는" 상태가 된다.
 *   실제로 그래서 href="#" 유실을 6주 동안 못 봤다.
 */
@EnabledIf("스모크_켜짐")
class LlmSmokeTest {

    static boolean 스모크_켜짐() {
        return "1".equals(System.getenv("LLM_SMOKE")) || "1".equals(System.getenv("OLLAMA"));
    }

    private static String env(String k, String fallback) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /** ★ 구현체를 고르는 유일한 지점. Bedrock 은 여기 한 줄만 추가하면 된다. */
    private LlmClient client() {
        String provider = env("LLM_PROVIDER", "ollama");
        return switch (provider) {
            case "ollama" -> new OllamaClient(
                    env("OLLAMA_URL", "http://localhost:11434"),
                    env("LLM_MODEL", env("OLLAMA_MODEL", "qwen2.5:7b")),
                    180);
            // case "bedrock" -> new BedrockClient(env("AWS_REGION", "ap-northeast-2"),
            //                                     env("LLM_MODEL", "..."), 180);
            default -> throw new IllegalStateException(
                    "모르는 provider: " + provider + " (ollama | bedrock)");
        };
    }

    private static void 기록(String 무엇, LlmClient.Response r) {
        System.out.printf("  %-10s %5.1f초  입력 %4d  출력 %4d  잘림 %s%n",
                무엇, r.wallMs() / 1000.0, r.inputTokens(), r.outputTokens(), r.truncated());
    }

    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("① 생성 — 실제 모델 출력이 검증을 통과한다")
    void 생성_왕복() {
        LlmClient.Response r = client().chat(LlmClient.Request.html(
                PromptBuilder.generate(),
                """
                이벤트명: 여름 데이터 대방출
                기간: 2026-07-01 ~ 2026-07-31
                혜택: 데이터 3GB 즉시 지급, 월 요금 30% 할인
                참여 방법: 요금제 선택 후 온라인 가입
                """));
        기록("생성", r);

        assertFalse(r.truncated(),
                "출력이 상한에 걸려 잘렸습니다. Mode.HTML 의 1536 을 보거나, 프롬프트가 길어졌는지 보세요.");
        assertTrue(r.outputTokens() > 0, "출력 토큰이 0 입니다. 응답 파싱이 잘못됐습니다.");
        // ★ inputTokens 는 단언하지 않는다 — Ollama 는 프롬프트가 캐시되면 0 으로 온다.
        //   Bedrock 은 항상 오므로, 전환 후에는 여기에 > 0 단언을 넣을 수 있다.

        String html = BlockValidator.sanitizeGenerated(BlockValidator.extract(r.content()));
        assertFalse(html.isBlank(), "HTML 을 못 뽑았습니다. 원문:\n" + r.content());

        List<BlockValidator.Failure> fails = BlockValidator.validateGenerated(html);
        assertTrue(fails.isEmpty(),
                "1차 생성이 검증에 걸렸습니다: " + fails + "\n─── 출력 ───\n" + html);
    }

    @Test
    @DisplayName("② 블록 수정 — 그 블록만 바뀌고 나머지는 남는다")
    void 수정_왕복() {
        String 현재 = """
                <section data-block="hero"><h1>여름 데이터 대방출</h1><p>시원한 여름</p></section>
                <section data-block="benefits"><ul><li>데이터 3GB</li><li>요금 30% 할인</li></ul></section>
                <section data-block="cta"><a href="#" class="btn">참여하기</a></section>""";

        // ★ 수정 검증은 "원본 대비" 다. before 를 떼어내서 같이 넘긴다.
        String before = BlockValidator.blockOf(현재, Block.CTA);

        LlmClient.Response r = client().chat(LlmClient.Request.html(
                PromptBuilder.edit(Block.CTA),
                "현재 영역:\n" + before + "\n\n요청: 버튼 문구를 '지금 신청하기' 로 바꿔줘"));
        기록("수정", r);

        assertFalse(r.truncated(), "수정 출력이 잘렸습니다.");

        String out = BlockValidator.sanitizeEdited(BlockValidator.extract(r.content()));
        List<BlockValidator.Failure> fails = BlockValidator.validateEdited(Block.CTA, before, out);
        assertTrue(fails.isEmpty(),
                "수정 결과가 검증에 걸렸습니다: " + fails + "\n─── 출력 ───\n" + out);

        String 병합 = BlockValidator.merge(현재, Block.CTA, out);
        assertTrue(병합.contains("여름 데이터 대방출"), "건드리면 안 되는 hero 가 사라졌습니다.");
        assertTrue(병합.contains("데이터 3GB"),        "건드리면 안 되는 benefits 가 사라졌습니다.");
        assertTrue(병합.contains("href=\"#\""),
                "href 가 사라졌습니다. 모델이 바꿨거나, Safelist 의 \"#\" 프로토콜이 빠졌습니다.");
    }

    @Test
    @DisplayName("③ 슬롯 수정 — 서버가 채울 자리가 살아남는다")
    void 슬롯_보존_왕복() {
        String before = """
                <section data-block="hero" class="vp-hero">\
                <h1>여름 데이터 대방출</h1>\
                <p class="vp-period" data-slot="period"></p>\
                </section>""";

        LlmClient.Response r = client().chat(LlmClient.Request.html(
                PromptBuilder.edit(Block.HERO),
                "현재 영역:\n" + before + "\n\n요청: 제목을 더 강렬하게 바꿔줘"));
        기록("슬롯수정", r);

        String out = BlockValidator.sanitizeEdited(BlockValidator.extract(r.content()));

        assertTrue(out.contains("data-slot=\"period\""),
                "슬롯이 사라졌습니다. 이 이벤트는 기간을 영영 못 채웁니다.\n─── 출력 ───\n" + out);

        List<BlockValidator.Failure> fails = BlockValidator.validateEdited(Block.HERO, before, out);
        assertTrue(fails.isEmpty(),
                "슬롯 수정이 검증에 걸렸습니다: " + fails + "\n─── 출력 ───\n" + out);
    }

    @Test
    @DisplayName("④ 라우터 — JSON 한 줄이 파싱되고 값이 유효하다")
    void 라우터_왕복() throws Exception {
        LlmClient.Response r = client().chat(LlmClient.Request.router(
                PromptBuilder.router(),
                "혜택에 '제휴 카페 쿠폰' 한 줄 추가해줘"));
        기록("라우터", r);

        assertFalse(r.truncated(),
                "라우터 출력이 256 토큰에 걸려 잘렸습니다. "
                + "추론 모델이면 사고에 다 씁니다 — Mode.ROUTER 상한을 512 로 올리세요.");

        String raw = r.content().trim();
        int s = raw.indexOf('{'), e = raw.lastIndexOf('}');
        assertTrue(s >= 0 && e > s,
                "JSON 을 못 찾았습니다. Ollama 는 format:\"json\" 으로 강제하지만 "
                + "Bedrock 에는 그게 없습니다 — toolConfig 로 강제할지 정해야 합니다.\n원문:\n" + raw);

        JsonNode j = new ObjectMapper().readTree(raw.substring(s, e + 1));

        String op = j.path("op").asText(null);
        assertNotNull(op, "op 필드가 없습니다: " + j);
        assertTrue(List.of("EDIT", "ADD", "DELETE", "STYLE").contains(op),
                "모르는 op 입니다: " + op);

        String target = j.path("target").asText(null);
        if (target != null && !target.isBlank() && !"null".equals(target)) {
            assertDoesNotThrow(() -> Block.of(target),
                    "레지스트리에 없는 영역을 골랐습니다: " + target);
        }
    }

    @Test
    @DisplayName("⑤ ★ 일부러 잘리게 하면 truncated 가 true 로 온다")
    void 잘림이_감지된다() {
        // ★ 왜 이 테스트가 필요한가
        //   나머지 테스트는 전부 assertFalse(truncated) 다. "잘리지 않았다" 만 주장하고,
        //   "잘렸을 때 제대로 true 가 되는가" 는 아무도 확인하지 않는다.
        //   이 매핑이 틀리면 잘린 HTML 이 그대로 저장된다 — Jsoup 이 끊긴 태그를
        //   자동으로 닫아버려서 검증도 통과한다. 조용히 깨지는 종류다.
        //
        //     Ollama   done_reason == "length"
        //     Bedrock  stopReason  == "max_tokens"
        //
        // ★ 왜 "지어내라" 가 아니라 "받아쓰라" 인가
        //   처음엔 "혜택 40개를 만들어라" 로 시켰는데 463 토큰만 나오고 멈췄다.
        //   모델은 분량 요구를 따르지 않는다 — 적당히 만들고 끝낸다.
        //   받아쓰기는 다르다. 줄 게 있으면 그만큼 낸다. 그래서 길이를 통제할 수 있다.
        //
        // ★ 왜 ROUTER 모드인가
        //   상한이 256 이라 1536 보다 넘기기 쉽다. 잘림 판정은 클라이언트 한 곳에서
        //   하므로(done_reason), 어느 모드로 재든 같은 코드를 검증한다.
        //   프로덕션 코드는 건드리지 않는다.
        StringBuilder 받아쓸것 = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            받아쓸것.append(i).append(". 여름 데이터 대방출 이벤트의 ")
                    .append(i).append("번째 안내 문구입니다.\n");
        }

        LlmClient.Response r = client().chat(LlmClient.Request.router(
                "너는 받아쓰기를 하는 도우미다. 요약하거나 생략하지 마라.",
                "다음 목록을 한 줄도 빠뜨리지 말고 그대로 JSON 배열로 옮겨라.\n\n" + 받아쓸것));
        기록("잘림유도", r);

        int 상한 = LlmClient.Mode.ROUTER.maxTokens;

        assertTrue(r.outputTokens() >= 상한 - 16,
                "출력이 상한에 닿지 않아 잘림을 확인할 수 없습니다. 출력 " + r.outputTokens()
                + " / 상한 " + 상한 + ". 받아쓸 항목 수를 늘리세요.");

        assertTrue(r.truncated(),
                "출력 " + r.outputTokens() + " 토큰으로 상한 " + 상한 + " 에 닿았는데 "
                + "truncated 가 false 입니다. done_reason / stopReason 매핑이 틀렸습니다.\n"
                + "이대로면 잘린 HTML 이 검증을 통과해서 그대로 저장됩니다 — "
                + "Jsoup 이 끊긴 태그를 자동으로 닫아주기 때문입니다.");
    }
}
