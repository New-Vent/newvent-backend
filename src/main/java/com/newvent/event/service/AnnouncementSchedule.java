package com.newvent.event.service;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 이 이벤트의 참여 설정에 <b>종료 이후여야 하는 발표 시각</b>이 있는가.
 *
 * ★ 왜 필요한가 — 발표 시각은 참여 설정을 저장할 때만 검사했다. 나중에 종료일을 발표 시각보다
 *   뒤로 미루면 그대로 저장됐고, 추첨 스케줄러가 "발표 시각은 종료 이후여야 한다" 로 실패해
 *   로그에만 남고 추첨이 돌지 않았다. 관리자는 몰랐다.
 *
 * ★ 왜 인터페이스인가 — {@link PublishedGame} 과 같은 이유. 참여 설정은 participation 패키지에 있고
 *   participation 은 이미 event 를 본다. 필요한 쪽(event)이 모양을 정하고, 아는 쪽이 구현한다.
 */
public interface AnnouncementSchedule {

    /** 종료 이후여야 하는 발표 시각. 그런 설정이 없거나 읽을 수 없으면 빈 값 */
    Optional<OffsetDateTime> mustFollowEnd(Long eventId);
}
