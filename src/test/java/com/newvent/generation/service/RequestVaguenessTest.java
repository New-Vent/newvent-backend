package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 내용이 없는 생성 요청 판정 (#230) */
class RequestVaguenessTest {

    @ParameterizedTest(name = "[{index}] 요청 \"{0}\"")
    @ValueSource(strings = {"이벤트", "이벤트 만들어줘", "이벤트 하나 만들어줘", "ㅋㅋㅋ 아무거나 재밌게", "그냥 알아서 해줘",
            "페이지 좀 만들어주세요", "  ", "...", "ㅎㅎ"})
    @DisplayName("제목도 '이벤트' 뿐이면 내용 없는 요청은 되묻는다")
    void 제목도_비면_되묻는다(String request) {
        assertTrue(RequestVagueness.isTooVague("이벤트", request), request);
        assertTrue(RequestVagueness.isTooVague(null, request), request);
    }

    @Test
    @DisplayName("제목이 있으면 요청이 '만들어줘' 뿐이어도 만든다 — 이벤트에는 보통 이름이 있다")
    void 제목이_있으면_만든다() {
        assertFalse(RequestVagueness.isTooVague("신규 가입 쿠폰 이벤트", "이벤트 만들어줘"));
        assertFalse(RequestVagueness.isTooVague("여름 데이터", "ㅋㅋㅋ 아무거나"));
        assertFalse(RequestVagueness.isTooVague("한가위 선물", null));
    }

    @ParameterizedTest(name = "[{index}] 요청 \"{0}\"")
    @ValueSource(strings = {"신규 가입 쿠폰", "여름 데이터", "월드컵 스코어 맞추기", "출석체크", "퀴즈",
            "Create a summer data coupon event page", "신규가입 쿠폰이벤트 맨들어줘 데이타 3기가",
            "혜택은 3가지, 참여 방법은 4단계로 하고 유의사항을 강조해서 만들어줘"})
    @DisplayName("짧아도 이벤트 내용이 있는 요청은 되묻지 않는다")
    void 내용이_있으면_되묻지_않는다(String request) {
        assertFalse(RequestVagueness.isTooVague("이벤트", request), request);
    }

    @Test
    @DisplayName("되묻기 문장에 예시가 있다 — 안 되는 것만 말하면 막힌다")
    void 되묻기_문장에_예시가_있다() {
        assertTrue(RequestVagueness.QUESTION.contains("예:"), RequestVagueness.QUESTION);
    }

    @Test
    @DisplayName("요청 판정기의 답에서 known 을 읽는다 — 코드 펜스 · 앞뒤 설명이 붙어도 읽고, 규격 밖이면 null")
    void 판정기_답을_읽는다() {
        assertEquals(Boolean.TRUE, RequestVagueness.parseKnown("{\"known\":true}"));
        assertEquals(Boolean.FALSE, RequestVagueness.parseKnown("```json\n{\"known\": false}\n```"));
        assertNull(RequestVagueness.parseKnown("잘 모르겠어요"));
        assertNull(RequestVagueness.parseKnown("{\"known\":\"maybe\"}"));
        assertNull(RequestVagueness.parseKnown(null));
    }

    @Test
    @DisplayName("글자도 숫자도 없는 입력은 모델 없이 걸러진다 — 판정 모델이 JSON 을 못 내는 입력")
    void 글자가_없는_입력() {
        for (String t : new String[]{"...", "!!!???", "   ", "😀😀😀", "ㅋㅋㅋ", "ㅅㅂ", null}) {
            assertTrue(RequestVagueness.hasNoLetters(t), String.valueOf(t));
        }
        for (String t : new String[]{"제목 바꿔", "더 화려하게", "12345", "응", "hello"}) {
            assertFalse(RequestVagueness.hasNoLetters(t), t);
        }
    }
}
