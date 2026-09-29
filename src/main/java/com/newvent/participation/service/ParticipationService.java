package com.newvent.participation.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.service.PublicEventService;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.dto.response.ParticipationCreateResponse;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParticipationService {

    private final PublicEventService publicEventService;
    private final UserService userService;
    private final EventParticipationRepository participationRepository;

    @Transactional
    public ParticipationCreateResponse participate(Long eventId, Long userId) {
        Event event = publicEventService.getPublicEvent(eventId);

        // 실제 참여는 게시 중인 이벤트에만 허용함
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventException(EventErrorCode.EVENT_NOT_ACCESSIBLE);
        }

        User user = userService.getById(userId);

        if (gradeRank(user.getMembershipGrade()) < gradeRank(event.getGrade())) {
            throw new ParticipationException(ParticipationErrorCode.INSUFFICIENT_GRADE);
        }

        if (participationRepository.existsByEventIdAndUserId(eventId, userId)) {
            throw new ParticipationException(ParticipationErrorCode.ALREADY_PARTICIPATED);
        }

        try {
            EventParticipation participation = participationRepository.saveAndFlush(EventParticipation.create(event, user));
            return ParticipationCreateResponse.builder()
                    .participationId(participation.getId())
                    .eventId(eventId)
                    .build();

        } catch (DataIntegrityViolationException exception) {
            // 동시에 들어온 요청이 (event_id, user_id) 제약에 걸린 경우
            throw new ParticipationException(ParticipationErrorCode.ALREADY_PARTICIPATED);
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
