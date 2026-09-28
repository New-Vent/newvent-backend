package com.newvent.generation.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.newvent.registry.BlockValidator;
import com.newvent.registry.Slots;

/**
 * 템플릿으로 이벤트의 첫 HTML 을 만든다. **LLM 을 부르지 않는다.**
 *
 * 백지 생성과 여기가 갈리는 지점:
 *
 *   백지 생성   모델이 문서를 만든다 → 검증 → 정화 → 저장
 *   템플릿      사람이 만든 HTML 을 그대로 가져온다 → 슬롯 비우고 저장
 *
 * ★ validateGenerated 를 걸지 않는다
 *   템플릿에는 notices 블록이 이미 있다. 그건 서버 소유라
 *   validateGenerated 가 wrote_notices 로 떨어뜨린다. 모델이 만든 게 아닌데도.
 *
 * ★ sanitizeGenerated 도 걸지 않는다
 *   그걸 걸면 data-slot 이 지워진다. 템플릿의 슬롯은 지켜야 하는 것이다.
 */
@Service
public class TemplateService {

    private final TemplateLoader loader;

    public TemplateService(TemplateLoader loader) {
        this.loader = loader;
    }

    /** 선택 화면용 목록 */
    public List<TemplateLoader.Source> list() {
        return loader.all();
    }

    /**
     * 그 코드의 템플릿이 있나. **생성을 시작하기 전에 묻는다.**
     *
     * ★ 왜 이게 필요해졌나
     *   예전에는 templateCode 가 프론트에서 왔다. 프론트는 목록 API 가 준 코드를
     *   그대로 돌려주니 항상 맞았다.
     *   지금은 `events.template_id` 가 준 코드도 들어온다. 그게 리소스(또는 나중의
     *   event_templates)에 있는지는 **아무도 보장하지 않는다.**
     *
     * ★ 없는 코드를 그냥 두면 initialHtml() 이 워커 스레드에서 터진다.
     *   runSafely 가 잡아서 "문제가 생겼습니다" 로 끝나고, **다시 시도해도 영원히 같다.**
     *   원인은 로그에만 남는다. 그래서 시작 전에 400 으로 돌려보낸다.
     *
     * ★ all() 이 아니라 find() 를 쓴다
     *   all() 은 HTML 5벌을 다 들고 온다. 있는지만 보는 데 그럴 이유가 없고,
     *   DB 로 옮기면 그 차이가 커진다. 그리고 **비활성 템플릿도 찾아야 한다** —
     *   이미 그 템플릿으로 만든 이벤트는 계속 페이지를 만들 수 있어야 한다.
     */
    public boolean exists(String code) {
        return code != null && !code.isBlank() && loader.find(code).isPresent();
    }

    /**
     * 이 템플릿으로 시작할 때 event_versions 1번에 들어갈 HTML.
     *
     * 순서가 중요하다:
     *   ① 재정화 (비내장만)  ② 슬롯 비우기
     *
     * ②를 먼저 하면 재정화가 빈 슬롯을 지울 수 있다. 정화는 항상 먼저다.
     */
    public String initialHtml(String code) {
        TemplateLoader.Source t = loader.find(code).orElseThrow(
                () -> new IllegalArgumentException("없는 템플릿입니다: " + code));

        return Slots.clear(sanitizeIfNeeded(t));
    }

    /**
     * 재정화 — 관리자가 만든 템플릿만.
     *
     * ★ 왜 두 번 정화하나
     *   저장 시 정화는 "모델이 방금 뱉은 걸 믿지 않는다" 이고,
     *   사용 시 정화는 "과거에 통과한 걸 오늘 규칙으로 다시 본다" 이다.
     *   **막는 게 서로 다르다.** 같은 일을 두 번 하는 게 아니다.
     *
     *   관리자 템플릿은 "그때의" 정화 규칙을 통과한 HTML 이다.
     *   (data-slot 보존, button·id 보존).
     *   템플릿은 수명이 길고 여러 이벤트에 퍼지므로 한 번 잘못 들어가면 다 퍼진다.
     *
     * ★ 기본 5종은 안 건다
     *   사람이 쓴 HTML 이고 우리가 보증한다. 그리고 PR 리뷰와
     *   왕복 테스트가 이미 지키고 있다.
     */
    private String sanitizeIfNeeded(TemplateLoader.Source t) {
        if (t.builtin()) return t.html();
        return BlockValidator.sanitizeEdited(t.html());   // 수정용 — 버튼·슬롯·id 를 보존한다
    }
}
