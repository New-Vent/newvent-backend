package com.newvent.registry;

import java.util.HashSet;
import java.util.Set;

/**
 * 검증 실패 코드 레지스트리 — 어떤 실패가 있고, 각각을 어떻게 다루는지 이 파일 하나가 정한다.
 *
 * ★ Block 과 같은 이유로 만들었다
 *   예전에는 "lost_" + key, "slot_lost_" + k 처럼 코드 문자열을 15곳에서 손으로 조립했다.
 *   어떤 코드가 있는지 한눈에 볼 곳이 없었고, 오타가 나도 컴파일이 통과했다.
 *   경고 판정도 code.startsWith("warning_") 에 기대고 있었다.
 *
 * ★ 분류 · 심각도 · 대응은 여기서만 정한다
 *   RetryService 는 Failure 가 막는 실패인지(isBlocking)만 본다.
 *   "무엇을 재시도할지" 를 RetryService 에 쓰지 말 것. 규칙이 두 군데가 되는 순간 어긋난다.
 *
 * ★ key 는 로그에 남는 문자열이다
 *   llm_call_logs.fail_codes 와 테스트가 이 문자열을 본다. 바꾸면 과거 로그와 집계가 끊긴다.
 *   VALUE_REMOVED 의 key 가 "warning_value_removed" 인 것도 기존 로그와 맞추기 위해서다.
 *
 * ★ 코드를 추가할 때
 *   심각도와 대응을 짝이 맞게 준다 (assertConsistent 가 기동 시점에 본다).
 *   실패 유형 문서의 표도 같이 고친다 — FailureCodeTest 가 표를 출력해 준다.
 */
public enum FailureCode {

    // ── 형식 — 출력이 HTML 조각 형식을 못 지켰다 ─────────────────────
    NO_HTML("no_html", Category.FORMAT, Severity.HARD, Action.RETRY),

    /** done_reason == length. Jsoup 이 끊긴 태그를 닫아 버려서 검증만으로는 못 잡는다 */
    TRUNCATED("truncated", Category.FORMAT, Severity.HARD, Action.RETRY),

    /** 라우터 출력이 JSON 규격이 아니다. 재시도하지 않고 관리자에게 다시 말해달라고 한다 */
    ROUTER_PARSE("router_parse", Category.FORMAT, Severity.HARD, Action.ASK_ADMIN),

    // ── 구조 — 블록이 없거나, 모양이 틀렸거나, 남의 블록을 만들었다 ──────
    NO_SECTION("no_section", Category.STRUCTURE, Severity.HARD, Action.RETRY),
    NO_DATA_BLOCK("no_data_block", Category.STRUCTURE, Severity.HARD, Action.RETRY),
    LOST_BLOCK("lost", Category.STRUCTURE, Severity.HARD, Action.RETRY),
    EMPTY_BLOCK("empty", Category.STRUCTURE, Severity.HARD, Action.RETRY),
    FEW_ITEMS("few", Category.STRUCTURE, Severity.HARD, Action.RETRY),
    EXTRA_BLOCK("extra", Category.STRUCTURE, Severity.HARD, Action.RETRY),
    ITEM_ADDED("item_added", Category.STRUCTURE, Severity.HARD, Action.RETRY),

    // ── 보존 — 수정에서 원본의 이름표를 지우거나 지어냈다 ─────────────
    SLOT_LOST("slot_lost", Category.PRESERVATION, Severity.HARD, Action.RETRY),
    SLOT_INVENTED("slot_invented", Category.PRESERVATION, Severity.HARD, Action.RETRY),
    ID_LOST("id_lost", Category.PRESERVATION, Severity.HARD, Action.RETRY),
    ID_INVENTED("id_invented", Category.PRESERVATION, Severity.HARD, Action.RETRY),
    CLASS_CHANGED("class_changed", Category.PRESERVATION, Severity.HARD, Action.RETRY),
    BEHAVIOR_LOST("behavior_lost", Category.PRESERVATION, Severity.HARD, Action.RETRY),
    BEHAVIOR_INVENTED("behavior_invented", Category.PRESERVATION, Severity.HARD, Action.RETRY),
    BUTTON_INVENTED("button_invented", Category.PRESERVATION, Severity.HARD, Action.RETRY),

