package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.service.PublicEventService;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.domain.ParticipationUnavailableReason;
import com.newvent.participation.dto.response.ParticipationStatusResponse;
import com.newvent.participation.repository.EventGameConfigRepository;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.service.UserService;

class ParticipationStatusServiceTest {

    private final PublicEventService publicEventService = mock(PublicEventService.class);
    private final UserService userService = mock(UserService.class);
    private final EventParticipationRepository participationRepository =
            mock(EventParticipationRepository.class);
    private final EventGameConfigRepository eventGameConfigRepository =
            mock(EventGameConfigRepository.class);

    // 검증기는 실제 구현을 쓴다 — 설정 내용 검증이 상태 판정에 실제로 반영되는지 보려는 것이다
    private final ParticipationValidator validator = new ParticipationValidator();

    private final ParticipationStatusService service = new ParticipationStatusService(
            publicEventService, userService, participationRepository, eventGameConfigRepository,
            validator);

    /**
     * 설정이 하나 있는 상태가 기본이다.
     *
     * ★ 이걸 안 깔면 아래 "참여 가능" 테스트가 NOT_CONFIGURED 로 떨어진다.
     *   실제로 그게 이번에 고친 버그다 — 설정을 안 보던 때는 설정이 없어도 가능이었다.
     */
    @BeforeEach
    void 기본은_설정이하나() {
        List<EventGameConfig> one = List.of(config("BASIC"));
        when(eventGameConfigRepository.findAllByEventId(1L)).thenReturn(one);
    }

