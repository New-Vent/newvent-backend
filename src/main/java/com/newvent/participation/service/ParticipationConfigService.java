package com.newvent.participation.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 참여 설정 저장·조회. 이벤트를 만든 관리자만 쓴다.
 *
 * ★ 이벤트당 설정은 **하나**다
 *   ParticipationService 가 configs.size() != 1 을 거부하므로, 방식이 바뀌어도
 *   행을 새로 만들지 않고 기존 행을 고친다(upsert). 그래서 POST 가 아니라 PUT 이다.
 *
 * ★ 참여 기록이 있으면 **결과를 뒤집는 값만** 바꿀 수 없다
 *   이미 저장된 result_data 는 그때의 방식을 전제한다. BASIC 으로 참여한 기록이
 *   남아 있는데 LUCKY_POUCH 로 바꾸면 그 기록에 pouchIndex 가 없다.
 *   영구 삭제를 참여 기록으로 막는 것(EVENT409-9)과 같은 이유다.
 *
 *   다만 전에는 **통째로** 거부했고 그건 너무 넓었다 — 경품명 오타도 못 고치고,
 *   스포츠 예측형은 정답을 넣을 길이 없어 기능 자체가 성립하지 않았다.
 *   지금은 {@link ParticipationConfigLock} 이 값 단위로 가른다.
 *
 * ★ 검증은 ParticipationValidator.validateConfig 하나만 쓴다
 *   참여 경로도 같은 함수를 부른다. 설정 규칙을 두 벌로 쓰면 갈라진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipationConfigService {

    private final EventOwnerCheck ownerCheck;
    private final EventRepository eventRepository;
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
        // ① 권한을 먼저 본다. 남의 이벤트가 행을 잠그게 하지 않는다
        ownerCheck.requireOwned(eventId, admin);

        // ② ★ 이벤트 행을 잠근다 (SELECT … FOR UPDATE)
        //   ParticipationService.participate 가 맨 먼저 같은 잠금을 잡는다.
        //   잠금 없이 existsByEventId 만 보면 이런 경합이 생긴다:
        //
        //     관리자(설정 변경)            사용자(참여)
        //     existsByEventId → 0건
        //                                  설정 읽기 (옛 방식)
        //                                  참여 저장 (옛 방식의 result_data)
        //                                  커밋
        //     changeConfig (새 방식)
        //     커밋
        //
        //   결과: result_data 는 BASIC 인데 설정은 LUCKY_POUCH 다. 그 기록에는
        //   pouchIndex 가 없어서 조회 화면이 설명할 수 없는 상태가 된다.
        //   같은 행을 잠그면 두 트랜잭션이 직렬화되어 뒤에 온 쪽이 기다린다.
        Event event = eventRepository.findByIdForDraw(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));

        Game game = gameRepository.findByCode(gameCode)
                .orElseThrow(() -> new ParticipationException(
                        ParticipationErrorCode.UNSUPPORTED_PARTICIPATION_TYPE));

        // ③ 저장 전에 검증한다. 통과하지 못한 설정이 DB 에 남으면
        //   상태 API 는 "참여 가능" 이라 하고 참여 API 는 409 를 내는 상태가 된다.
        validator.validateConfig(game, config);
        requireAnnouncementAfterEnd(event, game, config);

        // ④ 잠금을 잡은 뒤에 본다. 이제 그 사이에 참여가 끼어들 수 없다
        boolean participated = participationRepository.existsByEventId(eventId);

        List<EventGameConfig> existing = eventGameConfigRepository.findAllByEventId(eventId);

        EventGameConfig saved;
        if (existing.isEmpty()) {
            // ★ 참여 기록이 있는데 설정이 없는 상태는 성립하지 않는다 — 참여 API 가
            //   설정 없이는 받아 주지 않는다. 그래도 왔다면 손으로 지운 것이라 사람이 봐야 한다.
            if (participated) {
                throw new ParticipationException(ParticipationErrorCode.PARTICIPATION_CONFIG_LOCKED);
            }
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

            // ★ 참여 기록이 있으면 **결과를 뒤집지 않는 값만** 바꿀 수 있다.
            //   전에는 통째로 거부했는데, 그러면 경품명 오타도 못 고치고
            //   스포츠 예측형은 정답을 넣을 길이 없어 아예 성립하지 않았다.
            //   무엇이 허용되고 왜인지는 ParticipationConfigLock 에 있다.
            if (participated && !ParticipationConfigLock.allows(
                    before, gameCode, saved.getConfig(), config)) {
                throw new ParticipationException(ParticipationErrorCode.PARTICIPATION_CONFIG_LOCKED);
            }

            saved.changeConfig(game, config);
            log.info("참여 설정 변경 — event={}, admin={}, game={} -> {}, 참여기록={}",
                    eventId, admin.id(), before, gameCode, participated);
        }

        eventGameConfigRepository.flush();
        return ParticipationConfigResponse.from(eventId, saved);
    }

    /**
     * 추후 추첨의 발표 시각은 **이벤트 종료 이후**여야 한다.
     *
     * ★ 스케줄러(DelayedEventDrawService)가 같은 조건을 검사하고 어기면 예외를 던진다.
     *   거기서 걸리면 로그에만 남고 관리자는 모른다 — 저장 시점에 막는다.
     * ★ 종료일이 없으면 비교할 수 없으니 통과시킨다. 그 경우 스케줄러도 돌지 않는다.
     */
    private void requireAnnouncementAfterEnd(Event event, Game game, Map<String, Object> config) {
        boolean sports = "SPORTS_PREDICTION".equals(game.getCode());

        if (!AnnouncementRule.mustFollowEnd(game.getCode(), config)) {
            return;
        }

        if (sports && (config.containsKey("winningPrediction") || config.containsKey("drawnAt"))) {
            throw new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
        }

        if (event.getEndDate() == null) {
            if (sports) {
                throw new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
            }

            // 기존 기본형 동작 유지
            return;
        }

        if (!validator.readAnnouncementAt(config).isAfter(event.getEndDate())) {
            throw new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED);
        }
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
