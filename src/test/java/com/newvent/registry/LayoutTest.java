package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LayoutTest {

    @Test
    @DisplayName("★ PR #216 본문의 '고치기 전 / 지금' 표 — 영역을 두고 한 말은 페이지 배치가 아니다")
    void 고치기_전에_틀리게_읽던_요청문() {
        // 고치기 전: WIDE · SPLIT · STACK 으로 읽혀 페이지 전체가 바뀌거나 "이미 그 배치입니다" 로 실패했다
        for (String region : List.of(
                "혜택을 2열로 배치해줘",
                "혜택 카드를 좌우로 나눠줘",
                "FAQ 레이아웃을 기본으로")) {
            assertEquals(Optional.empty(), Layout.requestedBy(region), region);
        }
        // 고치기 전: "기본" 때문에 STACK 으로 읽혀 벤토가 사라졌다
        assertEquals(Optional.of(Layout.BENTO), Layout.requestedBy("벤토 배치를 기본 색으로 해줘"));
    }

    @Test
    @DisplayName("★ PR #216 본문의 둘째 표 — 배치와 다른 요청이 섞이면 배치는 서버가, 나머지는 모델이 맡는다")
    void 배치와_다른_요청이_섞인_요청문() {
        // 색감은 hero 의 일이다 — 배치만 걸고 끝내면 색감이 버려진다
        String color = "전체 색감을 가을로 바꾸고 페이지를 벤토 배치로";
        assertEquals(Optional.of(Layout.BENTO), Layout.requestedBy(color));
        assertTrue(Layout.asksMoreOfHero(color), color);
        assertEquals("전체 색감을 가을로 바꾸고", Layout.withoutLayoutTalk(color));

        // 제목도 hero 의 일이다 — 전에는 "같은 부분에 대한 요청이 두 개입니다" 로 거절됐다
        String title = "제목을 더 짧게 바꾸고 매거진 레이아웃으로";
        assertEquals(Optional.of(Layout.MAGAZINE), Layout.requestedBy(title));
        assertTrue(Layout.asksMoreOfHero(title), title);
        assertEquals("제목을 더 짧게 바꾸고", Layout.withoutLayoutTalk(title));

        // 혜택은 혜택 연산의 몫이다 — hero 는 배치만 걸면 되므로 모델을 부르지 않는다
        String benefits = "혜택 문구를 다듬고 페이지를 벤토 배치로";
        assertEquals(Optional.of(Layout.BENTO), Layout.requestedBy(benefits));
        assertFalse(Layout.asksMoreOfHero(benefits), benefits);
    }


    @Test
    @DisplayName("★ 요청문이 페이지 배치를 직접 말했는지 서버가 읽는다 — 영역 변형 요청은 걸리지 않는다")
    void 배치_열쇠말() {
        Object[][] hits = {
                {"페이지 전체를 벤토 그리드 배치로 바꿔줘", Layout.BENTO},
                {"벤토 그리드 배치의 봄 이벤트 페이지", Layout.BENTO},
                {"벤토 레이아웃으로 만들어줘", Layout.BENTO},
                {"페이지를 벤토 배치로 바꿔줘", Layout.BENTO},
                {"제목은 왼쪽에 두고 나머지는 오른쪽에 오게 좌우로 나눠줘", Layout.SPLIT},
                {"좌우 분할 화면으로 해줘", Layout.SPLIT},
                {"영역들을 2열로 넓게 배치해줘", Layout.WIDE},
                {"페이지를 두 열로 보여줘", Layout.WIDE},
                {"페이지 배치를 원래대로 세로 한 줄로 바꿔줘", Layout.STACK},
                {"기본 배치로 돌려줘", Layout.STACK},
        };
        for (Object[] h : hits) {
            assertEquals(Optional.of(h[1]), Layout.requestedBy((String) h[0]), (String) h[0]);
        }
        // 영역 변형 · 다른 요청 — 페이지 배치가 아니다
        for (String miss : List.of(
                "혜택을 벤토 그리드로 바꿔줘", "벤토 그리드로 바꿔줘", "bento 스타일로",
                "혜택을 2열 카드로 보여줘", "참여 방법을 2단계로 줄여줘", "제목을 왼쪽 정렬로 바꿔줘",
                "전체 색감을 초록색으로 바꿔줘", "혜택을 세로 목록으로 바꿔줘", "")) {
            assertTrue(Layout.requestedBy(miss).isEmpty(), "페이지 배치로 읽혔습니다: " + miss + " → " + Layout.requestedBy(miss));
        }
        assertTrue(Layout.requestedBy(null).isEmpty());
    }

    @Test
    @DisplayName("★ 영역을 두고 한 '배치 · 레이아웃' 은 페이지 배치가 아니다 — 걸리면 페이지 전체가 바뀌거나 요청이 통째로 실패한다")
    void 영역을_가리킨_배치_요청은_페이지_배치가_아니다() {
        // 실측 오탐 — 전부 영역 하나의 모양 요청이다
        for (String miss : List.of(
                "혜택을 2열로 배치해줘",
                "참여 방법을 두 단으로 배치해줘",
                "혜택 영역을 벤토 배치로 바꿔줘",
                "혜택 카드를 좌우로 나눠줘",
                "FAQ 레이아웃을 기본으로",
                "혜택을 넓게 2열로 배치해줘",
                "경품 목록을 벤토 레이아웃으로 바꿔줘",
                "자주 묻는 질문 배치를 원래대로 돌려줘",
                "페이지의 혜택을 2열로 보여줘")) {
            assertTrue(Layout.requestedBy(miss).isEmpty(), "페이지 배치로 읽혔습니다: " + miss + " → " + Layout.requestedBy(miss));
        }
        // 영역 이름이 같이 나와도 목적어가 아니면 페이지 배치다 — 생성 요청문이 이렇게 온다
        Object[][] hits = {
                {"혜택을 3개 넣고 페이지는 벤토 배치로 해줘", Layout.BENTO},
                {"쿠폰 이벤트 벤토 배치로 만들어줘", Layout.BENTO},
                {"봄맞이 이벤트, 혜택은 데이터 10GB, 벤토 그리드 배치로", Layout.BENTO},
                {"경품 추첨 이벤트를 좌우 분할 화면으로 만들어줘", Layout.SPLIT},
                {"혜택 문구를 다듬고 영역들을 2열로 배치해줘", Layout.WIDE},
                // "기본 색" 은 배치를 되돌리라는 말이 아니다 — STACK 에 먹히면 벤토가 사라진다
                {"벤토 배치를 기본 색으로 해줘", Layout.BENTO},
        };
        for (Object[] h : hits) {
            assertEquals(Optional.of(h[1]), Layout.requestedBy((String) h[0]), (String) h[0]);
        }
    }

    @Test
    @DisplayName("★ 배치 말고 hero 가 할 일이 더 있는지 읽는다 — 있으면 모델을 불러야 나머지 요청이 안 버려진다")
    void 배치_말고_시킨_것() {
        // 배치만 말했다 — 모델을 부를 이유가 없다
        for (String only : List.of(
                "페이지를 벤토 배치로 바꿔줘",
                "페이지 전체를 벤토 그리드 배치로 바꿔줘",
                "영역들을 2열로 넓게 배치해줘",
                "페이지를 두 열로 보여줘",
                "좌우 분할 화면으로 해줘",
                "제목은 왼쪽에 두고 나머지는 오른쪽에 오게 좌우로 나눠줘",
                "기본 배치로 돌려줘",
                "페이지 배치를 원래대로 세로 한 줄로 바꿔줘",
                // 다른 영역 얘기는 그 영역 연산의 몫이다 — hero 를 고칠 건 없다
                "혜택 문구를 다듬고 페이지를 벤토 배치로 바꿔줘",
                "페이지를 벤토 배치로 바꾸고 참여 방법을 3단계로 줄여줘",
                // 열쇠말이 앞쪽에서 걸려 꼬리말이 남는 꼴 — 모델을 한 번 더 불렀다 (Bedrock 실측)
                "페이지 전체를 bento layout 으로 바꿔줘",
                "페이지를 잡지 느낌으로 바꿔줘")) {
            assertFalse(Layout.asksMoreOfHero(only), "배치만 말했는데 더 있다고 읽었습니다: " + only);
        }
        // 색감 · 제목 문구는 hero 의 연산이다 — 배치만 걸고 끝내면 조용히 버려진다
        for (String more : List.of(
                "전체 색감을 가을로 바꾸고 페이지를 벤토 배치로 바꿔줘",
                "제목을 더 짧게 하고 페이지를 벤토 배치로",
                "페이지를 벤토 배치로 바꾸고 분위기를 차분하게 해줘",
                "벤토 배치를 기본 색으로 해줘",
                "좌우 분할 화면으로 바꾸고 파란색으로 해줘")) {
            assertTrue(Layout.asksMoreOfHero(more), "배치 말고 시킨 게 있는데 못 읽었습니다: " + more);
        }
        assertFalse(Layout.asksMoreOfHero(null));
        assertFalse(Layout.asksMoreOfHero(""));
    }

    @Test
    @DisplayName("★ 모델에게는 배치를 말한 마디를 뺀 요청문을 준다 — 영역 얘기와 다 빠지는 경우는 그대로")
    void 배치를_말한_마디를_뺀다() {
        assertEquals("전체 색감을 가을 느낌으로 바꾸고",
                Layout.withoutLayoutTalk("전체 색감을 가을 느낌으로 바꾸고 매거진 레이아웃으로 바꿔줘"));
        assertEquals("전체 색감은 여름 느낌으로 해줘",
                Layout.withoutLayoutTalk("페이지를 3열로 배치하고 전체 색감은 여름 느낌으로 해줘"));
        assertEquals("제목을 더 짧게 하고",
                Layout.withoutLayoutTalk("제목을 더 짧게 하고 페이지를 벤토 배치로"));
        // 영역을 두고 한 말은 배치가 아니다 — 모델이 읽어야 한다
        assertEquals("혜택을 2열로 배치해줘", Layout.withoutLayoutTalk("혜택을 2열로 배치해줘"));
        // 다 빠지면 원문 — 빈 요청을 줄 수는 없다
        assertEquals("페이지를 벤토 배치로 바꿔줘", Layout.withoutLayoutTalk("페이지를 벤토 배치로 바꿔줘"));
        assertNull(Layout.withoutLayoutTalk(null));
    }
}