    @Test
    void 참여이력이없고_등급이충분하면_참여가능하다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.EXCELLENT));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.BEST));

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertFalse(result.participated());
        assertTrue(result.canParticipate());
        assertNull(result.unavailableReason());
        assertEquals("BASIC", result.gameCode());
    }

    @Test
    void 이미참여했다면_참여불가사유를반환한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.NORMAL));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.BEST));
        when(participationRepository.existsByEventIdAndUserId(1L, 7L))
                .thenReturn(true);

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertTrue(result.participated());
        assertFalse(result.canParticipate());
        assertEquals(
                ParticipationUnavailableReason.ALREADY_PARTICIPATED,
                result.unavailableReason()
        );
    }

    @Test
    void 등급이부족하면_참여불가사유를반환한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.EXCELLENT));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.NORMAL));

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertFalse(result.participated());
        assertFalse(result.canParticipate());
        assertEquals(
                ParticipationUnavailableReason.INSUFFICIENT_GRADE,
                result.unavailableReason()
        );
    }

    @Test
    void 이미참여했고_등급도부족하면_이미참여한사유를우선한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.BEST));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.NORMAL));
        when(participationRepository.existsByEventIdAndUserId(1L, 7L))
                .thenReturn(true);

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertTrue(result.participated());
        assertFalse(result.canParticipate());
        assertEquals(
                ParticipationUnavailableReason.ALREADY_PARTICIPATED,
                result.unavailableReason()
        );
    }

    @Test
    @DisplayName("참여 설정이 없으면 참여할 수 없다 — POST 가 409 로 거절하는 것과 같은 판정")
    void 설정이없으면_참여불가다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.NORMAL));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.BEST));
        when(eventGameConfigRepository.findAllByEventId(1L)).thenReturn(List.of());

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertFalse(result.canParticipate());
        assertEquals(ParticipationUnavailableReason.NOT_CONFIGURED, result.unavailableReason());
        assertNull(result.gameCode(), "설정이 없으면 방식도 알 수 없다");
    }

    @Test
    @DisplayName("참여 설정이 둘이면 없는 것과 같다 — 어느 방식으로 참여할지 정할 수 없다")
    void 설정이둘이면_참여불가다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.NORMAL));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.BEST));
        List<EventGameConfig> two = List.of(config("BASIC"), config("LUCKY_POUCH"));
        when(eventGameConfigRepository.findAllByEventId(1L)).thenReturn(two);

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertFalse(result.canParticipate());
        assertEquals(ParticipationUnavailableReason.NOT_CONFIGURED, result.unavailableReason());
        assertNull(result.gameCode());
    }

    @Test
    @DisplayName("등급이 부족하고 설정도 없으면 등급 사유를 먼저 말한다 — POST 의 검사 순서와 같다")
    void 등급과설정이_둘다걸리면_등급이우선이다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.BEST));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.NORMAL));
        when(eventGameConfigRepository.findAllByEventId(1L)).thenReturn(List.of());

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertEquals(ParticipationUnavailableReason.INSUFFICIENT_GRADE, result.unavailableReason());
    }

    @Test
    @DisplayName("참여 방식을 그대로 내려준다 — 화면이 어떤 입력을 받을지 이걸로 고른다")
    void 참여방식을_내려준다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.NORMAL));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.BEST));
        List<EventGameConfig> pouch = List.of(config("LUCKY_POUCH", Map.of(
                "pouches", List.of(
                        Map.of("pouchIndex", 1, "winProbability", 10, "prizeName", "상품권"),
                        Map.of("pouchIndex", 2, "winProbability", 100, "prizeName", "데이터")))));
        when(eventGameConfigRepository.findAllByEventId(1L)).thenReturn(pouch);

        assertEquals("LUCKY_POUCH", service.getStatus(1L, 7L).gameCode());
    }

    @Test
    @DisplayName("설정이 하나 있어도 모양이 틀리면 참여 불가다 — 단일성만 보면 여전히 거짓말한다")
    void 설정이있어도_모양이틀리면_참여불가다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.NORMAL));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.BEST));
        // pouches 는 있는데 칸에 prizeName 이 없다 — 참여 API 가 409 를 내는 설정
        List<EventGameConfig> broken = List.of(config("LUCKY_POUCH", Map.of(
                "pouches", List.of(Map.of("pouchIndex", 1, "winProbability", 10)))));
        when(eventGameConfigRepository.findAllByEventId(1L)).thenReturn(broken);

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertFalse(result.canParticipate());
        assertEquals(ParticipationUnavailableReason.NOT_CONFIGURED, result.unavailableReason());
        assertNull(result.gameCode(), "쓸 수 없는 설정이면 방식도 알려주지 않는다");
    }

    @Test
    @DisplayName("지원하지 않는 방식이면 참여 불가다")
    void 지원하지않는_방식이면_참여불가다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.NORMAL));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.BEST));
        List<EventGameConfig> unknown = List.of(config("ROULETTE", Map.of()));
        when(eventGameConfigRepository.findAllByEventId(1L)).thenReturn(unknown);

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertFalse(result.canParticipate());
        assertEquals(ParticipationUnavailableReason.NOT_CONFIGURED, result.unavailableReason());
    }

    @Test
    void 접근할수없는_이벤트는_기존예외를그대로반환한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenThrow(new EventNotAccessibleException());

        assertThrows(
                EventNotAccessibleException.class,
                () -> service.getStatus(1L, 7L)
        );
    }

    private Event event(MembershipGrade grade) {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "grade", grade);
        return event;
    }

    private User user(MembershipGrade grade) {
        return new User(
                "user07", "hash", "사용자", "user07@test.com",
                null, 50000, grade
        );
    }

    private EventGameConfig config(String code) {
        return config(code, Map.of());
    }

    /** 검증기가 실제로 getConfig() 를 읽으므로 isActive·config 까지 채운다. */
    private EventGameConfig config(String code, Map<String, Object> values) {
        Game game = mock(Game.class);
        when(game.getCode()).thenReturn(code);
        when(game.isActive()).thenReturn(true);
        EventGameConfig config = mock(EventGameConfig.class);
        when(config.getGame()).thenReturn(game);
        when(config.getConfig()).thenReturn(values);
        return config;
    }
}
