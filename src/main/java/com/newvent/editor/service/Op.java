package com.newvent.editor.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * 라우터가 분류하는 동작.
 *
 * ★ 이름이 곧 프롬프트에 나가는 문자열이다.
 *   PromptBuilder.router() 가 values() 를 돌면서 목록을 만든다.
 *   여기에 없는 걸 프롬프트에 적거나, 프롬프트에 없는 걸 여기 넣으면 어긋난다.
 *
 * ★ ADD_ITEM 은 넣지 않는다
 *   v11 벤치마크(gemma-3-27b, 2026-09-23)에서 op 정확도가 **4/12** 였다.
 *   실패가 전부 op 에 몰려 있는 상태에서 op 을 하나 더 늘리면 나빠진다.
 */
public enum Op {

    /** 특정 영역의 내용을 고친다 */
    EDIT,

    /** 없는 영역을 새로 넣는다 — **영역 단위다. 항목 단위가 아니다** */
    ADD,

    /** 영역을 통째로 지운다 */
    DELETE,

    /** 색·크기·굵기 등 겉모양만 바꾼다 */
    STYLE;

    /**
     * 모르는 값이면 빈 값. **모델 출력을 볼 때 쓴다.**
     */
    public static Optional<Op> find(String raw) {
        if (raw == null) return Optional.empty();
        String k = raw.trim().toUpperCase();
        return Arrays.stream(values()).filter(o -> o.name().equals(k)).findFirst();
    }
}
