package com.newvent.user.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.newvent.common.config.JpaAuditingConfig;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;

// PostgreSQL 전용 스키마(JSONB, CHECK 등)라 내장 DB로 교체하지 않고 실제 datasource(docker-compose)를 그대로 쓴다.
// @DataJpaTest는 일반 @Configuration 빈을 스캔하지 않아 JpaAuditingConfig를 직접 import해야 @CreatedDate created_at이 채워진다 (안 하면 NOT NULL 제약 위반으로 저장이 깨진다).
@DataJpaTest
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("저장한 회원을 다시 조회하면 모든 필드가 그대로 복원된다 (membership_grade CHECK 통과 포함)")
    void 저장하고_조회하면_필드가_그대로다() {
        User user = new User(
                "repotest01", "hash", "저장소테스트", "repotest01@test.com", "010-0000-0000", 70000,
                MembershipGrade.EXCELLENT);

        User saved = userRepository.saveAndFlush(user);
        User found = userRepository.findById(saved.getId()).orElseThrow();

        assertEquals("repotest01", found.getLoginId());
        assertEquals(MembershipGrade.EXCELLENT, found.getMembershipGrade());
        assertEquals(70000, found.getPlan());
    }

    @Test
    @DisplayName("existsByLoginId/existsByEmail은 저장 여부를 정확히 판별한다")
    void existsBy_중복판별() {
        userRepository.saveAndFlush(
                new User("repotest02", "hash", "이름", "repotest02@test.com", null, 50000, MembershipGrade.NORMAL));

        assertTrue(userRepository.existsByLoginId("repotest02"));
        assertTrue(userRepository.existsByEmail("repotest02@test.com"));
        assertFalse(userRepository.existsByLoginId("없는아이디"));
        assertFalse(userRepository.existsByEmail("없는이메일@test.com"));
    }

    @Test
    @DisplayName("login_id가 중복되면 DB의 UNIQUE 제약이 저장을 막는다")
    void 로그인ID_유니크제약() {
        userRepository.saveAndFlush(
                new User("repotest03", "hash", "이름", "repotest03a@test.com", null, 50000, MembershipGrade.NORMAL));

        User duplicate =
                new User("repotest03", "hash", "이름2", "repotest03b@test.com", null, 50000, MembershipGrade.NORMAL);

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicate));
    }

    @Test
    @DisplayName("email이 중복되면 DB의 UNIQUE 제약이 저장을 막는다")
    void 이메일_유니크제약() {
        userRepository.saveAndFlush(
                new User("repotest04a", "hash", "이름", "repotest04@test.com", null, 50000, MembershipGrade.NORMAL));

        User duplicate =
                new User("repotest04b", "hash", "이름2", "repotest04@test.com", null, 50000, MembershipGrade.NORMAL);

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicate));
    }

    @Test
    @DisplayName("existsBy...IgnoreCase 는 대소문자가 달라도 저장된 아이디·이메일을 찾는다")
    void existsBy_대소문자무시() {
        userRepository.saveAndFlush(
                new User("CaseUser", "hash", "이름", "CaseUser@Test.com", null, 50000, MembershipGrade.NORMAL));

        assertTrue(userRepository.existsByLoginIdIgnoreCase("caseuser"));
        assertTrue(userRepository.existsByLoginIdIgnoreCase("CASEUSER"));
        assertTrue(userRepository.existsByEmailIgnoreCase("caseuser@test.com"));
        assertFalse(userRepository.existsByLoginIdIgnoreCase("caseuser2"));
        // 로그인용 조회는 지금처럼 대소문자를 구분한다
        assertFalse(userRepository.findByLoginId("caseuser").isPresent());
        assertTrue(userRepository.findByLoginId("CaseUser").isPresent());
    }

    @Test
    @DisplayName("login_id 가 대소문자만 달라도 DB 의 lower(login_id) 유니크 인덱스가 저장을 막는다")
    void 로그인ID_대소문자_유니크인덱스() {
        userRepository.saveAndFlush(
                new User("casedb01", "hash", "이름", "casedb01a@test.com", null, 50000, MembershipGrade.NORMAL));

        User variant =
                new User("CASEDB01", "hash", "이름2", "casedb01b@test.com", null, 50000, MembershipGrade.NORMAL);

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(variant));
    }

    @Test
    @DisplayName("email 이 대소문자만 달라도 DB 의 lower(email) 유니크 인덱스가 저장을 막는다")
    void 이메일_대소문자_유니크인덱스() {
        userRepository.saveAndFlush(
                new User("casedb02a", "hash", "이름", "casedb02@test.com", null, 50000, MembershipGrade.NORMAL));

        User variant =
                new User("casedb02b", "hash", "이름2", "CASEDB02@Test.com", null, 50000, MembershipGrade.NORMAL);

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(variant));
    }

    @Test
    @DisplayName("시드 회원(user01)과 대소문자만 다른 USER01 도 DB 가 막는다")
    void 시드회원과_대소문자_중복() {
        User variant =
                new User("USER01", "hash", "이름", "other-user01@test.com", null, 50000, MembershipGrade.NORMAL);

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(variant));
    }
}
