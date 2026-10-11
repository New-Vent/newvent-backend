package com.newvent.participation.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.dto.response.ParticipationConfigResponse;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventGameConfigRepository;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.participation.repository.GameRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 참여 설정 저장·조회. 이벤트를 만든 관리자만 쓴다.
 *
 * ★ 이벤트당 설정은 **하나**다
 *   ParticipationService 가 configs.size() != 1 을 거부하므로, 방식이 바뀌어도
 *   행을 새로 만들지 않고 기존 행을 고친다(upsert). 그래서 POST 가 아니라 PUT 이다.
 *
 * ★ 참여 기록이 있으면 바꿀 수 없다
 *   이미 저장된 result_data 는 그때의 방식을 전제한다. BASIC 으로 참여한 기록이
 *   남아 있는데 LUCKY_POUCH 로 바꾸면 그 기록에 pouchIndex 가 없다.
 *   영구 삭제를 참여 기록으로 막는 것(EVENT409-9)과 같은 이유다.
 *
 * ★ 검증은 ParticipationValidator.validateConfig 하나만 쓴다
 *   참여 경로도 같은 함수를 부른다. 설정 규칙을 두 벌로 쓰면 갈라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationConfigService {

    private final EventOwnerCheck ownerCheck;
    private final GameRepository gameRepository;
    private final EventGameConfigRepository eventGameConfigRepository;
    private final EventParticipationRepository participationRepository;
    private final ParticipationValidator validator;

    @Transactional
    public ParticipationConfigResponse save(
            Long eventId,
            AuthUser admin,
            String gameCode,
            Map<String, Object> config
    ) {
        Event event = ownerCheck.requireOwned(eventId, admin);

        Game game = gameRepository.findByCode(gameCode)
                .orElseThrow(() -> new ParticipationException(
                        ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE));

        // ★ 저장 전에 검증한다. 통과하지 못한 설정이 DB 에 남으면
        //   상태 API 는 "참여 가능" 이라 하고 참여 API 는 409 를 내는 상태가 된다.
        validator.validateConfig(game, config);

        if (participationRepository.existsByEventId(eventId)) {
            throw new ParticipationException(ParticipationErrorCode.PARTICIPATION_CONFIG_LOCKED);
        }

        List<EventGameConfig> existing = eventGameConfigRepository.findAllByEventId(eventId);

        EventGameConfig saved;
        if (existing.isEmpty()) {
            saved = eventGameConfigRepository.save(EventGameConfig.create(event, game, config));
            log.info("참여 설정 생성 — event={}, admin={}, game={}", eventId, admin.id(), gameCode);
        } else {
            // ★ 둘 이상이면 어느 것을 고칠지 정할 수 없다. 사람이 봐야 한다.
            //   (uk_event_game_configs_event_game 이 같은 방식의 중복만 막는다)
            if (existing.size() > 1) {
                throw new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
            }
            saved = existing.getFirst();
            String before = saved.getGame().getCode();
            saved.changeConfig(game, config);
            log.info("참여 설정 변경 — event={}, admin={}, game={} -> {}",
                    eventId, admin.id(), before, gameCode);
        }

        eventGameConfigRepository.flush();
        return ParticipationConfigResponse.from(eventId, saved);
    }

    /**
     * 설정 조회. 없으면 404 가 아니라 **빈 설정**을 돌려준다.
     *
     * ★ 관리자 화면이 "아직 설정하지 않았다" 를 404 로 구별하지 않아도 되게 한다 —
     *   설정 화면은 어차피 빈 폼으로 시작한다.
     */
    @Transactional(readOnly = true)
    public ParticipationConfigResponse find(Long eventId, AuthUser admin) {
        ownerCheck.requireOwned(eventId, admin);

        List<EventGameConfig> configs = eventGameConfigRepository.findAllByEventId(eventId);

        if (configs.size() != 1) {
            return new ParticipationConfigResponse(eventId, null, Map.of());
        }
        return ParticipationConfigResponse.from(eventId, configs.getFirst());
    }
}
