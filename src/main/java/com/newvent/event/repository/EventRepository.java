package com.newvent.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.event.domain.Event;

/**
 * **이 파일은 이벤트 파트 것입니다.** 브랜치에 이미 있으면 이 파일은 넣지 마세요.
 *
 *   생성 경로가 `Event` 를 읽어야 하는데 읽을 통로가 없으면 컴파일이 안 됩니다.
 *   이미 있으면 그대로 쓰고, 없으면 이 최소 형태로 넣은 뒤
 *   이벤트 파트가 필요한 메서드를 더하면 됩니다. 제 쪽에서는 `findById` 만 씁니다.
 */
public interface EventRepository extends JpaRepository<Event, Long> {
}
