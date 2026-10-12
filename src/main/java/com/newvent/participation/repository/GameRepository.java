package com.newvent.participation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.newvent.participation.domain.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

    // 관리자가 보낸 gameCode 로 찾는다. 비활성 방식도 찾아 와서 검증기가 거부하게 한다 —
    // "없는 방식" 과 "비활성 방식" 을 같은 코드(PARTICIPATION409-2)로 답하기 위해서다.
    Optional<Game> findByCode(String code);
}
