package com.newvent.registry;

import java.util.Arrays;
import java.util.Optional;

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
    PERIOD("period", "이벤트 기간", Kind.TEXT),

    /** 참여 링크 — 폼의 참여 URL. <a> 면 href, <button> 이면 data-href */
    CTA_LINK("cta-link", "참여 링크", Kind.LINK);

    /**
     * 슬롯이 태그의 **어디를** 쓰는가. 채우는 법도 비우는 법도 여기서 갈린다.
     *
     * ★ 이걸 구분 안 하면 CTA 버튼 문구가 날아간다.
     *   <button data-slot="cta-link">감사 쿠폰팩 3종 받기 🎁</button>
     *   저 문구는 슬롯 값이 아니라 **CTA 라벨**이다. 내용을 비우면 빈 버튼이 된다.
     */
    public enum Kind {
        /** 내용형 — 서버가 태그 안의 글자를 채운다. 비울 때 글자를 지운다 */
        TEXT,
        /** 링크형 — 서버가 href / data-href 를 채운다. 비울 때 **속성만** 지운다 */
        LINK
    }

    private final String key;
    private final String desc;
    private final Kind kind;

    Slot(String key, String desc, Kind kind) {
        this.key = key;
        this.desc = desc;
        this.kind = kind;
    }

    public String key()  { return key; }
    public String desc() { return desc; }
    public Kind kind()   { return kind; }

    public String selector() {
        return "[data-slot=\"" + key + "\"]";
    }

    /** 모르는 키면 던진다. 우리가 아는 슬롯을 쓸 때. */
    public static Slot of(String key) {
        return find(key).orElseThrow(() -> new IllegalArgumentException("없는 슬롯: " + key));
    }

    /**
     * 모르는 키면 빈 값. **문서에서 읽은 data-slot 을 볼 때 쓴다.**
     *
     * ★ 문서에는 우리가 모르는 data-slot 이 있을 수 있다 —
     *   모델이 지어냈거나, 템플릿 담당이 새 마커를 넣었거나.
     *   그때 예외로 터지면 안 되고, 손대지 않고 두면 된다.
     *   지어낸 것은 BlockValidator 의 checkPreserved 가 slot_invented 로 잡는다.
     */
    public static Optional<Slot> find(String key) {
        return Arrays.stream(values())
                .filter(s -> s.key.equals(key))
                .findFirst();
    }
}
