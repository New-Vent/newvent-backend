package com.newvent.generation.service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 완성된 HTML 을 이벤트의 버전으로 남기는 자리.
 *
 * ★ 저장 결과로 id 를 돌려준다
 *   수정은 "어느 버전을 고쳐서 나온 버전" 이다. 그걸 표현하려면
 *   {@code event_versions.source_version_id} 에 넣을 id 가 필요하고,
 *   그 id 는 저장한 쪽만 안다.
 *
 * ★ 읽기도 id 를 같이 준다 — {@link Snapshot}
 *   자동 저장 버전은 {@code is_checkpoint = false} 라서 저장 지점 목록 API 에 안 나온다.
 *   프론트가 "지금 화면이 어느 버전인가" 를 알 수 있는 경로가
 *   생성 폴링 응답과 미리보기 응답뿐이다. 그 id 가 이후 수정 요청의 기준이 된다.
 *
 * ★ sourceNote 를 뺐다
 *   {@code event_versions} 에 그 문자열을 넣을 컬럼이 없다. 인메모리 구현도 무시하고 있었다.
 *   "템플릿 T1" / "백지 생성" 은 로그로 남긴다. 저장이 필요해지면 컬럼이 먼저 생겨야 한다.
 */
public interface VersionStore {

    /**
     * 저장된 버전의 신분.
     *
     * ★ versionNo 는 이벤트 안에서의 순번이다. id 와 다르다.
     *   화면에 "v3" 으로 보이는 건 versionNo 이고, 참조에 쓰는 건 id 다.
     */
    record Saved(Long versionId, int versionNo) {}

    /**
     * 마지막 버전의 <b>신분과 내용을 한 번에</b>.
     *
     * ★ 왜 합쳤나 — 예전에는 latest(HTML) 와 latestVersion(신분) 이 따로 있었다
     *   부르는 쪽은 늘 둘 다 필요하다. 미리보기는 HTML 과 versionId 를 같이 내려주고,
     *   수정은 기준 HTML 과 sourceVersionId 를 같이 쓴다.
     *
     *   따로 부르면 같은 쿼리가 두 번 나가고, <b>두 호출 사이에 새 버전이 저장되면
     *   짝이 어긋난다</b> — HTML 은 v5 인데 id 는 v6 을 가리키는 응답이 나갈 수 있다.
     *   컨트롤러가 트랜잭션을 열어 줘도 READ COMMITTED 라 막히지 않는다.
     *
     *   그리고 latestVersion() 은 지금 부르는 데가 없다. EditService 가 쓸 예정이었는데,
     *   그쪽도 HTML 을 같이 필요로 하니 이 하나로 끝난다.
     */
    record Snapshot(Long versionId, int versionNo, String html) {}

    /**
     * 새 버전으로 남긴다.
     *
     * @param sourceVersionId 이 버전이 <b>어느 버전을 고쳐서</b> 나왔나.
     *                        수정이면 기준이 된 버전의 id, <b>생성이면 null</b>.
     *                        생성은 백지에서 다시 만드는 것이라 고친 원본이 없다 —
     *                        두 번째 생성이어도 null 이 맞다.
     */
    Saved save(Long eventId, String html, Long sourceVersionId);

    /** 마지막 버전. 없으면 빈 값 */
    Optional<Snapshot> latest(Long eventId);

    /**
     * 특정 버전의 HTML.
     *
     * ★ 왜 필요한가 — 수정이 항상 최신을 고치는 게 아니다
     *   관리자가 버전 이력에서 v2 를 골라 "이 버전 기준으로 수정" 을 누르면,
     *   최신이 v5 여도 기준은 v2 다. 새 버전은 v6 이 되고 source_version_id 는 v2 를 가리킨다.
     *   version_no 는 여전히 max+1 이라 충돌하지 않는다 — 버전 트리에 가지가 생길 뿐이다.
     *
     * ★ <b>체크포인트 제약을 걸지 않는다</b>
     *   저장 지점만 고르게 할지는 부르는 쪽(수정 서비스)의 정책이다.
     *   평소 수정은 체크포인트가 아닌 최신 자동저장 버전을 기준으로 한다.
     *
     * @return 그 이벤트의 버전이 아니면 빈 값
     */
    Optional<String> htmlOf(Long eventId, Long versionId);

    /**
     * DB 를 띄우지 않는 테스트용. <b>빈이 아니다 — 테스트가 직접 만든다.</b>
     *
     * ★ 예전에는 @Component 였다. {@link JpaVersionStore} 가 생겼으므로 떼야 한다.
     *   안 떼면 VersionStore 빈이 둘이 되어 주입이 실패하고 스프링 컨텍스트가 안 뜬다.
     * ★ 서버가 죽으면 다 사라진다. 여기서 매기는 번호는 진짜 version_no 가 아니다.
     */
    class InMemory implements VersionStore {

        private final Map<Long, Snapshot> lastSaved = new ConcurrentHashMap<>();
        private final Map<Long, String> htmlById = new ConcurrentHashMap<>();
        private final Map<Long, Long> eventOfVersion = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Saved save(Long eventId, String html, Long sourceVersionId) {
            Snapshot prev = lastSaved.get(eventId);
            Snapshot now = new Snapshot(ids.incrementAndGet(),
                    prev == null ? 1 : prev.versionNo() + 1, html);

            lastSaved.put(eventId, now);
            htmlById.put(now.versionId(), html);
            eventOfVersion.put(now.versionId(), eventId);
            return new Saved(now.versionId(), now.versionNo());
        }

        @Override
        public Optional<Snapshot> latest(Long eventId) {
            return Optional.ofNullable(lastSaved.get(eventId));
        }

        @Override
        public Optional<String> htmlOf(Long eventId, Long versionId) {
            if (!Objects.equals(eventOfVersion.get(versionId), eventId)) {
                return Optional.empty();   // 남의 이벤트 버전은 안 준다
            }
            return Optional.ofNullable(htmlById.get(versionId));
        }
    }
}
