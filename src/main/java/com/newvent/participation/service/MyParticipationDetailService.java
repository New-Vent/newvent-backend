package com.newvent.participation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.dto.response.MyParticipationDetailResponse;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventParticipationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyParticipationDetailService {

    private final EventParticipationRepository eventParticipationRepository;

    public MyParticipationDetailResponse getParticipation(Long userId, Long participationId){
        EventParticipation participation = eventParticipationRepository.findByIdAndUserId(participationId, userId)
                .orElseThrow(() -> new ParticipationException(ParticipationErrorCode.PARTICIPATION_NOT_FOUND));

        return MyParticipationDetailResponse.from(participation);
    }
}
