package com.newvent.participation.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.service.AnnouncementSchedule;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventGameConfigRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link AnnouncementSchedule} 의 구현 — 참여 설정에서 종료 이후여야 하는 발표 시각을 꺼낸다.
 *
 * ★ 기준은 저장 경로와 같다 ({@link AnnouncementRule}). 설정이 하나가 아니거나 발표 시각을
 *   읽을 수 없으면 빈 값이다 — 비교할 수 없는 것으로 종료일 변경을 막지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ConfiguredAnnouncement implements AnnouncementSchedule {

    private final EventGameConfigRepository eventGameConfigRepository;
    private final ParticipationValidator validator;

    @Override
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> mustFollowEnd(Long eventId) {
        List<EventGameConfig> configs = eventGameConfigRepository.findAllByEventId(eventId);
        if (configs.size() != 1) return Optional.empty();

        EventGameConfig config = configs.getFirst();
        if (config.getGame() == null || config.getConfig() == null
                || !AnnouncementRule.mustFollowEnd(config.getGame().getCode(), config.getConfig())) {
            return Optional.empty();
        }
        try {
            return Optional.of(validator.readAnnouncementAt(config.getConfig()));
        } catch (ParticipationException e) {
            return Optional.empty();
        }
    }
}
