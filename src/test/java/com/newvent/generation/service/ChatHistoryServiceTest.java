package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.domain.ChatMessage;
import com.newvent.generation.domain.ChatMessageStatus;
import com.newvent.generation.domain.ChatRole;
import com.newvent.generation.dto.ChatMessageResponse;
import com.newvent.generation.repository.ChatMessageRepository;

// 채팅 내역 조회. 실제 PostgreSQL 에서 정렬 · 페이지 · 버전 번호 · 소유자 검사를 본다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ChatHistoryService.class, com.newvent.common.config.JpaAuditingConfig.class})
class ChatHistoryServiceTest {

    @Autowired
    private ChatHistoryService history;

    @Autowired
    private ChatMessageRepository messages;

    @Autowired
    private EventVersionRepository versions;

    @Autowired
    private TestEntityManager tem;

    // adminId, eventId
    private long[] seedEvent(String name) {
        EntityManager em = tem.getEntityManager();
        long adminId = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, "admin_" + name)
                .setParameter(2, "x")
                .setParameter(3, "관리자")
                .getSingleResult()).longValue();
        long eventId = ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade) VALUES (?1,?2,?3) RETURNING id")
                .setParameter(1, adminId)
                .setParameter(2, name)
                .setParameter(3, "NORMAL")
                .getSingleResult()).longValue();
        return new long[] {adminId, eventId};
    }

    private ChatMessage add(long eventId, ChatRole role, String content, ChatMessageStatus status) {
        return messages.saveAndFlush(ChatMessage.create(
                tem.getEntityManager().getReference(Event.class, eventId), role, content, null, status));
    }

    @Test
    @DisplayName("대화는 오래된 것부터 시간순이고, 페이지로 나뉜다")
    void 시간순_페이지() {
        long[] e = seedEvent("여름 이벤트");
        for (int i = 1; i <= 5; i++) {
            add(e[1], i % 2 == 1 ? ChatRole.ADMIN : ChatRole.ASSISTANT, "메시지 " + i, ChatMessageStatus.COMPLETED);
        }
        tem.clear();

        PageResponse<ChatMessageResponse> first = history.list(e[1], e[0], 0, 2);
        PageResponse<ChatMessageResponse> last = history.list(e[1], e[0], 2, 2);

        assertEquals(5, first.totalElements());
        assertEquals(3, first.totalPages());
        assertEquals(List.of("메시지 1", "메시지 2"), first.content().stream().map(ChatMessageResponse::content).toList());
        assertEquals(List.of("메시지 5"), last.content().stream().map(ChatMessageResponse::content).toList());
        assertEquals(ChatRole.ADMIN, first.content().get(0).role());
    }

    @Test
    @DisplayName("요청으로 만들어진 버전의 번호가 같이 나가고, 버전을 만들지 않은 메시지는 null 이다")
    void 버전_번호() {
        long[] e = seedEvent("여름 이벤트");
        ChatMessage request = add(e[1], ChatRole.ADMIN, "제목을 바꿔줘", ChatMessageStatus.COMPLETED);
        add(e[1], ChatRole.ASSISTANT, "수정을 반영했습니다.", ChatMessageStatus.COMPLETED);
        ChatMessage failed = add(e[1], ChatRole.ADMIN, "실패한 요청", ChatMessageStatus.FAILED);
        versions.saveAndFlush(EventVersion.create(
                tem.getEntityManager().getReference(Event.class, e[1]), 3, "<p>v3</p>", null, null, request));
        tem.clear();

        List<ChatMessageResponse> content = history.list(e[1], e[0], 0, 10).content();

        assertEquals(3, content.size());
        assertEquals(3, content.get(0).versionNo(), "요청 메시지에는 만든 버전의 번호가 나가야 합니다.");
        assertNull(content.get(1).versionNo(), "AI 응답에는 버전 번호가 없습니다.");
        assertNull(content.get(2).versionNo(), "실패한 요청은 버전을 만들지 않았습니다.");
        assertEquals(failed.getId(), content.get(2).id());
    }

    @Test
    @DisplayName("다른 이벤트의 대화는 섞이지 않는다")
    void 이벤트별로_나뉜다() {
        long[] a = seedEvent("이벤트 A");
        long[] b = seedEvent("이벤트 B");
        add(a[1], ChatRole.ADMIN, "A 의 요청", ChatMessageStatus.COMPLETED);
        add(b[1], ChatRole.ADMIN, "B 의 요청", ChatMessageStatus.COMPLETED);
        tem.clear();

        List<ChatMessageResponse> content = history.list(a[1], a[0], 0, 10).content();

        assertEquals(1, content.size());
        assertEquals("A 의 요청", content.get(0).content());
    }

    @Test
    @DisplayName("대화가 없으면 빈 페이지이다")
    void 대화가_없으면_빈_페이지() {
        long[] e = seedEvent("여름 이벤트");

        PageResponse<ChatMessageResponse> page = history.list(e[1], e[0], 0, 10);

        assertTrue(page.content().isEmpty());
        assertEquals(0, page.totalElements());
    }

    @Test
    @DisplayName("★ 이벤트를 만든 관리자가 아니면 볼 수 없다 - 접근할 수 없는 이벤트로 답한다")
    void 소유자가_아니면_거부() {
        long[] owner = seedEvent("이벤트 A");
        long[] other = seedEvent("이벤트 B");
        add(owner[1], ChatRole.ADMIN, "비밀 요청", ChatMessageStatus.COMPLETED);

        EventException e = assertThrows(EventException.class,
                () -> history.list(owner[1], other[0], 0, 10));
        assertEquals(EventErrorCode.EVENT_NOT_ACCESSIBLE, e.getErrorCode());

        EventException noAdmin = assertThrows(EventException.class,
                () -> history.list(owner[1], null, 0, 10));
        assertEquals(EventErrorCode.EVENT_NOT_ACCESSIBLE, noAdmin.getErrorCode());
    }

    @Test
    @DisplayName("없는 이벤트와 삭제된 이벤트는 404 이다")
    void 없는_이벤트() {
        long[] e = seedEvent("여름 이벤트");

        EventException missing = assertThrows(EventException.class,
                () -> history.list(999_999_999L, e[0], 0, 10));
        assertEquals(EventErrorCode.EVENT_NOT_FOUND, missing.getErrorCode());

        tem.getEntityManager()
                .createNativeQuery("UPDATE events SET deleted_at = now() WHERE id = ?1")
                .setParameter(1, e[1])
                .executeUpdate();
        tem.clear();
        EventException deleted = assertThrows(EventException.class,
                () -> history.list(e[1], e[0], 0, 10));
        assertEquals(EventErrorCode.EVENT_NOT_FOUND, deleted.getErrorCode());
    }
}
