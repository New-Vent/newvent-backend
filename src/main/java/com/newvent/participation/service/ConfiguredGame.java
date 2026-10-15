package com.newvent.participation.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.service.PublishedGame;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventGameConfigRepository;

import lombok.RequiredArgsConstructor;

/**
 * 게시 페이지에 그릴 참여 방식을 찾는다. {@link PublishedGame} 의 구현이다.
 *
 * ★★ 참여 API 와 <b>같은 기준</b>이어야 한다
 *   여기서 "그릴 수 있다" 고 하고 POST 가 409 를 내면, 사용자는 복주머니를 고르고
 *   참여 버튼을 눌렀는데 아무 일도 안 나는 화면을 본다.
 *   그래서 판정을 둘로 둔다 — 개수(하나인가)와 내용(검증기를 통과하는가).
 *
 * ★ 기준 두 가지
 *     ① 설정이 정확히 하나      둘이면 어느 방식으로 참여할지 정할 수 없어 없는 것과 같다
 *                              (ParticipationService 가 size() != 1 을 거부한다)
 *     ② 그 내용이 유효          하나 있어도 모양이 틀리면 참여가 409 다
 */
@Component
@RequiredArgsConstructor
public class ConfiguredGame implements PublishedGame {

    private final EventGameConfigRepository eventGameConfigRepository;
    private final ParticipationValidator validator;

    @Override
    @Transactional(readOnly = true)
    public Optional<Spec> of(Long eventId) {
        List<EventGameConfig> configs = eventGameConfigRepository.findAllByEventId(eventId);

        if (configs.size() != 1) {
            return Optional.empty();
        }

        EventGameConfig config = configs.getFirst();

        if (config.getGame() == null || !valid(config)) {
            return Optional.empty();
        }

        Map<String, Object> values = config.getConfig() == null ? Map.of() : config.getConfig();
        return Optional.of(new Spec(config.getGame().getCode(), values));
    }

    /**
     * 설정 내용을 관리자 저장 경로와 <b>같은 함수로</b> 본다. 기준을 두 벌로 쓰지 않는다.
     *
     * ★ 예외를 삼키는 게 맞다. 「설정이 틀렸다」는 관리자에게 알릴 일이고,
     *   게시 페이지에는 <b>게임 UI 를 그리지 않는다</b>는 사실만 반영하면 된다.
     */
    private boolean valid(EventGameConfig config) {
        try {
            validator.validateConfig(config.getGame(), config.getConfig());
            return true;
        } catch (ParticipationException exception) {
            return false;
        }
    }
}