    // ── 날조 — 주어지지 않은 것을 만들었다 ──────────────────────────
    PLACEHOLDER("placeholder", Category.FABRICATION, Severity.HARD, Action.RETRY),

    /** REQ-LLM-41 — 원본에도 요청문에도 없던 수치가 생겼다 */
    VALUE_ADDED("value_added", Category.FABRICATION, Severity.HARD, Action.RETRY),

    /** REQ-LLM-41 — 있던 수치가 빠졌다. "짧게 요약해줘" 의 정상 결과일 수 있어 실패로 보지 않는다 */
    VALUE_REMOVED("warning_value_removed", Category.FABRICATION, Severity.WARNING, Action.NOTIFY),

    // ── 권한 — 서버 소유 영역을 모델이 만들었다 ──────────────────────
    WROTE_SERVER_BLOCK("wrote", Category.PERMISSION, Severity.HARD, Action.RETRY);

    /** 무엇이 잘못됐나. 대응 방법이 분류마다 다르다 (형식은 서버가 복구할 수 있고, 날조는 되묻는 게 맞을 수 있다) */
    public enum Category { FORMAT, STRUCTURE, PRESERVATION, FABRICATION, PERMISSION, SECURITY }

    /**
     * 결과를 쓸 수 있나.
     *   HARD    못 쓴다 — 결과가 실패가 된다
     *   WARNING 쓸 수 있지만 사람이 확인해야 한다
     *   SOFT    서버가 이미 고쳤다 — 통계용
     */
    public enum Severity { HARD, WARNING, SOFT }

    /**
     * 그래서 무엇을 하나.
     *   RETRY     실패 메시지를 붙여 모델을 다시 부른다
     *   ASK_ADMIN 모델을 다시 불러도 지어낼 뿐이다 — 관리자에게 되묻는다
     *   NOTIFY    통과시키고 관리자에게 알린다
     *   RECOVERED 서버가 고쳤으니 기록만 한다
     */
    public enum Action { RETRY, ASK_ADMIN, NOTIFY, RECOVERED }

    private final String key;
    private final Category category;
    private final Severity severity;
    private final Action action;

    FailureCode(String key, Category category, Severity severity, Action action) {
        this.key = key;
        this.category = category;
        this.severity = severity;
        this.action = action;
    }

    public String key()          { return key; }
    public Category category()   { return category; }
    public Severity severity()   { return severity; }
    public Action action()       { return action; }

    /**
     * 심각도와 대응이 짝이 맞는가, key 가 겹치지 않는가.
     * 애플리케이션 시작 시 한 번 부른다(RegistryConfig). 어긋나면 그 자리에서 죽는 게 낫다.
     *
     *   SOFT    ↔ RECOVERED           서버가 고친 것을 재시도하면 모델이 멀쩡한 걸 고친다
     *   WARNING ↔ NOTIFY              통과시키면서 재시도할 수는 없다
     *   HARD    ↔ RETRY | ASK_ADMIN   막았으면 다음 행동이 있어야 한다
     */
    public static void assertConsistent() {
        Set<String> keys = new HashSet<>();
        for (FailureCode c : values()) {
            if (!keys.add(c.key)) {
                throw new IllegalStateException(c + ": key \"" + c.key + "\" 가 다른 코드와 겹칩니다.");
            }
            if (!pairs(c.severity, c.action)) {
                throw new IllegalStateException(
                        c + ": 심각도와 대응이 맞지 않습니다 (severity=" + c.severity + ", action=" + c.action + ")");
            }
        }
    }

    static boolean pairs(Severity severity, Action action) {
        return switch (severity) {
            case SOFT    -> action == Action.RECOVERED;
            case WARNING -> action == Action.NOTIFY;
            case HARD    -> action == Action.RETRY || action == Action.ASK_ADMIN;
        };
    }
}
