package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import com.newvent.event.domain.Event;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.repository.EventGameConfigRepository;

/** 종료일을 바꿀 때 지켜야 할 발표 시각을 참여 설정에서 꺼낸다 */
class ConfiguredAnnouncementTest {

    private static final String AT = "2026-08-01T12:00:00+09:00";

    private final EventGameConfigRepository configs = mock(EventGameConfigRepository.class);
    private final ConfiguredAnnouncement announcement =
            new ConfiguredAnnouncement(configs, new ParticipationValidator());

    @Test
    @DisplayName("결과를 나중에 추첨하는 기본형은 발표 시각을 돌려준다")
    void 지연_추첨_기본형은_발표_시각이_있다() {
        given("BASIC", Map.of("resultMode", "DELAYED", "announcementAt", AT, "winnerCount", 1, "prizeName", "커피"));

        assertEquals(Optional.of(OffsetDateTime.parse(AT)), announcement.mustFollowEnd(1L));
    }

    @Test
    @DisplayName("승부 예측형도 발표 시각을 돌려준다")
    void 승부_예측형은_발표_시각이_있다() {
        given("SPORTS_PREDICTION", Map.of("announcementAt", AT));

        assertEquals(Optional.of(OffsetDateTime.parse(AT)), announcement.mustFollowEnd(1L));
    }

    @Test
    @DisplayName("즉시 결과 기본형 · 복주머니는 종료일과 상관없다 — 빈 값")
    void 종료와_무관한_방식은_빈_값이다() {
        given("BASIC", Map.of("resultMode", "IMMEDIATE", "announcementAt", AT));
        assertTrue(announcement.mustFollowEnd(1L).isEmpty());

        given("LUCKY_POUCH", Map.of("announcementAt", AT));
        assertTrue(announcement.mustFollowEnd(1L).isEmpty());
    }

    @Test
    @DisplayName("설정이 없거나 둘 이상이거나 발표 시각을 못 읽으면 빈 값 — 비교할 수 없는 것으로 막지 않는다")
    void 비교할_수_없으면_빈_값이다() {
        when(configs.findAllByEventId(1L)).thenReturn(List.of());
        assertTrue(announcement.mustFollowEnd(1L).isEmpty());

        given("SPORTS_PREDICTION", Map.of("announcementAt", "내일 저녁"));
        assertTrue(announcement.mustFollowEnd(1L).isEmpty());
    }

    private void given(String gameCode, Map<String, Object> config) {
        Game game = mock(Game.class);
        when(game.getCode()).thenReturn(gameCode);
        EventGameConfig row = EventGameConfig.create(BeanUtils.instantiateClass(Event.class), game, config);
        when(configs.findAllByEventId(1L)).thenReturn(List.of(row));
    }
}
