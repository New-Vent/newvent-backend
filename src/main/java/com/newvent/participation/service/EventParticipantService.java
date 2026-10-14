package com.newvent.participation.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.auth.dto.AuthUser;
import com.newvent.common.response.PageResponse;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.domain.ParticipationResultStatus;
import com.newvent.participation.dto.response.EventParticipantListResponse;
import com.newvent.participation.dto.response.EventParticipantResponse;
import com.newvent.participation.dto.response.ParticipationSummaryResponse;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.participation.repository.ParticipationSummaryProjection;

import lombok.RequiredArgsConstructor;

/**
 * 한 이벤트의 참여자를 관리자에게 보여준다.
 *
 * ★★ 왜 생겼나 — <b>참여 기록을 볼 길이 아예 없었다.</b>
 *   참여 API 도 추첨도 돌아가서 event_participations 에 행이 쌓이는데,
 *   조회는 전부 {@code user.id} 로 걸린 「내 참여」뿐이었다. 관리자는 당첨자 명단을
 *   뽑을 수도, 참여자가 몇 명인지 볼 수도 없었다. 결과가 들어가기만 하고 나오지 않았다.
 *
 * ★ 남의 이벤트는 못 본다. 참여 입력에 전화번호가 들어갈 수 있어서
 *   (사전예약형) 소유자 검사가 선택이 아니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventParticipantService {

    private final EventOwnerCheck ownerCheck;
    private final EventParticipationRepository participationRepository;

    /**
     * @param resultStatus 결과 상태로 거른다. null 이면 전체 — 당첨자 명단은 {@code WON} 이다
     */
    public EventParticipantListResponse getParticipants(
            Long eventId,
            AuthUser admin,
            ParticipationResultStatus resultStatus,
            int page,
            int size
    ) {
        // ★ 권한을 먼저 본다. 남의 이벤트면 참여 수조차 알려주지 않는다 —
        //   건수만으로도 남의 이벤트 성과가 새어 나간다.
        ownerCheck.requireOwned(eventId, admin);

        Page<EventParticipation> result = participationRepository.findByEventId(
                eventId,
                resultStatus == null ? null : resultStatus.name(),
                PageRequest.of(page, size)
        );

        List<EventParticipantResponse> content = result.getContent().stream()
                .map(EventParticipantResponse::from)
                .toList();

        ParticipationSummaryProjection counts = participationRepository.findSummaryByEventId(eventId);

        ParticipationSummaryResponse summary = ParticipationSummaryResponse.builder()
                .totalParticipationCount(counts.getTotalParticipationCount())
                .rewardCount(counts.getRewardCount())
                .pendingCount(counts.getPendingCount())
                .build();

        return new EventParticipantListResponse(
                summary,
                PageResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements())
        );
    }
}
