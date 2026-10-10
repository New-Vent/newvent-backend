package com.newvent.editor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.editor.exception.RouterErrorCode;
import com.newvent.registry.Block;

/**
 * ★ LLM · DB · HTML 을 전혀 보지 않는다.
 *   RawRoute 하나를 넣고 Decision 하나를 받는다. Mock 이 필요 없다.
 */
public class GateTest {

	private final Gate gate = new Gate();

	// ----- 1 행 : 모르는 target -------

	@Test
	@DisplayName("알 수 없는 영역이면 거부")
	void 모르는_영역은_거부() {
		Decision d = gate.decide(new RawRoute("EDIT", "없는 영역", "내용"));

		Decision.Reject r = assertInstanceOf(Decision.Reject.class, d);
		assertEquals(RouterErrorCode.TARGET_NOT_FOUND, r.code());
	}

	@Test
	@DisplayName("영역이 아예 없으면 거부 - 파서가 빈 객체를 통과")
	void 영역이_없으면_거부() {
		Decision d = gate.decide(new RawRoute("EDIT", null ,"내용"));

		Decision.Reject r = assertInstanceOf(Decision.Reject.class, d);
		assertEquals(RouterErrorCode.TARGET_NOT_FOUND, r.code());
	}

	// ----- 2 행 : source == SERVER -------

	@Test
	@DisplayName("서버 소유 영역은 EDIT 이어도 거부")
	void 서버_영역은_편집도_거부() {
		Decision d = gate.decide(new RawRoute("EDIT", "notices", "내용"));

		Decision.Reject r = assertInstanceOf(Decision.Reject.class, d);
		assertTrue(r.message().contains("시스템"));
	}

	@Test
	@DisplayName("서버 소유 영역은 추가 요청도 거부")
	void 서버_영역은_추가도_거부() {
		assertInstanceOf(Decision.Reject.class,
				gate.decide(new RawRoute("ADD", "notices", "새 항목")));
	}

	@Test
	@DisplayName("서버 소유 영역은 삭제 요청도 거부")
	void 서버_영역은_삭제도_거부() {
		assertInstanceOf(Decision.Reject.class,
				gate.decide(new RawRoute("DELETE", "notices", null)));
	}

	@Test
	@DisplayName("서버 소유 영역은 스타일 요청도 거부 - denyReason 은 스타일을 놓침")
	void 서버_영역은_스타일도_거부() {
		Decision d = gate.decide(new RawRoute("STYLE", "notices", "파란색으로"));

		Decision.Reject r = assertInstanceOf(Decision.Reject.class, d);
		assertTrue(r.message().contains("시스템"));
	}

	// ----- 3 행 : 추가 + 생성 불가 -> 편집으로 교정 -------

	@Test
	@DisplayName("필수 영역에 추가를 시키면 편집으로 교정 - hero 는 canCreate 가 false")
	void 필수_영역_추가는_편집으로_교정() {
		Decision d = gate.decide(new RawRoute("ADD", "hero", "새 제목"));

		assertEquals(new Decision.Run(Block.HERO, Op.EDIT, "새 제목"), d);
	}

	// ★★ 전에는 "혜택 항목 추가는 거절한다" 였다. 바뀐 이유:
	//    막으려던 건 모델이 **지어낸** 혜택이지 관리자가 **적어 준** 혜택이 아니다.
	//    "혜택에 커피쿠폰 추가해" 는 관리자가 지급을 결정한 것이다.
	@Test
	@DisplayName("혜택 항목 추가는 내용을 적어 주면 ADD 로 통과한다")
	void 혜택_항목_추가는_내용이_있으면_통과() {
		assertEquals(new Decision.Run(Block.BENEFITS, Op.ADD, "커피쿠폰"),
				gate.decide(new RawRoute("ADD", "benefits", "커피쿠폰")));
	}

	// ★ op 이 ADD 로 남아야 한다. EDIT 으로 교정되면 "항목 하나 더" 라는 의도가
	//   사라져서 아래 단계(프롬프트 · 검증기)가 그걸 알 방법이 없다.
	@Test
	@DisplayName("혜택 항목 추가는 EDIT 으로 교정되지 않는다 - 의도가 op 에 남아야 한다")
	void 혜택_항목_추가는_교정되지_않는다() {
		Decision.Run r = assertInstanceOf(Decision.Run.class,
				gate.decide(new RawRoute("ADD", "benefits", "커피쿠폰")));

		assertEquals(Op.ADD, r.op());
	}


