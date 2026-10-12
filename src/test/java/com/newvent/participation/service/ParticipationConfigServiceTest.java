package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.AccessDeniedException;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.dto.response.ParticipationConfigResponse;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventGameConfigRepository;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.participation.repository.GameRepository;

class ParticipationConfigServiceTest {

    private static final AuthUser ADMIN = AuthUser.admin(3L);
    private static final Map<String, Object> BASIC_CONFIG =
            Map.of("resultMode", "IMMEDIATE", "winProbability", 30, "prizeName", "데이터 5GB");

    private final EventOwnerCheck ownerCheck = mock(EventOwnerCheck.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final EventGameConfigRepository configRepository = mock(EventGameConfigRepository.class);
    private final EventParticipationRepository participationRepository =
            mock(EventParticipationRepository.class);

    // 검증기는 실제 구현을 쓴다 — 설정 규칙이 서비스를 지나 실제로 걸리는지 보려는 것이다
    private final ParticipationValidator validator = new ParticipationValidator();

    private final EventRepository eventRepository = mock(EventRepository.class);

    private final ParticipationConfigService service = new ParticipationConfigService(
            ownerCheck, eventRepository, gameRepository, configRepository,
            participationRepository, validator);

    @Test
    @DisplayName("설정이 없으면 새로 만든다")
    void 없으면_생성한다() {
        Event event = event();
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event);
        lockEvent();
        Optional<Game> basic = Optional.of(game("BASIC"));
        when(gameRepository.findByCode("BASIC")).thenReturn(basic);
        when(configRepository.findAllByEventId(1L)).thenReturn(List.of());
        when(configRepository.save(any(EventGameConfig.class)))
                .thenAnswer(call -> call.getArgument(0));

        ParticipationConfigResponse result =
                service.save(1L, ADMIN, "BASIC", BASIC_CONFIG);

        assertEquals("BASIC", result.gameCode());
        assertEquals(BASIC_CONFIG, result.config());
        verify(configRepository).save(any(EventGameConfig.class));
    }

    @Test
    @DisplayName("설정이 있으면 행을 새로 만들지 않고 고친다 — 둘이 되면 참여 API 가 거부한다")
    void 있으면_덮어쓴다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        lockEvent();
        Optional<Game> basic = Optional.of(game("BASIC"));
        when(gameRepository.findByCode("BASIC")).thenReturn(basic);

        EventGameConfig existing = EventGameConfig.create(event(), game("LUCKY_POUCH"), Map.of());
        List<EventGameConfig> one = List.of(existing);
        when(configRepository.findAllByEventId(1L)).thenReturn(one);

        ParticipationConfigResponse result = service.save(1L, ADMIN, "BASIC", BASIC_CONFIG);

