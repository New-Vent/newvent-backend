package com.newvent.event.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;
import com.newvent.registry.Block;

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
 * @param blockOrder      블록을 세울 순서. 블록 key 목록 (예: ["hero","steps","benefits"])
 */
public record DirectEditRequest(
        @NotNull(message = "기준 버전 ID(sourceVersionId)는 필수입니다.")
        Long sourceVersionId,
        List<@Valid TextEdit> edits,
        @Valid ButtonStyle buttonStyle,
        List<String> blockOrder) {

    public DirectEditRequest {
        if ((edits == null || edits.isEmpty()) && buttonStyle == null
                && (blockOrder == null || blockOrder.isEmpty())) {
            throw new DirectEditException(DirectEditErrorCode.EMPTY_EDIT_REQUEST);
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
    }

    /** blockOrder 를 쓰지 않는 기존 호출을 위한 것. */
    public DirectEditRequest(Long sourceVersionId, List<TextEdit> edits, ButtonStyle buttonStyle) {
        this(sourceVersionId, edits, buttonStyle, null);
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
