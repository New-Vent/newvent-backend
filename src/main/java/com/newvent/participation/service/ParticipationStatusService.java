package com.newvent.participation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.Event;
import com.newvent.event.service.PublicEventService;
import com.newvent.participation.domain.ParticipationUnavailableReason;
import com.newvent.participation.dto.response.ParticipationStatusResponse;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParticipationStatusService {

    private final PublicEventService publicEventService;
    private final UserService userService;
    private final EventParticipationRepository participationRepository;

    @Transactional(readOnly = true)
    public ParticipationStatusResponse getStatus(Long eventId, Long userId) {
        Event event = publicEventService.getPublicEvent(eventId);
        User user = userService.getById(userId);

        boolean participated = participationRepository.existsByEventIdAndUserId(eventId, userId);
        boolean gradeAllowed = gradeRank(user.getMembershipGrade()) >= gradeRank(event.getGrade());

        ParticipationUnavailableReason reason = null;

        if (participated) {
            reason = ParticipationUnavailableReason.ALREADY_PARTICIPATED;
        } else if (!gradeAllowed) {
            reason = ParticipationUnavailableReason.INSUFFICIENT_GRADE;
        }

        return ParticipationStatusResponse.builder()
                .participated(participated)
                .canParticipate(!participated && gradeAllowed)
                .unavailableReason(reason)
                .build();
    }

    private int gradeRank(MembershipGrade grade) {
        return switch (grade) {
            case NORMAL -> 0;
            case EXCELLENT -> 1;
            case BEST -> 2;
        };
    }
}
