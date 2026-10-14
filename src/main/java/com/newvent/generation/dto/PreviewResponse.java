package com.newvent.generation.dto;

import java.util.List;

import com.newvent.event.service.DirectEditor;
import com.newvent.generation.service.GenerationService;

/**
 *
 * ★ 슬롯은 채워진 상태다
 *   저장본에는 기간·참여링크가 비어 있고, 보여줄 때 Slots.fill() 이 채운다.
 *
 * ★ 어느 버전인지 같이 준다
 *   폴링을 놓쳐도 화면을 새로 열면 이쪽으로 복구된다.
 * ★ editableTexts 는 직접 편집의 기준 순서다
 *   프론트는 period 슬롯과 notices 블록을 빼고 이 목록의 index/before 를 그대로 보낸다.
 *   미리보기의 period 텍스트는 서버가 채우지만 버전 HTML 에는 없으므로 목록에서 제외한다.
 */
public record PreviewResponse(String html, Long versionId, Integer versionNo,
                              List<DirectEditor.EditableText> editableTexts) {

    public static PreviewResponse of(GenerationService.Rendered rendered) {
        return of(rendered, rendered.html());
    }

    /**
     * @param document 내보낼 HTML — 완전한 문서(PublicEventService.pageOf)
     *
     * ★ editableTexts 는 문서가 아니라 조각에서 뽑는다. 직접 편집은 저장된 조각에 거는 것이라
     *   index 가 조각 기준이어야 한다 (문서에는 &lt;title&gt; · 보여 줄 때 채운 글자가 더 있다)
     */
    public static PreviewResponse of(GenerationService.Rendered rendered, String document) {
        return new PreviewResponse(
                document, rendered.versionId(), rendered.versionNo(),
                DirectEditor.editableTexts(rendered.html()));
    }
}
