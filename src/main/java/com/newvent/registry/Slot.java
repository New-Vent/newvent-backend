package com.newvent.registry;

import java.util.Arrays;

/**
 * 슬롯 — 서버가 폼 값으로 채우는 자리. **블록이 아니다.**
 *
 *   블록(data-block)  문단 단위.  LLM 이 만들고, 채팅으로 고칠 수 있다
 *   슬롯(data-slot)   값 하나.    서버만 채우고, 채팅 대상이 아니다
 *
 * ★ 왜 블록으로 안 하는가
 *   템플릿 5종 전부 기간이 hero 안(또는 템플릿 고유 박스 안)에 들어가 있다.
 *   <section data-block="period"> 로 빼면 디자인이 망가진다.
 *   그리고 모델은 기간이라는 게 있는지 알 필요가 없다 — 프롬프트에 안 넣는다.
 *
 * ★ 이게 없으면 생기는 일
 *   기간 위치가 템플릿마다 제각각이다 (vp-period · fs-period-sub · 클래스 없음 …).
 *   공통 마커가 없으면 서버가 템플릿마다 다른 선택자를 알아야 한다.
 *
 * 템플릿 쪽 약속
 *   <span data-slot="period">2026.09.20 ~ 2026.10.05</span>
 *   <button class="cta-btn" data-slot="cta-link">응모하기</button>
 */
public enum Slot {

    /** 이벤트 기간 — 폼의 start_at ~ end_at */
    PERIOD("period", "이벤트 기간"),

    /** 참여 링크 — 폼의 참여 URL. <a> 면 href, <button> 이면 data-href */
    CTA_LINK("cta-link", "참여 링크");

    private final String key;
    private final String desc;

    Slot(String key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public String key()  { return key; }
    public String desc() { return desc; }

    public String selector() {
        return "[data-slot=\"" + key + "\"]";
    }

    public static Slot of(String key) {
        return Arrays.stream(values())
                .filter(s -> s.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("없는 슬롯: " + key));
    }
}
