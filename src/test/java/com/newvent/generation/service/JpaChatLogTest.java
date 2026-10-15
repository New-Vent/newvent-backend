package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.newvent.generation.domain.ChatMessage;
import com.newvent.generation.domain.ChatMessageStatus;
import com.newvent.generation.domain.ChatRole;
import com.newvent.generation.repository.ChatMessageRepository;

// 채팅 내역을 실제 PostgreSQL 에 남기는 JpaChatLog.
// 테스트가 트랜잭션을 열지 않는다
// @DataJpaTest 는 @Service 를 안 올린다 - JpaChatLog 를 @Import 로 직접 넣는다.
// ddl-auto=validate - Flyway 가 만든 스키마와 엔티티가 어긋나면 여기서 죽는다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaChatLog.class, com.newvent.common.config.JpaAuditingConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaChatLogTest {

    @Autowired
    private JpaChatLog chatLog;

    @Autowired
    private ChatMessageRepository messages;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    private final List<Long> eventIds = new ArrayList<>();
    private final List<Long> adminIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        eventIds.forEach(id -> jdbc.update("DELETE FROM events WHERE id = ?", id));
        adminIds.forEach(id -> jdbc.update("DELETE FROM admins WHERE id = ?", id));
    }

    private Long seedEvent(String name) {
        String login = "chatlog_" + name + "_" + System.nanoTime();
        Long adminId = jdbc.queryForObject(
                "INSERT INTO admins (login_id, password_hash, name) VALUES (?,?,?) RETURNING id",
                Long.class, login, "x", "관리자");
        adminIds.add(adminId);
        Long eventId = jdbc.queryForObject(
                "INSERT INTO events (owner_admin_id, title, grade) VALUES (?,?,?) RETURNING id",
                Long.class, adminId, name, "NORMAL");
        eventIds.add(eventId);
        return eventId;
    }

    private List<ChatMessage> all(Long eventId) {
        return messages.findPageByEventId(eventId, PageRequest.of(0, 100)).getContent();
    }

    @Test
    @DisplayName("★ 스프링 빈으로 등록되는 클래스여야 한다 - @Service 가 빠지면 EditService · GenerationService 가 ChatLog 를 못 찾아 서버가 못 뜬다")
    void 빈으로_등록된다() {
        // 이 테스트 클래스는 @Import 로 JpaChatLog 를 직접 넣기 때문에, @Service 가 빠져도 다른 테스트는 통과한다.
        // 실제 서버의 컴포넌트 스캔이 이 클래스를 찾는지는 어노테이션으로 확인한다.
        assertTrue(org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(
                JpaChatLog.class, org.springframework.stereotype.Component.class));
    }

    @Test
    @DisplayName("요청은 ADMIN 메시지로 PENDING 상태로 남는다")
    void 요청이_남는다() {
        Long eventId = seedEvent("여름 이벤트");

        Long id = chatLog.request(eventId, "제목을 가을 축제로 바꿔줘", "hero", false);

        assertNotNull(id);
        List<ChatMessage> saved = all(eventId);
        assertEquals(1, saved.size());
        ChatMessage m = saved.get(0);
        assertEquals(ChatRole.ADMIN, m.getRole());
        assertEquals("제목을 가을 축제로 바꿔줘", m.getContent());
        assertEquals("hero", m.getTargetBlock());
        assertEquals(ChatMessageStatus.PENDING, m.getStatus());
        assertNotNull(m.getCreatedAt());
    }

    @Test
    @DisplayName("★ 호출한 쪽이 읽기 전용 트랜잭션이어도 남고, 호출한 쪽 트랜잭션은 멀쩡히 커밋된다")
    void 읽기_전용_트랜잭션_안에서_불러도_남는다() {
        // 회귀 테스트: GenerateController · EditController 의 start() 가 @Transactional(readOnly = true) 다.
        //   예전에는 JpaChatLog 가 그 트랜잭션에 합류해 INSERT 가 거부되고,
        //   바깥 트랜잭션이 rollback-only 가 되어 생성·수정 요청이 500 으로 실패했다.
        Long eventId = seedEvent("여름 이벤트");
        TransactionTemplate readOnly = new TransactionTemplate(txManager);
        readOnly.setReadOnly(true);

        Long id = assertDoesNotThrow(() -> readOnly.execute(status -> {
            Long requestId = chatLog.request(eventId, "읽기 전용 안에서 부른 요청", null, false);
            chatLog.finish(requestId, eventId, ChatMessageStatus.COMPLETED, "응답");
            return requestId;
        }), "읽기 전용 바깥 트랜잭션이 rollback-only 로 오염됐습니다.");

        assertNotNull(id, "읽기 전용 트랜잭션 안에서 부르면 남기지 못합니다.");
        assertEquals(2, all(eventId).size());
        assertEquals(ChatMessageStatus.COMPLETED, all(eventId).get(0).getStatus());
    }

    @Test
    @DisplayName("★ 개인정보가 감지된 요청은 원문을 저장하지 않고 표시만 남긴다")
    void 개인정보_요청은_원문이_저장되지_않는다() {
        Long eventId = seedEvent("여름 이벤트");

        chatLog.request(eventId, "문의는 a@example.com 으로 받아줘", null, true);

        List<ChatMessage> saved = all(eventId);
        assertEquals(1, saved.size());
        assertEquals(ChatMessage.PRIVACY_PLACEHOLDER, saved.get(0).getContent());
        assertFalse(saved.stream().anyMatch(m -> m.getContent().contains("example.com")),
                "개인정보 원문이 chat_messages 에 저장됐습니다.");
    }

    @Test
    @DisplayName("끝맺으면 요청 상태가 바뀌고, AI 응답이 같은 상태로 뒤따른다")
    void 끝맺으면_응답이_남는다() {
        Long eventId = seedEvent("여름 이벤트");
        Long id = chatLog.request(eventId, "제목을 바꿔줘", null, false);

        chatLog.finish(id, eventId, ChatMessageStatus.COMPLETED, "수정을 반영했습니다.");

        List<ChatMessage> saved = all(eventId);
        assertEquals(2, saved.size());
        assertEquals(ChatRole.ADMIN, saved.get(0).getRole());
        assertEquals(ChatMessageStatus.COMPLETED, saved.get(0).getStatus());
        assertEquals(ChatRole.ASSISTANT, saved.get(1).getRole());
        assertEquals("수정을 반영했습니다.", saved.get(1).getContent());
        assertEquals(ChatMessageStatus.COMPLETED, saved.get(1).getStatus());
    }

    @Test
    @DisplayName("실패와 취소도 요청 상태에 그대로 남는다")
    void 실패와_취소() {
        Long eventId = seedEvent("여름 이벤트");
        Long failed = chatLog.request(eventId, "첫 요청", null, false);
        chatLog.finish(failed, eventId, ChatMessageStatus.FAILED, "연결하지 못했습니다.");
        Long cancelled = chatLog.request(eventId, "둘째 요청", null, false);
        chatLog.finish(cancelled, eventId, ChatMessageStatus.CANCELLED, "생성을 중단했습니다.");

        List<ChatMessage> saved = all(eventId);
        assertEquals(4, saved.size());
        assertEquals(ChatMessageStatus.FAILED, saved.get(0).getStatus());
        assertEquals(ChatMessageStatus.FAILED, saved.get(1).getStatus());
        assertEquals(ChatMessageStatus.CANCELLED, saved.get(2).getStatus());
        assertEquals(ChatMessageStatus.CANCELLED, saved.get(3).getStatus());
    }

    @Test
    @DisplayName("응답 문장이 비어 있으면 응답 메시지는 남기지 않는다")
    void 빈_응답은_남기지_않는다() {
        Long eventId = seedEvent("여름 이벤트");
        Long id = chatLog.request(eventId, "제목을 바꿔줘", null, false);

        chatLog.finish(id, eventId, ChatMessageStatus.COMPLETED, " ");

        List<ChatMessage> saved = all(eventId);
        assertEquals(1, saved.size());
        assertEquals(ChatMessageStatus.COMPLETED, saved.get(0).getStatus());
    }

    @Test
    @DisplayName("★ 다른 이벤트의 메시지는 끝맺지 않는다")
    void 남의_이벤트_메시지는_건드리지_않는다() {
        Long a = seedEvent("이벤트 A");
        Long b = seedEvent("이벤트 B");
        Long requestOfA = chatLog.request(a, "A 의 요청", null, false);

        chatLog.finish(requestOfA, b, ChatMessageStatus.COMPLETED, "B 로 끝맺기 시도");

        assertEquals(ChatMessageStatus.PENDING, all(a).get(0).getStatus());
        assertTrue(all(b).isEmpty(), "다른 이벤트에 응답 메시지가 생겼습니다.");
    }

    @Test
    @DisplayName("요청 id 가 null 이면 끝맺기와 영역 기록은 조용히 건너뛴다")
    void null_요청은_건너뛴다() {
        Long eventId = seedEvent("여름 이벤트");

        chatLog.finish(null, eventId, ChatMessageStatus.COMPLETED, "응답");
        chatLog.target(null, "hero");

        assertTrue(all(eventId).isEmpty());
    }

    @Test
    @DisplayName("라우터가 정한 영역을 나중에 채운다. 50자를 넘으면 컬럼 길이에 맞춰 자른다")
    void target_block() {
        Long eventId = seedEvent("여름 이벤트");
        Long id = chatLog.request(eventId, "고쳐줘", null, false);

        chatLog.target(id, "hero,benefits");
        assertEquals("hero,benefits", all(eventId).get(0).getTargetBlock());

        chatLog.target(id, "x".repeat(80));
        assertEquals(50, all(eventId).get(0).getTargetBlock().length());
    }

    @Test
    @DisplayName("★ 서버가 켜질 때 PENDING 만 FAILED 로 정리하고 나머지는 그대로 둔다")
    void 기동_시_PENDING_정리() {
        // 주의: failStalePending 은 DB 전체의 PENDING 을 바꾼다. 개발 DB 에 남은 PENDING 도 FAILED 가 된다.
        //       서버가 켜질 때 하는 일 그대로라서 의도된 동작이다.
        Long eventId = seedEvent("여름 이벤트");
        Long pending = chatLog.request(eventId, "처리 중이던 요청", null, false);
        Long done = chatLog.request(eventId, "끝난 요청", null, false);
        chatLog.finish(done, eventId, ChatMessageStatus.COMPLETED, null);
        Long cancelled = chatLog.request(eventId, "취소된 요청", null, false);
        chatLog.finish(cancelled, eventId, ChatMessageStatus.CANCELLED, null);

        chatLog.failStalePending();

        assertEquals(ChatMessageStatus.FAILED, messages.findById(pending).orElseThrow().getStatus());
        assertEquals(ChatMessageStatus.COMPLETED, messages.findById(done).orElseThrow().getStatus());
        assertEquals(ChatMessageStatus.CANCELLED, messages.findById(cancelled).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("★ 저장에 실패해도 예외를 던지지 않고 null 을 돌려준다 - 채팅 내역 때문에 생성이 실패하면 안 된다")
    void 저장_실패는_삼킨다() {
        // 없는 이벤트 id 라 FK 위반으로 실패한다
        Long id = chatLog.request(999_999_999L, "요청", null, false);

        assertNull(id);
    }

    @Test
    @DisplayName("저장에 실패한 호출이 읽기 전용 바깥 트랜잭션을 망가뜨리지 않는다")
    void 저장_실패가_바깥_트랜잭션을_오염시키지_않는다() {
        TransactionTemplate readOnly = new TransactionTemplate(txManager);
        readOnly.setReadOnly(true);

        Long result = assertDoesNotThrow(() -> readOnly.execute(status ->
                chatLog.request(999_999_999L, "요청", null, false)));

        assertNull(result);
    }
}
