package com.newvent.event.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.newvent.admin.domain.Admin;
import com.newvent.event.domain.EventTemplate;

/** Docker PostgreSQL이 필요하다. Flyway V14와 검색 조건/소유자 필터를 실제 DB에서 검증한다. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.newvent.common.config.JpaAuditingConfig.class)
class EventTemplateLibraryRepositoryTest {
    @Autowired EventTemplateRepository templates;
    @Autowired TestEntityManager em;

    Admin admin() {
        var id = ((Number) em.getEntityManager().createNativeQuery(
                "INSERT INTO admins(login_id,password_hash,name) VALUES (?1,'x','관리자') RETURNING id")
                .setParameter(1, UUID.randomUUID().toString()).getSingleResult()).longValue();
        return em.getEntityManager().getReference(Admin.class, id);
    }

    @Test
    void ownerAndBuiltinVisibilityAndNullableFilters() {
        Admin owner = admin();
        Admin other = admin();
        String prefix = UUID.randomUUID().toString();
        templates.save(EventTemplate.seed("builtin_" + prefix, prefix + " 기본", null, "<div/>", true));
        templates.save(EventTemplate.custom("own_" + prefix, owner, prefix + " 내 것", null, "<div/>"));
        templates.save(EventTemplate.custom("other_" + prefix, other, prefix + " 남의 것", null, "<div/>"));
        templates.flush();
        var page = PageRequest.of(0, 50);
        assertEquals(2, templates.findLibrary(owner.getId(), "%" + prefix + "%", null, false, page).getTotalElements());
        assertEquals(1, templates.findLibrary(owner.getId(), "%" + prefix + "%", true, false, page).getTotalElements());
        assertEquals(1, templates.findLibrary(owner.getId(), "%" + prefix + "%", false, false, page).getTotalElements());
        assertTrue(templates.findLibrary(owner.getId(), null, null, false, page).getTotalElements() >= 2);
    }

    @Test
    void disabledTemplatesAreOnlyIncludedWhenRequested() {
        Admin owner = admin();
        String name = UUID.randomUUID().toString();
        var t = EventTemplate.custom("off_" + name, owner, name, null, "<div/>");
        t.deactivate();
        templates.saveAndFlush(t);
        var page = PageRequest.of(0, 12);
        assertEquals(0, templates.findLibrary(owner.getId(), name, false, false, page).getTotalElements());
        assertEquals(1, templates.findLibrary(owner.getId(), name, false, true, page).getTotalElements());
    }
}
