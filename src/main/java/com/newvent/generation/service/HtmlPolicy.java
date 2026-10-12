package com.newvent.generation.service;

import java.util.ArrayList;
import java.util.List;

import com.newvent.registry.Block;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.BlockValidator.Failure;
import com.newvent.registry.ValueCheck;

/**
 * "모델 출력을 어떻게 다듬고, 무엇을 합격으로 볼 것인가" 한 벌.
 *
 * ★ RetryService 는 이것만 봅니다. registry 를 직접 import 하지 않습니다.
 *   순서(부르기 → 다듬기 → 판정 → 되먹임)는 RetryService 가 알고,
 *   규칙(무엇을 지우고 무엇을 합격으로 보는가)은 전부 여기로 모읍니다.
 *
 * ★ 왜 정화까지 밖에서 받는가
 *   생성과 수정의 정화 규칙이 **반대**이기 때문입니다.
 *     생성 — data-slot 을 지운다. 빈 문서에서 만드는 거라 있을 이유가 없다.
 *     수정 — data-slot 을 남긴다. 원본에 있던 걸 보존해야 한다.
 *   한쪽 규칙을 양쪽에 쓰면, 제목 한 번 고쳤을 뿐인데 그 이벤트는
 *   기간을 영원히 못 채우게 됩니다(merge 가 블록을 통째로 갈아끼우므로).
 */
public interface HtmlPolicy {

    String clean(String raw);

    List<Failure> validate(String html);


    /** 생성 — 요청문 · 제목을 모를 때(테스트). 선택 블록을 거르지 않고 대괄호는 전부 자리표시자로 본다 */
    static HtmlPolicy generation() {
        return generation(null, null);
    }

    /**
     * 생성 — 필수 블록이 다 있어야 하고, data-slot 은 지운다.
     *
     * @param requestText 요청문. 열쇠말이 없는 선택 블록(faq · compare · audience)은 출력에서 지운다
     * @param title       이벤트명. 요청문과 함께 "원래 있던 대괄호" 의 출처다 — [단독] 같은 것
     */
    static HtmlPolicy generation(String requestText, String title) {
        return generation(requestText, title, null);
    }

    /**
     * @param period 이벤트 기간 표기 ("2026.12.01 ~ 12.31"). 연도 검사가 허용하는 연도의 출처다 —
     *               요청문에 연도가 없어도 이벤트 기간의 연도는 쓸 수 있다
     */
    static HtmlPolicy generation(String requestText, String title, String period) {
        final String source = (title == null ? "" : title) + "\n" + (requestText == null ? "" : requestText)
                + "\n" + (period == null ? "" : period);
        final boolean known = requestText != null || title != null;
        return new HtmlPolicy() {
            @Override
            public String clean(String raw) {
                String html = BlockValidator.sanitizeGenerated(BlockValidator.extract(raw));
                return BlockValidator.dropUntriggered(html, requestText);
            }

            @Override
            public List<Failure> validate(String html) {
                return BlockValidator.validateGenerated(html, known ? source : null);
            }
        };
    }

    /**
     * 수정 — 요청한 블록 하나만 와야 하고, 원본의 data-slot 이 그대로여야 한다.
     *
     * @param target     수정 대상 블록
     * @param before     수정 전 그 블록의 HTML (서버가 갖고 있는 것)
     * @param userPrompt 사용자 요청 원문 — ValueCheck 가 여기 나온 숫자는 허용한다
     */
    static HtmlPolicy edit(Block target, String before, String userPrompt) {
        return edit(target, before, userPrompt, false);
    }

    /** @param allowOneMore 항목을 하나 더하는 요청인가 (BlockValidator.validateEdited 참고) */
    static HtmlPolicy edit(Block target, String before, String userPrompt, boolean allowOneMore) {
        // ★ 람다 캡처를 위해 effectively-final 지역 변수로 받는다
        final String beforeSnap = before;
        final String promptSnap = userPrompt;
        return new HtmlPolicy() {
            @Override
            public String clean(String raw) {
                return BlockValidator.sanitizeEdited(BlockValidator.extract(raw));
            }

            @Override
            public List<Failure> validate(String html) {
                List<Failure> f = new ArrayList<>(
                        BlockValidator.validateEdited(target, beforeSnap, html, allowOneMore));
                // REQ-LLM-41 — 없던 수치가 생겼나 (value_added → 실패, warning_value_removed → 경고)
                f.addAll(ValueCheck.diff(beforeSnap, html, promptSnap));
                return f;
            }
        };
    }
}
