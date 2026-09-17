package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newvent.infra.llm.LlmClient;
import com.newvent.infra.llm.OllamaClient;

/**
 * 실제 Ollama 로 한 바퀴 돌려본다. **CI 에서는 돌지 않는다.**
 *
 *   OLLAMA=1 ./gradlew test --rerun          실제 모델로
 *   ./gradlew test                            CI 와 동일 — 여기는 skip
 *   OLLAMA=1 OLLAMA_MODEL=exaone3.5:7.8b ./gradlew test --rerun
 *
 * ★ 왜 CI 에 넣지 않는가
 *   깃허브 러너에는 GPU 도 Ollama 도 없다. 그리고 있다 해도 넣으면 안 된다 —
 *   벤치마크에서 회차 간 편차가 16.7pp 였다. 이유 없이 빨간불이 나는 테스트는
 *   사람들이 무시하기 시작하고, 그때부터 진짜 실패도 같이 묻힌다.
 *
 * ★ 무엇을 주장하는가
 *   "프롬프트 문자열이 이거다" 가 아니라 "결과가 검증을 통과한다" 이다.
 *   그래서 PromptBuilder 가 바뀌어도 이 테스트는 그대로 유효하다.
 *   깨진다면 그건 프롬프트를 바꿨더니 모델이 못 따르게 됐다는 뜻이고,
 *   그게 정확히 알고 싶은 것이다.
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA", matches = "1")
class OllamaSmokeTest {

    private static final String BASE  = env("OLLAMA_URL",   "http://localhost:11434");
    private static final String MODEL = env("OLLAMA_MODEL", "qwen2.5:7b");

    private static String env(String k, String fallback) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /** ★ 이 생성자가 OllamaClient 가 지켜야 할 계약입니다 */
    private LlmClient client() {
        return new OllamaClient(BASE, MODEL, 180);
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
                "출력이 num_predict 에 걸려 잘렸습니다. done_reason 매핑이나 상한을 보세요.");
        assertTrue(r.outputTokens() > 0, "출력 토큰이 0 입니다. 응답 파싱이 잘못됐습니다.");

        String html = BlockValidator.extract(r.content());
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

        LlmClient.Response r = client().chat(LlmClient.Request.html(
                PromptBuilder.edit(Block.CTA),
                "현재 영역:\n" + 현재 + "\n\n요청: 버튼 문구를 '지금 신청하기' 로 바꿔줘"));
        기록("수정", r);

        assertFalse(r.truncated(), "수정 출력이 잘렸습니다.");

        String out = BlockValidator.extract(r.content());
        List<BlockValidator.Failure> fails = BlockValidator.validateEdited(Block.CTA, out);
        assertTrue(fails.isEmpty(),
                "수정 결과가 검증에 걸렸습니다: " + fails + "\n─── 출력 ───\n" + out);

        String 병합 = BlockValidator.merge(현재, Block.CTA, out);
        assertTrue(병합.contains("여름 데이터 대방출"), "건드리면 안 되는 hero 가 사라졌습니다.");
        assertTrue(병합.contains("데이터 3GB"),        "건드리면 안 되는 benefits 가 사라졌습니다.");
        assertTrue(병합.contains("href=\"#\""),        "href 가 바뀌었습니다. 실제 주소를 만들어 넣었습니다.");
    }

    @Test
    @DisplayName("③ 라우터 — JSON 한 줄이 파싱되고 값이 유효하다")
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
        assertTrue(s >= 0 && e > s, "JSON 을 못 찾았습니다. 원문:\n" + raw);

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
}
