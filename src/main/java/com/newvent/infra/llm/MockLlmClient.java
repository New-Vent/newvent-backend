package com.newvent.infra.llm;

/**
 * 키도 Ollama 도 없이 도는 가짜 클라이언트. **기본값.**
 */
public class MockLlmClient implements LlmClient {

    private static final String OK = """
            <section data-block="hero">
              <h1>여름 데이터 대방출</h1>
              <p>이번 여름 데이터 걱정 없이 마음껏 즐기세요</p>
            </section>
            <section data-block="benefits">
              <ul>
                <li>데이터 3GB 즉시 지급</li>
                <li>월 요금 30% 할인</li>
                <li>제휴 카페 음료 쿠폰</li>
              </ul>
            </section>
            <section data-block="steps">
              <ol>
                <li>이벤트 페이지에서 요금제 선택</li>
                <li>온라인으로 가입 신청</li>
              </ol>
            </section>
            <section data-block="cta">
              <a href="#" class="btn">참여하기</a>
            </section>""";

    /** benefits 안이 맨 텍스트 — empty_benefits 로 걸립니다 */
    private static final String BROKEN = """
            <section data-block="hero">
              <h1>여름 데이터 대방출</h1>
            </section>
            <section data-block="benefits">
              데이터 3GB 지급, 요금 할인
            </section>
            <section data-block="cta">
              <a href="#" class="btn">참여하기</a>
            </section>""";

    private static final String BLOCK = """
            <section data-block="benefits">
              <ul>
                <li>데이터 3GB 즉시 지급</li>
                <li>월 요금 30% 할인</li>
              </ul>
            </section>""";

    private final long delayMs;

    public MockLlmClient()            { this(400); }
    public MockLlmClient(long delayMs){ this.delayMs = delayMs; }

    @Override
    public Response chat(Request r) {
        sleep();                                   // 로딩 UI 를 볼 수 있게 약간 지연

        String body;
        if (r.mode() == Mode.ROUTER) {
            body = """
                   {"op":"EDIT","target":"benefits"}""";
        } else if (r.user() != null && r.user().contains("FAIL")) {
            body = BROKEN;                         // 검증 실패 경로 확인용
        } else if (r.system() != null && r.system().contains("영역만 수정해서")) {
            body = BLOCK;                          // 블록 수정 요청
        } else {
            body = OK;
        }

        return new Response(
                body,
                estimateTokens(r.system()) + estimateTokens(r.user()),
                estimateTokens(body),
                delayMs,
                false);
    }

    @Override
    public String providerName() { return "mock"; }

    /** 한글은 대략 1.5자당 1토큰. 로그 모양을 보려는 용도지 정확한 값이 아닙니다 */
    private static int estimateTokens(String s) {
        return s == null ? 0 : (int) (s.length() / 1.5);
    }

    private void sleep() {
        if (delayMs <= 0) return;
        try { Thread.sleep(delayMs); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
