package com.newvent.infra.llm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

/**
 * 잘림 판정만 본다. **AWS 호출 없음 — CI 에서 돈다.**
 *
 * ★ 왜 이것만 떼어 테스트하나
 *   이 판정이 틀리면 조용히 깨진다. Jsoup 이 끊긴 태그를 자동으로 닫아버려서
 *   **검증까지 통과**하고 잘린 페이지가 게시된다. 예외가 안 난다.
 *   실제 모델로 확인하는 건 LlmSmokeTest ⑤ 가 하고, 여기서는 경계값을 0원으로 굳힌다.
 */
class BedrockClientTest {

    private static final int HTML = LlmClient.Mode.HTML.maxTokens;      // 1536
    private static final int ROUTER = LlmClient.Mode.ROUTER.maxTokens;  // 256

    @Test
    @DisplayName("stopReason 이 MAX_TOKENS 면 잘린 것이다")
    void 정상_경로() {
        assertTrue(BedrockClient.isTruncated(StopReason.MAX_TOKENS, 1536, HTML));
        assertTrue(BedrockClient.isTruncated(StopReason.MAX_TOKENS, 10, HTML),
                "토큰이 적어도 stopReason 이 우선이다");

        assertFalse(BedrockClient.isTruncated(StopReason.END_TURN, 800, HTML));
        assertFalse(BedrockClient.isTruncated(StopReason.STOP_SEQUENCE, 800, HTML));
    }

    @Test
    @DisplayName("★ HTML 호출이 ROUTER 상한 때문에 오탐되면 안 된다")
    void 모드별_상한() {
        // 실측 HTML 출력은 600~800 토큰이다. 여기가 잘림으로 찍히면
        // 거의 모든 생성이 재시도를 타고 비용이 몇 배가 된다.
        assertFalse(BedrockClient.isTruncated(StopReason.END_TURN, 700, HTML),
                "HTML 상한은 1536 인데 ROUTER 상한(256)에 걸렸습니다");
        assertFalse(BedrockClient.isTruncated(StopReason.END_TURN, 811, HTML),
                "benefits 블록 원본이 811 토큰이다 — 정상 출력이다");

        // 같은 토큰 수라도 ROUTER 호출이면 상한에 닿은 것이다
        assertTrue(BedrockClient.isTruncated(StopReason.END_TURN, 256, ROUTER));
    }

    @Test
    @DisplayName("2차 방어선 — stopReason 이 달라도 상한에 닿으면 의심한다")
    void 상한_도달() {
        // 캡 256 을 추론 토큰으로 다 쓰고 빈 응답을 낼 수 있음
        assertTrue(BedrockClient.isTruncated(StopReason.END_TURN, ROUTER, ROUTER));
        assertTrue(BedrockClient.isTruncated(null, HTML, HTML));

        // 한 토큰 모자라면 정상
        assertFalse(BedrockClient.isTruncated(StopReason.END_TURN, ROUTER - 1, ROUTER));
    }

    @Test
    @DisplayName("토큰 수를 못 받았으면 stopReason 만 믿는다")
    void 토큰_없음() {
        assertFalse(BedrockClient.isTruncated(StopReason.END_TURN, null, HTML));
        assertTrue(BedrockClient.isTruncated(StopReason.MAX_TOKENS, null, HTML));
    }

    @Test
    @DisplayName("★ 필터·가드레일은 잘림이 아니다 — 아직 갈 곳이 없다")
    void 필터는_잘림이_아니다() {
        // stopReason 은 end_turn · max_tokens · stop_sequence 말고도 온다.
        //   CONTENT_FILTERED · GUARDRAIL_INTERVENED
        // 잘림도 정상도 아닌데 FailureType 에 자리가 없다(STOPPED 가 비어 있다).
        // 지금은 "잘림 아님" 으로 흘러가 VALIDATION_FAIL 로 뭉개진다. -> 추후 변경해야 함
        assertFalse(BedrockClient.isTruncated(StopReason.CONTENT_FILTERED, 100, HTML));
        assertFalse(BedrockClient.isTruncated(StopReason.GUARDRAIL_INTERVENED, 100, HTML));
    }
}
