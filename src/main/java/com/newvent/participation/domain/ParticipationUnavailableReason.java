package com.newvent.participation.domain;

public enum ParticipationUnavailableReason {
    NOT_STARTED,
    ALREADY_PARTICIPATED,
    INSUFFICIENT_GRADE,

    /**
     * 이벤트에 참여 설정(event_game_configs)이 없거나 둘 이상이다.
     *
     * ★ 사용자가 고칠 수 없는 사유다. 관리자가 설정을 해야 한다.
     *   화면에는 "준비 중" 으로 보여주고 참여 버튼을 숨기는 쪽이 맞다 —
     *   "등급이 부족해요" 처럼 사용자 탓으로 읽히면 안 된다.
     *
     * ★ 이 사유가 없으면 상태 API 가 거짓말을 한다.
     *   설정을 보지 않던 때는 canParticipate=true 를 주는데 POST 는
     *   409 PARTICIPATION409-1 로 거절했다. 화면은 항상 실패하는 버튼을 띄웠다.
     */
    NOT_CONFIGURED
}