        assertEquals("BASIC", result.gameCode());
        verify(configRepository, never()).save(any(EventGameConfig.class));
    }

    @Test
    @DisplayName("참여 기록이 있으면 바꿀 수 없다 — 저장된 결과가 그때의 방식을 전제한다")
    void 참여기록이있으면_거부한다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        lockEvent();
        Optional<Game> basic = Optional.of(game("BASIC"));
        when(gameRepository.findByCode("BASIC")).thenReturn(basic);
        when(participationRepository.existsByEventId(1L)).thenReturn(true);

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> service.save(1L, ADMIN, "BASIC", BASIC_CONFIG));

        assertEquals(ParticipationErrorCode.PARTICIPATION_CONFIG_LOCKED, exception.getErrorCode());
        verify(configRepository, never()).save(any(EventGameConfig.class));
    }

    @Test
    @DisplayName("설정이 방식에 맞지 않으면 저장하지 않는다 — 통과 못한 설정이 DB 에 남으면 상태 API 가 거짓말한다")
    void 잘못된설정은_저장하지않는다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        lockEvent();
        Optional<Game> basic = Optional.of(game("BASIC"));
        when(gameRepository.findByCode("BASIC")).thenReturn(basic);

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                // IMMEDIATE 인데 winProbability·prizeName 이 없다
                () -> service.save(1L, ADMIN, "BASIC", Map.of("resultMode", "IMMEDIATE")));

        assertEquals(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED, exception.getErrorCode());
        verify(configRepository, never()).save(any(EventGameConfig.class));
    }

    @Test
    void 없는_방식이면_거부한다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        lockEvent();
        when(gameRepository.findByCode("ROULETTE")).thenReturn(Optional.empty());

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> service.save(1L, ADMIN, "ROULETTE", Map.of()));

        assertEquals(ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE, exception.getErrorCode());
    }

    @Test
    @DisplayName("남의 이벤트면 소유자 검사에서 막힌다 — 그 뒤 아무것도 읽지 않는다")
    void 남의이벤트는_막힌다() {
        when(ownerCheck.requireOwned(1L, ADMIN))
                .thenThrow(new AccessDeniedException("이벤트 소유 관리자만 사용할 수 있습니다."));

        assertThrows(AccessDeniedException.class,
                () -> service.save(1L, ADMIN, "BASIC", BASIC_CONFIG));

        verify(gameRepository, never()).findByCode(any());
    }

    @Test
    @DisplayName("설정이 없으면 404 가 아니라 빈 설정을 준다 — 설정 화면은 빈 폼으로 시작한다")
    void 조회는_없어도_빈설정을준다() {
        when(configRepository.findAllByEventId(1L)).thenReturn(List.of());

        ParticipationConfigResponse result = service.find(1L, ADMIN);

        assertNull(result.gameCode());
        assertEquals(Map.of(), result.config());
    }

    @Test
    void 조회는_저장된_설정을_그대로준다() {
        EventGameConfig existing = EventGameConfig.create(event(), game("BASIC"), BASIC_CONFIG);
        List<EventGameConfig> one = List.of(existing);
        when(configRepository.findAllByEventId(1L)).thenReturn(one);

        ParticipationConfigResponse result = service.find(1L, ADMIN);

        assertEquals("BASIC", result.gameCode());
        assertEquals(BASIC_CONFIG, result.config());
    }

    /** save() 가 이벤트 행을 잠근다 (SELECT … FOR UPDATE). 그 조회를 받아 준다. */
    private void lockEvent() {
        Optional<Event> locked = Optional.of(event());
        when(eventRepository.findByIdForDraw(1L)).thenReturn(locked);
    }

    @Test
    @DisplayName("저장은 이벤트 행을 먼저 잠근다 — 참여와 같은 잠금이라 경합이 직렬화된다")
    void 저장은_행을_잠근다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        lockEvent();
        Optional<Game> basic = Optional.of(game("BASIC"));
        when(gameRepository.findByCode("BASIC")).thenReturn(basic);
        when(configRepository.findAllByEventId(1L)).thenReturn(List.of());
        when(configRepository.save(any(EventGameConfig.class)))
                .thenAnswer(call -> call.getArgument(0));

        service.save(1L, ADMIN, "BASIC", BASIC_CONFIG);

        // 참여 체크보다 잠금이 먼저여야 한다 — 아니면 그 사이에 참여가 끼어든다
        InOrder order = inOrder(eventRepository, participationRepository);
        order.verify(eventRepository).findByIdForDraw(1L);
        order.verify(participationRepository).existsByEventId(1L);
    }

    @Test
    @DisplayName("DELAYED 는 winnerCount·announcementAt·prizeName 이 모두 있어야 한다 — 없으면 야간 스케줄러가 매일 실패한다")
    void DELAYED_필수값_누락은_거부한다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        lockEvent();
        Optional<Game> basic = Optional.of(game("BASIC"));
        when(gameRepository.findByCode("BASIC")).thenReturn(basic);

        for (Map<String, Object> values : List.<Map<String, Object>>of(
                Map.of("resultMode", "DELAYED"),
                Map.of("resultMode", "DELAYED", "winnerCount", 10),
                Map.of("resultMode", "DELAYED", "winnerCount", 10, "prizeName", "쿠폰"),
                Map.of("resultMode", "DELAYED", "winnerCount", 0,
                        "prizeName", "쿠폰", "announcementAt", "2026-12-01T00:00:00+09:00"),
                Map.of("resultMode", "DELAYED", "winnerCount", 10,
                        "prizeName", "쿠폰", "announcementAt", "내일"))) {
            ParticipationException exception = assertThrows(
                    ParticipationException.class,
                    () -> service.save(1L, ADMIN, "BASIC", values),
                    () -> "거부되어야 합니다: " + values);
            assertEquals(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
                    exception.getErrorCode());
        }
        verify(configRepository, never()).save(any(EventGameConfig.class));
    }

    private Event event() {
        return BeanUtils.instantiateClass(Event.class);
    }

    private Game game(String code) {
        Game game = mock(Game.class);
        when(game.getCode()).thenReturn(code);
        when(game.isActive()).thenReturn(true);
        return game;
    }
}
