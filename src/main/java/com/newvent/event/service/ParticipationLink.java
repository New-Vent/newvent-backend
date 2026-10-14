package com.newvent.event.service;

/**
 * CTA 버튼이 가리킬 참여 화면 주소. <b>cta-link 슬롯에 들어가는 값이다.</b>
 *
 * ★ 왜 참여 API 주소가 아닌가
 *   참여는 {@code POST /api/users/me/events/{id}/participations} 이고 <b>사용자 토큰이 필수</b>다.
 *   그런데 이 링크가 박히는 곳은 공유 링크 페이지(`GET /e/{id}`)로, 로그인이 없는 화면이다.
 *   버튼에 API 주소를 넣으면 누구나 401 을 받는다. 그래서 <b>참여할 수 있는 화면</b>으로 보낸다 —
 *   거기서 로그인 상태를 보고 참여 폼이나 로그인 안내를 띄우는 건 프론트의 일이다.
 *
 * ★ 왜 상대 경로인가 — 환경변수로 도메인을 들고 다니지 않아도 된다
 *   배포에서 nginx 가 같은 오리진에 둘을 나란히 얹는다 (docs/infra.md):
 *       /e/   → proxy_pass 127.0.0.1:8080   (이 페이지)
 *       /     → try_files … /index.html     (사용자 SPA)
 *   그래서 `/events/3` 은 같은 호스트의 SPA 로 풀린다. 절대 주소를 설정에 두면
 *   프로파일마다 값이 갈리고, 틀려도 조용히 틀린다 — 버튼을 눌러야 알 수 있다.
 *
 * ★ 로컬 개발에서는 이 링크가 맞지 않는다. 백엔드가 8080, 프론트가 5173 이라
 *   `localhost:8080/events/3` 은 404 다. 로컬에서 `/e/{id}` 는 프론트가 쓰지 않는 화면이고
 *   (프론트는 publishedHtml 을 직접 받아 그린다) 배포에서만 실제로 열리는 경로다.
 *
 * ★ 경로 모양은 프론트 라우터와 맞춰야 한다 — frontend/src/user/main.js 의
 *   {@code PATH.event = (id) => `/events/${id}`}. 한쪽을 바꾸면 다른 쪽도 바꾼다.
 */
public final class ParticipationLink {

    private ParticipationLink() {}

    /** 이벤트 id 가 없으면 null — {@code Slots.fill} 이 null 을 건너뛴다 (슬롯은 빈 채로 남는다) */
    public static String of(Long eventId) {
        return eventId == null ? null : "/events/" + eventId;
    }
}