	@Test
	@DisplayName("항목이 폼 값이 아닌 선택 영역은 그대로 추가로 통과")
	void 폼값_아닌_영역_추가는_통과() {
		assertEquals(new Decision.Run(Block.STEPS, Op.ADD, "앱 설치"),
				gate.decide(new RawRoute("ADD", "steps", "앱 설치")));
	}

	// ----- 4 행 : 필수 영역 삭제 거부 -------

    @Test
    @DisplayName("필수가 아닌 영역 삭제는 통과한다 - steps 만 해당")
    void 선택_영역_삭제는_통과() {
        assertEquals(new Decision.Run(Block.STEPS, Op.DELETE, null),
                gate.decide(new RawRoute("DELETE", "steps", null)));
    }

    @Test
    @DisplayName("필수 영역 삭제는 거부한다 - benefits 도 필수")
    void 필수_영역_삭제는_혜택도_거부() {
        Decision d = gate.decide(new RawRoute("DELETE", "benefits", null));

        Decision.Reject r = assertInstanceOf(Decision.Reject.class, d);
        assertTrue(r.message().contains("필수"));
    }

	// ----- 5 행 : 항목 추가 + 내용 없음 -> 되묻기 -------

	@Test
	@DisplayName("항목 추가에 내용이 없으면 되묻는다 - 모델을 부르지 않는다.")
	void 항목_추가는_내용이_없으면_되묻기() {
		Decision d = gate.decide(new RawRoute("ADD", "steps", null));

		Decision.AskBack a = assertInstanceOf(Decision.AskBack.class, d);
		assertTrue(a.question().contains("어떤 내용을 추가할까요?"));
	}

	@Test
	@DisplayName("빈 문자열도 내용이 없는 것으로 본다.")
	void 항목_추가는_빈_문자열도_되묻기() {
		assertInstanceOf(Decision.AskBack.class,
				gate.decide(new RawRoute("ADD", "steps", "     ")));
	}

	@Test
	@DisplayName("항목 추가는 교정보다 먼저 - 필수 영역이 편집으로 바뀌면 빈 항목이 생김")
	void 항목_추가는_교정보다_먼저() {
		assertInstanceOf(Decision.AskBack.class,
				gate.decide(new RawRoute("ADD", "cta", null)));
	}

	// ★★ 전에는 "혜택 거절은 되묻기보다 먼저" 였다 — 5번이 무조건 거절했으니까.
	//    5번이 없어진 지금은 6번이 받아서 **되묻는다.** 거절보다 그쪽이 맞다 —
	//    무엇을 더할지는 관리자가 정할 일이고, 물어보면 정할 수 있다.
	@Test
	@DisplayName("내용 없는 혜택 항목 추가는 되묻는다 - 거절하고 끝내지 않는다")
	void 내용_없는_혜택_추가는_되묻는다() {
		Decision d = gate.decide(new RawRoute("ADD", "benefits", null));

		assertInstanceOf(Decision.AskBack.class, d);
	}

	// ----- 6 행 : 그 외 통과 -------

	@Test
	@DisplayName("일반 수정은 그대로 통과")
	void 일반_수정은_통과() {
		assertEquals(new Decision.Run(Block.BENEFITS, Op.EDIT, "와일드카드 추가"),
				gate.decide(new RawRoute("EDIT", "benefits", "와일드카드 추가")));
	}

	// ----- 경계 -------

	@Test
	@DisplayName("동작 이름은 소문자여도 정규화 - Op.find 가 대문자로 바뀜")
	void 동작은_소문자도_통과() {
		assertEquals(new Decision.Run(Block.BENEFITS, Op.EDIT, "내용"),
				gate.decide(new RawRoute("edit", "benefits", "내용")));
	}

	@Test
	@DisplayName("알 수 없는 동작은 거부")
	void 모르는_동작은_거부() {
		Decision d = gate.decide(new RawRoute("ADD_ITEM", "benefits", "항목"));

		Decision.Reject r = assertInstanceOf(Decision.Reject.class, d);
		assertEquals(RouterErrorCode.UNKNOWN_OP, r.code());
	}

	@Test
	@DisplayName("동작이 없으면 거부 - denyReason 의 switch 에서 죽지 않음")
	void 동작이_없으면_거부() {
		Decision d = gate.decide(new RawRoute(null, "notices", "내용"));

		Decision.Reject r = assertInstanceOf(Decision.Reject.class, d);
		assertEquals(RouterErrorCode.UNKNOWN_OP, r.code());
	}

	@Test
	@DisplayName("빈 요청도 거부")
	void 빈_요청은_거부() {
		assertInstanceOf(Decision.Reject.class,
				gate.decide(new RawRoute(null, null, null)));

		assertInstanceOf(Decision.Reject.class, gate.decide(null));
	}
}
