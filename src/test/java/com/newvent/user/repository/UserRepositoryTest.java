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

    // 시드 회원(V3)이 DB 에 이미 있으므로, 검색 테스트는 다른 데이터와 겹치지 않는 고유한 글자를 쓴다.
    private User saved(String loginId, String name, String email, MembershipGrade grade) {
        return userRepository.saveAndFlush(new User(loginId, "hash", name, email, null, 50000, grade));
    }

    @Test
    @DisplayName("검색어는 아이디·이름·이메일 어디에 있어도 찾고 대소문자를 구분하지 않는다")
    void 검색_아이디_이름_이메일() {
        saved("zqx1login", "가나다", "zqx1a@test.com", MembershipGrade.NORMAL);
        saved("other1", "ZQX2이름", "zqx2a@test.com", MembershipGrade.NORMAL);
        saved("other2", "라마바", "other2@zqx3mail.com", MembershipGrade.NORMAL);
        saved("other3", "사아자", "other3@test.com", MembershipGrade.NORMAL);

        assertEquals(1, userRepository.searchUsers("%zqx1login%", null, 0, 10).getTotalElements());
        assertEquals(1, userRepository.searchUsers("%zqx2이름%", null, 0, 10).getTotalElements());
        assertEquals(1, userRepository.searchUsers("%zqx3mail%", null, 0, 10).getTotalElements());
        assertEquals(0, userRepository.searchUsers("%zqx9none%", null, 0, 10).getTotalElements());
    }

    @Test
    @DisplayName("검색어의 % 는 이스케이프하면 글자 그대로 찾는다 (와일드카드로 쓰이지 않는다)")
    void 검색_와일드카드_이스케이프() {
        saved("wild_pct", "퍼센트A", "zqxpct@test.com", MembershipGrade.NORMAL);
        saved("wild_pcx", "퍼센트B", "zqxpcx@test.com", MembershipGrade.NORMAL);
        saved("zqx%name", "퍼센트C", "zqxpcname@test.com", MembershipGrade.NORMAL);

        // "zqx%" 를 이스케이프 없이 쓰면 zqx 로 시작하는 모두가 걸리지만, 이스케이프하면 % 가 든 하나만 걸린다
        assertEquals(1, userRepository.searchUsers("%zqx!%name%", null, 0, 10).getTotalElements());
        assertEquals(3, userRepository.searchUsers("%zqx%", null, 0, 10).getTotalElements());
    }

    @Test
    @DisplayName("등급 필터는 해당 등급만 남긴다")
    void 검색_등급필터() {
        saved("zqxg1", "등급1", "zqxg1@test.com", MembershipGrade.NORMAL);
        saved("zqxg2", "등급2", "zqxg2@test.com", MembershipGrade.EXCELLENT);
        saved("zqxg3", "등급3", "zqxg3@test.com", MembershipGrade.BEST);

        assertEquals(3, userRepository.searchUsers("%zqxg%", null, 0, 10).getTotalElements());
        var excellent = userRepository.searchUsers("%zqxg%", MembershipGrade.EXCELLENT, 0, 10);
        assertEquals(1, excellent.getTotalElements());
        assertEquals("zqxg2", excellent.getContent().get(0).getLoginId());
    }

    @Test
    @DisplayName("조건이 없으면 전체를 최근 가입순(id 내림차순)으로 페이지 단위로 돌려준다")
    void 검색_조건없음_최근순_페이지() {
        User first = saved("zqxp1", "페이지1", "zqxp1@test.com", MembershipGrade.NORMAL);
        saved("zqxp2", "페이지2", "zqxp2@test.com", MembershipGrade.NORMAL);
        User third = saved("zqxp3", "페이지3", "zqxp3@test.com", MembershipGrade.NORMAL);

        var all = userRepository.searchUsers(null, null, 0, 2);
        assertEquals(2, all.getContent().size());
        assertEquals(third.getId(), all.getContent().get(0).getId());
        assertTrue(all.getTotalElements() >= 3);

        var onlyMine = userRepository.searchUsers("%zqxp%", null, 0, 2);
        assertEquals(3, onlyMine.getTotalElements());
        assertEquals(2, onlyMine.getTotalPages());
        assertEquals(first.getId(), userRepository.searchUsers("%zqxp%", null, 1, 2).getContent().get(0).getId());
    }
}
