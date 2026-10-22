package com.newvent.participation.service;

import java.util.Map;

/**
 * 발표 시각(announcementAt)이 <b>이벤트 종료 이후여야</b> 하는 참여 방식인가.
 *
 * ★ 한 곳에 둔다 — 참여 설정을 저장할 때(ParticipationConfigService)와
 *   이벤트 종료일을 바꿀 때(ConfiguredAnnouncement)가 같은 기준을 봐야 한다.
 *   둘이 갈리면 설정은 통과했는데 종료일을 미루는 순간 추첨이 조용히 멈춘다.
 */
final class AnnouncementRule {

    private AnnouncementRule() {}

    /** 승부 예측형, 그리고 결과를 나중에 추첨하는 기본형(resultMode=DELAYED) */
    static boolean mustFollowEnd(String gameCode, Map<String, Object> config) {
        if ("SPORTS_PREDICTION".equals(gameCode)) return true;
        return "BASIC".equals(gameCode) && config != null && "DELAYED".equals(config.get("resultMode"));
    }
}
