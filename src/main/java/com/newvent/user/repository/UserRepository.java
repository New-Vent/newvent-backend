package com.newvent.user.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // LIKE 의 이스케이프 문자. 검색어 안의 % _ 와 이 문자 자체에는 UserService 가 앞에 이 문자를 붙여서 넘긴다.
    char LIKE_ESCAPE = '!';

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    // 관리자 목록 - 검색어(아이디/이름/이메일 부분 일치)와 등급은 값이 있을 때만 조건에 넣는다
    default Page<User> searchUsers(String keywordPattern, MembershipGrade grade, int page, int size) {
        return findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (keywordPattern != null) {
                filters.add(cb.or(
                        cb.like(cb.lower(root.get("loginId")), keywordPattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("name")), keywordPattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("email")), keywordPattern, LIKE_ESCAPE)));
            }
            if (grade != null) {
                filters.add(cb.equal(root.get("membershipGrade"), grade));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }
}
