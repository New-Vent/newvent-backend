package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventTemplate;
import com.newvent.user.domain.MembershipGrade;

/**
 * {@code templateCode} 해석 결정표. **스프링도 DB 도 안 띄운다.**
 *
 * ★ 이 표를 테스트로 박아두는 이유
 *   null 과 "" 를 다르게 보는 규칙은 코드를 읽어야 알 수 있고, 틀렸을 때
 *   예외가 아니라 **조용히 다른 경로**로 간다. 주석만으로는 지켜지지 않는다.
 *   실제로 "AI로 만들기" 가 템플릿 페이지를 만들고 DONE 으로 끝난 사고가 있었다.
 *
 * ★ 여기서는 해석만 본다
 *   모순 조합을 실제로 400 으로 거부하는지는 GenerationServiceTest 가 본다.
 *   이 파일은 "어떤 입력이 어느 경로가 되는가" 만 책임진다.
 */
class GenerateCommandTest {

    private static final OffsetDateTime START =
            OffsetDateTime.of(2026, 9, 20, 0, 0, 0, 0, ZoneOffset.ofHours(9));
    private static final OffsetDateTime END =
            OffsetDateTime.of(2026, 10, 5, 0, 0, 0, 0, ZoneOffset.ofHours(9));

    /** 템플릿이 붙은 이벤트 */
    private static Event 템플릿이벤트() {
        return event(EventTemplate.seed("holiday_gift", "한가위", "설명", "<div></div>", true));
    }

    /** 템플릿이 없는 이벤트 */
    private static Event 맨이벤트() {
        return event(null);
    }

    private static Event event(EventTemplate template) {
        // ★ ownerAdmin 은 null 로 둔다. GenerateCommand 는 보지 않는다.
        return Event.createDraft(null, template, "한가위 이벤트", START, END, MembershipGrade.NORMAL);
    }

    // ── 템플릿이 붙은 이벤트 ─────────────────────────────────────

    @Test
    @DisplayName("① templateCode 생략 → 이벤트에 붙은 템플릿으로 폴백한다")
    void 생략하면_폴백한다() {
        GenerateCommand cmd = GenerateCommand.of(템플릿이벤트(), null, null);

        assertEquals("holiday_gift", cmd.templateCode());
        assertTrue(cmd.hasTemplate(), "폴백이 안 걸렸습니다. '같은 템플릿으로 다시 생성' 이 깨집니다.");
        assertTrue(cmd.templateFromEvent(),
                "코드를 이벤트에서 가져왔는데 그 사실이 안 남았습니다. "
                + "거부 로그가 '프론트가 안 보냈다' 와 '둘 다 보냈다' 를 구분하지 못합니다.");
    }

    @Test
    @DisplayName("② ★ templateCode:\"\" → 백지. 폴백하지 않는다")
    void 빈_문자열은_백지다() {
        GenerateCommand cmd = GenerateCommand.of(템플릿이벤트(), "", "데이터 3GB 주는 이벤트");

        assertNull(cmd.templateCode(),
                "\"\" 가 폴백을 막지 못했습니다. 템플릿이 붙은 이벤트에서는 "
                + "AI 생성을 고를 방법이 아예 없어집니다.");
        assertFalse(cmd.hasTemplate());
        assertFalse(cmd.templateFromEvent());
    }

    @Test
    @DisplayName("③ 공백만 있는 templateCode 도 백지다 — \"\" 와 같게 본다")
    void 공백_코드도_백지다() {
        assertFalse(GenerateCommand.of(템플릿이벤트(), "   ", "요청문").hasTemplate());
    }

    @Test
    @DisplayName("④ templateCode 를 명시하면 이벤트에 붙은 것보다 그게 이긴다")
    void 명시한_코드가_이긴다() {
        GenerateCommand cmd = GenerateCommand.of(템플릿이벤트(), "sports_cheer", null);

        assertEquals("sports_cheer", cmd.templateCode());
        assertFalse(cmd.templateFromEvent(), "요청이 준 코드인데 이벤트에서 왔다고 기록됐습니다.");
    }

    @Test
    @DisplayName("⑤ 앞뒤 공백은 떼고 쓴다 — 저장소 조회가 공백 때문에 빗나가면 안 된다")
    void 공백을_떼어낸다() {
        assertEquals("holiday_gift",
                GenerateCommand.of(템플릿이벤트(), "  holiday_gift  ", null).templateCode());
    }

    // ── 템플릿이 없는 이벤트 ─────────────────────────────────────

    @Test
    @DisplayName("⑥ 템플릿 없는 이벤트 + 생략 → 백지. 폴백할 것이 없다")
    void 템플릿이_없으면_백지다() {
        GenerateCommand cmd = GenerateCommand.of(맨이벤트(), null, "데이터 3GB 주는 이벤트");

        assertNull(cmd.templateCode());
        assertFalse(cmd.templateFromEvent(),
                "가져온 코드가 없는데 이벤트에서 왔다고 기록됐습니다.");
    }

    // ── 요청문이 버려지는 조합 ───────────────────────────────────

    @Test
    @DisplayName("⑦ ★ 템플릿 경로 + 요청문 → 요청문이 버려지는 조합으로 표시된다")
    void 요청문이_버려지는_조합을_알아낸다() {
        // 폴백으로 들어간 꼴 — 프론트가 templateCode 를 안 보낸 경우
        assertTrue(GenerateCommand.of(템플릿이벤트(), null, "데이터 3GB").discardsRequestText(),
                "이게 실제 사고였습니다. 'AI로 만들기' 가 템플릿 페이지를 만들고 DONE 으로 끝났습니다.");

        // 둘 다 명시한 꼴
        assertTrue(GenerateCommand.of(템플릿이벤트(), "sports_cheer", "데이터 3GB").discardsRequestText());
    }

    @Test
    @DisplayName("⑧ 정상 조합은 버려지는 조합이 아니다")
    void 정상_조합은_통과한다() {
        // 템플릿만
        assertFalse(GenerateCommand.of(템플릿이벤트(), "holiday_gift", null).discardsRequestText());
        // 백지 + 요청문
        assertFalse(GenerateCommand.of(템플릿이벤트(), "", "데이터 3GB").discardsRequestText());
        // 템플릿 + 공백만 있는 요청문 — RequestFilter 와 같은 기준이어야 한다
        assertFalse(GenerateCommand.of(템플릿이벤트(), "holiday_gift", "   \n ").discardsRequestText());
    }

    // ── 보여주기용 ───────────────────────────────────────────────

    @Test
    @DisplayName("⑨ forRender 는 템플릿이 붙은 이벤트에서도 폴백하지 않는다")
    void 보여주기는_폴백하지_않는다() {
        // ★ 미리보기는 저장된 HTML 을 그대로 쓴다. 여기서 폴백이 걸리면
        //   discardsRequestText 판정이 흔들리고, 의미 없는 코드가 로그에 섞인다.
        GenerateCommand cmd = GenerateCommand.forRender(템플릿이벤트());

        assertNull(cmd.templateCode());
        assertFalse(cmd.templateFromEvent());
        assertFalse(cmd.hasRequestText());
    }

    @Test
    @DisplayName("⑩ 기간은 이벤트에서 만들어진다")
    void 기간을_채운다() {
        assertEquals("2026.09.20 ~ 10.05", GenerateCommand.of(템플릿이벤트(), null, null).period());
    }
}
