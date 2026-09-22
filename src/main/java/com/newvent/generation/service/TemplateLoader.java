package com.newvent.generation.service;

import java.util.List;
import java.util.Optional;

/**
 * 템플릿을 어디서 가져오는가. **여기가 DB 로 갈아끼우는 지점이다.**
 *
 *   지금    ResourceTemplateLoader   classpath 의 templates/ 에서 읽는다
 *   나중    DbTemplateLoader         event_templates 테이블에서 읽는다
 *
 * TemplateService 는 이 인터페이스만 본다. 구현이 바뀌어도 한 줄도 안 바뀐다.
 *
 * ★ DB 로 가도 resources 파일은 남는다
 *   기본 5종을 DB 에 넣어주는 시드가 어디선가 읽어야 하고,
 *   왕복 테스트는 DB 없이 돌아야 하기 때문이다.
 */
public interface TemplateLoader {

    /**
     * 템플릿 한 벌. 파일에서 왔든 DB 에서 왔든 같은 모양이다.
     *
     * @param code        식별자. 파일명(확장자 제외)과 같다
     * @param description **RAG 템플릿 추천의 임베딩 대상.**
     *                    HTML 전문을 임베딩하면 클래스명·태그가 노이즈가 돼서
     *                    의미 검색이 망가진다. 설명 문장을 써야 한다
     * @param builtin     기본 5종이면 true. 관리자가 만든 것이면 false —
     *                    이 값이 재정화 여부를 가른다 (TemplateService 참고)
     */
    record Source(
            String code,
            String name,
            String description,
            String thumbnailPath,
            String html,
            boolean builtin) {}

    /** 선택 화면에 보여줄 목록. HTML 까지 들고 오므로 화면용으로는 무겁다 — 필요하면 나중에 메타만 따로 뺀다. */
    List<Source> all();

    Optional<Source> find(String code);
}
