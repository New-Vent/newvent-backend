package com.newvent.event.dto.request;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;
import com.newvent.registry.Block;
import com.newvent.registry.Layout;
import com.newvent.registry.Variant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 에디터 직접 편집 요청 DTO.
 *
 * ★ 블록 순서도 여기로 받는다. 새 엔드포인트를 만들지 않은 이유 —
 *   "기준 버전에 무언가를 적용해 새 버전으로 저장한다" 는 모양이 직접 편집과 같고,
 *   소유자 확인 · 종료 잠금 · 기준 버전 검증이 이미 DirectEditService 에 다 있다.
 *   길을 하나 더 내면 그 검사들을 두 벌 유지해야 한다.
 *
 * @param sourceVersionId 기준이 되는 버전의 ID (event_versions.id)
 * @param edits           수정할 문구 목록
 * @param buttonStyle     수정할 CTA 버튼 스타일 (색상, 크기, 모양)
 * @param textStyle       제목 글자 크기 · 색. 비운 항목은 테마를 그대로 따른다
 * @param blockOrder      블록을 세울 순서. 블록 key 목록 (예: ["hero","steps","benefits"])
 * @param layout          페이지 배치 (예: "layout-bento"). 기본으로 되돌리려면 "layout-stack"
 * @param variants        영역 → 모양 (예: {"benefits":"v-benefits-grid"}). 빈 문자열이면 모양 class 를 뗀다
 * @param removeBlocks    직접수정에서 삭제할 블록 key
 * @param removeItems     블록별 삭제 항목. index와 현재 문구를 같이 보내 오래된 화면의 잘못된 삭제를 막는다
 */
