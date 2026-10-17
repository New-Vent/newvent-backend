package com.newvent.participation.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.service.PublicEventService;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.ParticipationUnavailableReason;
import com.newvent.participation.dto.response.ParticipationStatusResponse;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventGameConfigRepository;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * 참여 가능 여부. 화면이 버튼을 보일지, 어떤 입력을 받을지 정하는 데 쓴다.
 *
 * ★ 판정 기준은 ParticipationService.participate 와 같아야 한다.
 *   여기서 "가능" 이라고 하고 POST 가 거절하면 화면은 **항상 실패하는 버튼**을 띄운다.
 *   참여 설정 검사가 빠져 있던 것이 그 경우였다 — canParticipate=true 인데
 *   POST 는 409 PARTICIPATION409-1 이었다.
 */
@Service
@RequiredArgsConstructor
public class ParticipationStatusService {

    private final PublicEventService publicEventService;
    private final UserService userService;
    private final EventParticipationRepository participationRepository;
    private final EventGameConfigRepository eventGameConfigRepository;
    private final ParticipationValidator validator;

    @Transactional(readOnly = true)
    public ParticipationStatusResponse getStatus(Long eventId, Long userId) {
        Event event = publicEventService.getPublicEvent(eventId);
        User user = userService.getById(userId);

        if (event.getStartDate() != null && OffsetDateTime.now().isBefore(event.getStartDate())) {
            return ParticipationStatusResponse.builder()
                    .participated(false)
                    .canParticipate(false)
                    .unavailableReason(ParticipationUnavailableReason.NOT_STARTED)
                    .build();
        }

        boolean participated = participationRepository.existsByEventIdAndUserId(eventId, userId);
        boolean gradeAllowed = gradeRank(user.getMembershipGrade()) >= gradeRank(event.getGrade());

        // ★ size() == 1 이 기준이다. ParticipationService 가 그걸로 거르므로 같게 맞춘다 —
        //   설정이 둘이면 어느 방식으로 참여할지 정할 수 없어서 없는 것과 같다.
        List<EventGameConfig> configs = eventGameConfigRepository.findAllByEventId(eventId);
        boolean configured = configs.size() == 1 && valid(configs.getFirst());
        String gameCode = configured ? configs.getFirst().getGame().getCode() : null;

        // ★ 순서가 POST 와 같다 — 등급을 먼저 보고 설정을 나중에 본다.
        //   그래야 둘 다 걸릴 때 두 API 가 같은 이유를 말한다.
        ParticipationUnavailableReason reason = null;

        if (participated) {
            reason = ParticipationUnavailableReason.ALREADY_PARTICIPATED;
        } else if (!gradeAllowed) {
            reason = ParticipationUnavailableReason.INSUFFICIENT_GRADE;
        } else if (!configured) {
            reason = ParticipationUnavailableReason.NOT_CONFIGURED;
        }

        return ParticipationStatusResponse.builder()
                .participated(participated)
                .canParticipate(!participated && gradeAllowed && configured)
                .unavailableReason(reason)
                .gameCode(gameCode)
                .build();
    }

    /**
     * 설정이 하나 있다는 것만으로는 부족하다 — **내용까지** 참여 API 와 같은 기준으로 본다.
     *
     * ★ 단일성만 보면 여전히 거짓말을 한다
     *   설정이 하나 있지만 모양이 틀린 경우(지원하지 않는 방식, LUCKY_POUCH 인데
     *   칸에 prizeName 이 없음 …)에 canParticipate=true 를 주고 POST 는 409 를 낸다.
     *   그래서 validateConfig 를 그대로 부른다 — 기준을 두 벌로 쓰지 않는다.
     *
     * ★ 예외를 삼키는 게 맞다
     *   상태 조회는 "지금 참여할 수 있나" 에 답하는 API 다. 설정이 틀렸다는 것은
     *   관리자에게 알릴 일이고, 사용자에게는 **참여 불가**라는 사실만 전하면 된다.
     *   사유는 NOT_CONFIGURED 로 묶인다 — 사용자가 고칠 수 없는 것은 하나다.
     */
    private boolean valid(EventGameConfig config) {
        try {
            validator.validateConfig(config.getGame(), config.getConfig());
            return true;
        } catch (ParticipationException exception) {
            return false;
        }
    }

    private int gradeRank(MembershipGrade grade) {
        return switch (grade) {
            case NORMAL -> 0;
            case EXCELLENT -> 1;
            case BEST -> 2;
        };
    }
}
