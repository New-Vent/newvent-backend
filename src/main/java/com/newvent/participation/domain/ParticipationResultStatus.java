package com.newvent.participation.domain;

/**
 * 참여 결과 상태 — {@code event_participations.result_data ->> 'status'} 에 들어가는 값.
 *
 * ★ 컬럼이 아니라 JSONB 안의 값이다. 방식마다 결과의 모양이 달라서 그렇다 —
 *   사전예약형은 결과가 아예 없고({@code {}}), 복주머니형은 고른 주머니에 따라 갈린다.
 *   그래서 컬럼으로 빼지 않고 {@code result_data} 에 둔다.
 *
 * ★★ 그래도 이름은 한 곳에 모은다. 전에는 {@code "WON"} 이 소스 일곱 군데에 흩어진
 *   문자열이었다. 오타가 나면 컴파일은 되고 <b>조회만 조용히 비어 돌아온다</b> —
 *   「당첨자가 0명」으로 보이는 게 그 증상이다.
 *
 * ★ 상태가 없는 것도 정상이다. 결과를 내지 않는 방식(사전예약형, resultMode 없는 기본형)은
 *   {@code result_data} 가 빈 객체다. 그래서 enum 에 NONE 을 두지 않는다 — 없는 것은 null 이다.
 */
public enum ParticipationResultStatus {

    /** 당첨. {@code prizeName} 이 같이 저장된다 */
    WON,

    /** 미당첨 */
    LOST,

    /**
     * 결과 대기.
     *
     * ★ 여기서 끝나면 안 된다. 기본형 DELAYED 는 야간 스케줄러(DelayedEventDrawService)가
     *   발표 시각 뒤에 WON·LOST 로 바꾼다. 스포츠 예측형은 정답 채점이 같은 일을 한다.
     */
    PENDING
}
