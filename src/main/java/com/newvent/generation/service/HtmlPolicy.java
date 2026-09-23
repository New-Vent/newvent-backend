package com.newvent.generation.service;

import java.util.List;

import com.newvent.registry.Block;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.BlockValidator.Failure;

/**
 * "모델 출력을 어떻게 다듬고, 무엇을 합격으로 볼 것인가" 한 벌.
 *
 * ★ RetryService 는 이것만 봅니다. registry 를 직접 import 하지 않습니다.
 *   순서(부르기 → 다듬기 → 판정 → 되먹임)는 RetryService 가 알고,
 *   규칙(무엇을 지우고 무엇을 합격으로 보는가)은 전부 여기로 모읍니다.
 *
 * ★ 왜 정화까지 밖에서 받는가
 *   생성과 수정의 정화 규칙이 **반대**이기 때문입니다.
 *     생성 — data-slot 을 지운다. 빈 문서에서 만드는 거라 있을 이유가 없다.
 *     수정 — data-slot 을 남긴다. 원본에 있던 걸 보존해야 한다.
 *   한쪽 규칙을 양쪽에 쓰면, 제목 한 번 고쳤을 뿐인데 그 이벤트는
 *   기간을 영원히 못 채우게 됩니다(merge 가 블록을 통째로 갈아끼우므로).
 */
public interface HtmlPolicy {

    String clean(String raw);

    List<Failure> validate(String html);


    /** 생성 — 필수 블록 5개가 다 있어야 하고, data-slot 은 지운다 */
    static HtmlPolicy generation() {
        return new HtmlPolicy() {
            @Override
            public String clean(String raw) {
                return BlockValidator.sanitizeGenerated(BlockValidator.extract(raw));
            }

            @Override
            public List<Failure> validate(String html) {
                return BlockValidator.validateGenerated(html);
            }
        };
    }

    /**
     * 수정 — 요청한 블록 하나만 와야 하고, 원본의 data-slot 이 그대로여야 한다.
     *
     */
    static HtmlPolicy edit(Block target, String before) {
        return new HtmlPolicy() {
            @Override
            public String clean(String raw) {
                return BlockValidator.sanitizeEdited(BlockValidator.extract(raw));
            }

            @Override
            public List<Failure> validate(String html) {
                return BlockValidator.validateEdited(target, before, html);
            }
        };
    }
}
