package com.newvent.generation.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.newvent.generation.domain.ChatMessage;
import com.newvent.generation.domain.ChatMessageStatus;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 다른 이벤트의 메시지는 못 찾는다 - 버전에 남의 이벤트 메시지를 걸 수 없게 eventId 를 같이 본다
    Optional<ChatMessage> findByIdAndEventId(Long id, Long eventId);


    // 이벤트의 대화를 시간순으로. 정렬은 Pageable 이 아니라 쿼리가 정한다
    @Query(value = "SELECT m FROM ChatMessage m WHERE m.event.id = :eventId ORDER BY m.createdAt ASC, m.id ASC",
            countQuery = "SELECT COUNT(m) FROM ChatMessage m WHERE m.event.id = :eventId")
    Page<ChatMessage> findPageByEventId(@Param("eventId") Long eventId, Pageable pageable);

    // 서버 기동 시 정리용 - 바꾼 행 수를 돌려준다
    @Modifying
    @Query("UPDATE ChatMessage m SET m.status = :to WHERE m.status = :from")
    int updateStatus(@Param("from") ChatMessageStatus from, @Param("to") ChatMessageStatus to);
}