public record DirectEditRequest(
        @Schema(description = "지금 에디터에 띄운 버전. 새 버전은 이 버전을 이어서 만든다.")
        @NotNull(message = "기준 버전 ID(sourceVersionId)는 필수입니다.")
        Long sourceVersionId,
        @Schema(description = "고칠 문구 목록. buttonStyle 과 둘 중 하나는 있어야 한다.")
        List<@Valid TextEdit> edits,
        @Valid ButtonStyle buttonStyle,
        @Valid TextStyle textStyle,
        List<String> blockOrder,
        @Schema(description = "페이지 배치 class. 미리보기 응답의 looks.layouts 에 있는 이름")
        String layout,
        @Schema(description = "영역(data-block) → 모양 class. 미리보기 응답의 looks.blocks[영역].variants 에 있는 이름")
        Map<String, String> variants,
        List<String> removeBlocks,
        Map<String, List<ItemRemoval>> removeItems) {

    public record ItemRemoval(int index, String before) {}

    public DirectEditRequest {
        if ((edits == null || edits.isEmpty()) && buttonStyle == null
                && (textStyle == null || textStyle.isEmpty())
                && (blockOrder == null || blockOrder.isEmpty())
                && layout == null && (variants == null || variants.isEmpty())
                && (removeBlocks == null || removeBlocks.isEmpty())
                && (removeItems == null || removeItems.isEmpty())) {
            throw new DirectEditException(DirectEditErrorCode.EMPTY_EDIT_REQUEST);
        }
        // ★ 이름은 여기서 거른다 (순서와 같다). 붙일 수 있는지는 페이지를 봐야 알아서 서비스가 본다
        if (layout != null && Layout.find(layout).isEmpty()) {
            throw new DirectEditException(DirectEditErrorCode.INVALID_LOOK);
        }
        if (variants != null) {
            variants.forEach((k, v) -> {
                if (Block.find(k).isEmpty() || v == null || (!v.isEmpty() && Variant.find(v).isEmpty())) {
                    throw new DirectEditException(DirectEditErrorCode.INVALID_LOOK);
                }
            });
        }
        // ★ 여기서 바로 걸러 낸다. 서비스까지 들고 가서 조용히 무시하면
        //   프론트는 오타를 낸 줄 모르고, 관리자는 왜 안 움직이는지 모른다
        if (blockOrder != null) {
            for (String key : blockOrder) {
                if (Block.find(key).isEmpty()) {       // find 는 null · 공백을 이미 처리한다
                    throw new DirectEditException(DirectEditErrorCode.INVALID_BLOCK_ORDER);
                }
            }
        }
        if (removeBlocks != null) for (String key : removeBlocks) {
            if (Block.find(key).isEmpty()) throw new DirectEditException(DirectEditErrorCode.INVALID_BLOCK_ORDER);
        }
        if (removeItems != null) removeItems.forEach((key, items) -> {
            if (Block.find(key).isEmpty() || items == null || items.stream().anyMatch(i -> i == null || i.index() < 0 || i.before() == null))
                throw new DirectEditException(DirectEditErrorCode.INVALID_BLOCK_ORDER);
        });
    }

    /** blockOrder 를 쓰지 않는 기존 호출을 위한 것. */
    public DirectEditRequest(Long sourceVersionId, List<TextEdit> edits, ButtonStyle buttonStyle) {
        this(sourceVersionId, edits, buttonStyle, null);
    }

    /** 배치 · 모양을 쓰지 않는 기존 호출을 위한 것. */
    public DirectEditRequest(Long sourceVersionId, List<TextEdit> edits, ButtonStyle buttonStyle,
                             List<String> blockOrder) {
        this(sourceVersionId, edits, buttonStyle, null, blockOrder, null, null, null, null);
    }

    /**
     * textStyle 을 쓰지 않는 기존 호출을 위한 것.
     *
     * ★★ 구성요소를 record 가운데에 끼웠으므로 **6인자 호출이 전부 깨진다.**
     *   한 번 겪었다 — DirectEditServiceTest 7곳이 컴파일에서 터졌다.
     *   옛 자리 그대로 받는 생성자를 남겨 두면 부르는 쪽을 안 고쳐도 된다.
     */
    public DirectEditRequest(Long sourceVersionId, List<TextEdit> edits, ButtonStyle buttonStyle,
                             List<String> blockOrder, String layout, Map<String, String> variants) {
        this(sourceVersionId, edits, buttonStyle, null, blockOrder, layout, variants, null, null);
    }

    /** 배치 · 모양 중 고친 것이 있는가 */
    public boolean hasLooks() {
        return layout != null || (variants != null && !variants.isEmpty());
    }

    /** 배치 · 모양 말고 고친 것이 있는가 — 문구 · 버튼 스타일 · 블록 순서 */
    public boolean hasOtherEdits() {
        return (edits != null && !edits.isEmpty()) || buttonStyle != null
                || (textStyle != null && !textStyle.isEmpty())
                || (blockOrder != null && !blockOrder.isEmpty())
                || (removeBlocks != null && !removeBlocks.isEmpty())
                || (removeItems != null && !removeItems.isEmpty());
    }

    /** 고른 배치. 안 고쳤으면 null */
    public Layout layoutPick() {
        return layout == null ? null : Layout.find(layout).orElseThrow();
    }

    /** 영역 → 고른 모양. 값이 null 이면 모양을 뗀다 */
    public Map<Block, Variant> variantPicks() {
        Map<Block, Variant> out = new LinkedHashMap<>();
        // 빈 문자열 = 모양을 뗀다 → null
        if (variants != null) variants.forEach((k, v) -> out.put(Block.of(k.trim()), Variant.find(v).orElse(null)));
        return out;
    }

    /**
     * 순서 목록을 Block 으로. 비어 있으면 빈 목록이다.
     *
     * ★ 중복과 고정 블록(유의사항 · 참여버튼)은 여기서 안 거른다.
     *   PageShell.applyOrder 가 거른다 — 거르는 규칙을 두 곳에 적지 않는다.
     */
    public List<Block> blocks() {
        if (blockOrder == null) return List.of();
        return blockOrder.stream()
                .map(k -> Block.of(k.trim()))
                .toList();
    }
}
