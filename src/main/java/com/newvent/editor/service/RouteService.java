package com.newvent.editor.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.newvent.infra.llm.LlmClient;
import com.newvent.registry.PromptBuilder;

/**
 * 관리자 요청문을 연산 목록으로 바꾼다. **모델을 한 번 부른다.**
 */
@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private final LlmClient llm;

    public RouteService(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * 못 알아들으면 빈 값. 호출부는 그걸 보고 관리자에게 다시 말해달라고 한다.
     *
     * ★ LlmCallException 은 잡지 않는다.
     */
    public Optional<List<RawRoute>> route(String requestText) {
        if (requestText == null || requestText.isBlank()) return Optional.empty();

        LlmClient.Response res = llm.chat(
                LlmClient.Request.router(PromptBuilder.router(), requestText.strip()));

        // ★ 잘린 출력은 파싱 전에 버린다.
        if (res.truncated()) {
            log.warn("라우터 출력이 잘렸습니다. 요청이 너무 복잡했을 수 있습니다.");
            return Optional.empty();
        }

        Optional<List<RawRoute>> ops = RouteParser.parse(res.content());
        if (ops.isEmpty()) {
            // ★ 원문을 로그에 남긴다.
            log.info("라우터 출력을 읽지 못했습니다: {}", brief(res.content()));
        }
        return ops;
    }

    private static String brief(String s) {
        if (s == null) return "(없음)";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
