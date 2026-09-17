package com.newvent.registry;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 블록 레지스트리 — 이 파일 하나가 프롬프트 · 검증기 · CSS 선택자 · 라우터 목록을
 * 전부 만들어낸다. 같은 사실을 여러 파일에 적지 않기 위한 장치다.
 *
 * ★ 고칠 때는 shape 와 must 를 반드시 같이 고친다.
 *   shape 는 "모델에게 시키는 말", must 는 "그게 지켜졌는지 보는 선택자"다.
 */
public enum Block {

    HERO("hero", true, Source.LLM,
            "이벤트 제목과 한 줄 소개",
            "제목은 <h1>, 소개는 <p> 로 감싼다",
            "h1", 0),

    PERIOD("period", true, Source.SERVER,
            "이벤트 기간 — 폼 값을 서버가 삽입",
            null, null, 0),

    BENEFITS("benefits", true, Source.MIXED,
            "혜택 — 항목은 폼 값, 문장만 다듬는다",
            "<ul> 안에 <li> 로 항목을 나열한다. 2개 이상",
            "ul li", 2),

    STEPS("steps", false, Source.LLM,
            "참여 방법 2~4단계",
            "<ol> 안에 <li> 로 순서대로 나열한다",
            "ol li", 2),

    NOTICES("notices", true, Source.SERVER,
            "유의사항 — 승인된 문구만 서버가 삽입",
            null, null, 0),

    CTA("cta", true, Source.MIXED,
            "참여 버튼. 문구만 생성, 링크는 폼 값",
            "<a href=\"#\" class=\"btn\"> 안에 버튼 문구를 넣는다",
            "a", 0);

    /** 누가 내용을 만드는가. 프롬프트·클릭 가능 여부·덮어쓰기가 여기서 갈린다. */
    public enum Source {
        /** 모델이 전부 만든다 */
        LLM,
        /** 서버가 채운다. 모델은 만들면 안 된다 */
        SERVER,
        /** 값은 폼·서버, 문장만 모델 */
        MIXED
    }

    private final String key;
    private final boolean required;
    private final Source source;
    private final String desc;
    private final String shape;
    private final String must;
    private final int minItems;

    Block(String key, boolean required, Source source,
          String desc, String shape, String must, int minItems) {
        this.key = key;
        this.required = required;
        this.source = source;
        this.desc = desc;
        this.shape = shape;
        this.must = must;
        this.minItems = minItems;
    }

    public String key()      { return key; }
    public boolean required(){ return required; }
    public Source source()   { return source; }
    public String desc()     { return desc; }
    public String shape()    { return shape; }
    public String must()     { return must; }
    public int minItems()    { return minItems; }

    /** 모델이 만드는 블록 (SERVER 제외) */
    public static List<Block> llmBlocks() {
        return Arrays.stream(values())
                .filter(b -> b.source != Source.SERVER)
                .collect(Collectors.toList());
    }

    /** 서버가 채우는 블록 — 모델 출력을 무시하고 덮어쓴다 */
    public static List<Block> serverBlocks() {
        return Arrays.stream(values())
                .filter(b -> b.source == Source.SERVER)
                .collect(Collectors.toList());
    }

    /** preview 에서 클릭해 수정 대상으로 고를 수 있는 블록 */
    public static List<Block> clickable() {
        return llmBlocks();
    }

    public static Block of(String key) {
        return Arrays.stream(values())
                .filter(b -> b.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("없는 블록: " + key));
    }

    /** CSS 선택자 — event.css 와 검증기가 같은 문자열을 쓰게 한다 */
    public String selector() {
        return "[data-block=\"" + key + "\"]";
    }

    // ── 조작 허용


    /** 채팅으로 내용을 고칠 수 있나 */
    public boolean canEdit() {
        return source != Source.SERVER;
    }

    /** 통째로 지울 수 있나 — 필수 블록은 못 지운다 */
    public boolean canDelete() {
        return !required;
    }

    /** 없을 때 새로 만들 수 있나 */
    public boolean canCreate() {
        return source != Source.SERVER && !required;
    }

    /** 순서를 옮길 수 있나 — 서버 소유라도 위치는 옮겨도 된다 */
    public boolean canMove() {
        return true;
    }

    /** 거부 사유 — 사용자에게 그대로 보여줄 문장 */
    public String denyReason(String op) {
        if (source == Source.SERVER) {
            return switch (op) {
                case "EDIT", "ADD" ->
                        desc + " 영역은 시스템이 관리합니다. 채팅으로 바꿀 수 없습니다.";
                case "DELETE" ->
                        "유의사항·기간은 반드시 표시해야 해서 지울 수 없습니다.";
                default -> null;
            };
        }
        if (required && op.equals("DELETE")) {
            return key + " 영역은 필수라 지울 수 없습니다.";
        }
        return null;
    }

    /**
     * shape 를 시킨 블록은 must 도 있어야 한다.
     * 애플리케이션 시작 시 한 번 부른다. 어긋나면 그 자리에서 죽는 게 낫다.
     */
    public static void assertConsistent() {
        for (Block b : values()) {
            boolean hasShape = b.shape != null && !b.shape.isBlank();
            boolean hasMust  = b.must  != null && !b.must.isBlank();
            if (hasShape != hasMust) {
                throw new IllegalStateException(
                        b.key + ": shape 와 must 는 짝이어야 합니다 " +
                        "(shape=" + hasShape + ", must=" + hasMust + ")");
            }
            if (b.source == Source.SERVER && hasShape) {
                throw new IllegalStateException(
                        b.key + ": SERVER 블록에 shape 를 주면 안 됩니다. 모델이 만들게 됩니다.");
            }
            if (b.minItems > 0 && !hasMust) {
                throw new IllegalStateException(
                        b.key + ": minItems 를 세려면 must 가 필요합니다.");
            }
        }
    }
}
