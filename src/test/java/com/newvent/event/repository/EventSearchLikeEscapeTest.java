package com.newvent.event.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.newvent.common.util.LikePattern;
import com.newvent.event.domain.Event;

// 이벤트 제목 검색의 % _ 가 와일드카드가 아니라 글자로 찾아지는지를 <b>실제 PostgreSQL 에서</b> 본다.

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.newvent.common.config.JpaAuditingConfig.class)
class EventSearchLikeEscapeTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T12:00:00+09:00");
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-01T00:00:00+09:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-10-31T23:59:59+09:00");

    @Autowired
    private EventRepository events;

    @Autowired
    private TestEntityManager tem;

    private Long percent, thousand, underscore, other, bang;

    @BeforeEach
    void seed() {
        EntityManager em = tem.getEntityManager();
        Long admin = ((Number) em.createNativeQuery(
                        "INSERT INTO admins (login_id, password_hash, name) VALUES ('escape_admin', 'x', '관리자') RETURNING id")
                .getSingleResult()).longValue();
        // 100% 는 "1000원 쿠폰" 과, a_b 는 "axb 코드" 와 와일드카드로 겹치는 쌍이다
        percent = insert(em, admin, "이스케이프확인 100% 페이백");
        thousand = insert(em, admin, "이스케이프확인 1000원 쿠폰");
        underscore = insert(em, admin, "이스케이프확인 a_b 코드");
        other = insert(em, admin, "이스케이프확인 axb 코드");
        bang = insert(em, admin, "이스케이프확인 느낌표! 이벤트");
    }

    private static Long insert(EntityManager em, Long admin, String title) {
        return ((Number) em.createNativeQuery(
                        "INSERT INTO events (owner_admin_id, title, grade, start_date, end_date, status) "
                        + "VALUES (?1, ?2, 'NORMAL', ?3, ?4, 'PUBLISHED') RETURNING id")
                .setParameter(1, admin)
                .setParameter(2, title)
                .setParameter(3, START)
                .setParameter(4, END)
                .getSingleResult()).longValue();
    }

    private List<Long> publicSearch(String keyword) {
        return events.findPublicEvents(null, LikePattern.contains(keyword), null, NOW, PageRequest.of(0, 50))
                .getContent().stream().map(Event::getId).sorted().toList();
    }

    private List<Long> adminSearch(String keyword) {
        return events.findAdminEvents(LikePattern.contains(keyword), null, null, null, NOW, null, null, PageRequest.of(0, 50))
                .getContent().stream().map(Event::getId).sorted().toList();
    }

    @Test
    @DisplayName("공개 검색: % 는 글자 그대로 — 100% 가 1000원 쿠폰까지 찾지 않는다")
    void 공개_퍼센트는_글자다() {
        assertEquals(List.of(percent), publicSearch("이스케이프확인 100%"));
    }

    @Test
    @DisplayName("공개 검색: _ 는 글자 그대로 — a_b 가 axb 까지 찾지 않는다")
    void 공개_언더스코어는_글자다() {
        assertEquals(List.of(underscore), publicSearch("이스케이프확인 a_b"));
    }

    @Test
    @DisplayName("공개 검색: 이스케이프 문자(!) 도 글자 그대로 찾는다 — 쿼리가 깨지지 않는다")
    void 공개_느낌표도_글자다() {
        assertEquals(List.of(bang), publicSearch("느낌표!"));
    }

    @Test
    @DisplayName("공개 검색: 와일드카드가 없는 일반 검색은 그대로 부분 일치다")
    void 공개_일반_검색은_그대로() {
        assertEquals(List.of(underscore, other), publicSearch("b 코드"));
    }

    @Test
    @DisplayName("관리자 검색: % 는 글자 그대로 — 100% 가 1000원 쿠폰까지 찾지 않는다")
    void 관리자_퍼센트는_글자다() {
        assertEquals(List.of(percent), adminSearch("이스케이프확인 100%"));
    }

    @Test
    @DisplayName("관리자 검색: _ 는 글자 그대로 — a_b 가 axb 까지 찾지 않는다")
    void 관리자_언더스코어는_글자다() {
        assertEquals(List.of(underscore), adminSearch("이스케이프확인 a_b"));
    }

    @Test
    @DisplayName("관리자 검색: 이스케이프 문자(!) 도 글자 그대로 찾는다")
    void 관리자_느낌표도_글자다() {
        assertEquals(List.of(bang), adminSearch("느낌표!"));
    }

    @Test
    @DisplayName("관리자 검색: 와일드카드가 없는 일반 검색은 그대로 부분 일치다")
    void 관리자_일반_검색은_그대로() {
        assertEquals(List.of(underscore, other), adminSearch("b 코드"));
    }

    @Test
    @DisplayName("공개·관리자 모두: % 만 입력하면 제목에 % 가 든 것만 나온다 (전부 나오지 않는다)")
    void 퍼센트만_입력하면_퍼센트_든_제목만() {
        // % 한 글자만 찾는다 — 이 테스트가 심은 것 중에서만 본다 (DB 에 다른 이벤트가 있어도 흔들리지 않게)
        List<Long> mine = List.of(percent, thousand, underscore, other, bang);
        assertEquals(List.of(percent), publicSearch("%").stream().filter(mine::contains).toList());
        assertEquals(List.of(percent), adminSearch("%").stream().filter(mine::contains).toList());
        assertFalse(publicSearch("%").contains(thousand));
    }

    @Test
    @DisplayName("공개·관리자 모두: _ 만 입력하면 제목에 _ 가 든 것만 나온다 (아무 한 글자로 보지 않는다)")
    void 언더스코어만_입력하면_언더스코어_든_제목만() {
        List<Long> mine = List.of(percent, thousand, underscore, other, bang);
        assertEquals(List.of(underscore), publicSearch("_").stream().filter(mine::contains).toList());
        assertEquals(List.of(underscore), adminSearch("_").stream().filter(mine::contains).toList());
    }
}
