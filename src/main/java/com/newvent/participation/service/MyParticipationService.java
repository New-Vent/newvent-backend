package com.newvent.participation.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.dto.response.PageResponse;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.dto.request.ParticipationListFilter;
import com.newvent.participation.dto.response.MyParticipationListResponse;
import com.newvent.participation.dto.response.MyParticipationResponse;
import com.newvent.participation.dto.response.ParticipationSummaryResponse;
import com.newvent.participation.repository.EventParticipationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyParticipationService {

    private static final String WON = "WON";
    private static final String PENDING = "PENDING";

    private final EventParticipationRepository participationRepository;

    public MyParticipationListResponse getParticipations(
            Long userId,
            ParticipationListFilter filter,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<EventParticipation> result = switch (filter) {
            case ALL -> participationRepository.findMyParticipations(
                    userId,
                    pageable
            );
            case REWARDS ->
                    participationRepository.findMyParticipationsByResultStatus(
                            userId,
                            WON,
                            pageable
                    );
        };

        List<MyParticipationResponse> content = result.getContent()
                .stream()
                .map(MyParticipationResponse::from)
                .toList();

        ParticipationSummaryResponse summary =
                new ParticipationSummaryResponse(
                        participationRepository.countByUserId(userId),
                        participationRepository.countByUserIdAndResultStatus(
                                userId,
                                WON
                        ),
                        participationRepository.countByUserIdAndResultStatus(
                                userId,
                                PENDING
                        )
                );

        PageResponse<MyParticipationResponse> participations =
                PageResponse.of(
                        content,
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements()
                );

        return new MyParticipationListResponse(
                summary,
                participations
        );
    }
}
