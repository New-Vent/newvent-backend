package com.newvent.event.dto.request;

import java.util.Objects;

/**
 * 에디터 직접 편집 시 단일 텍스트 치환 항목.
 *
 * @param index  [data-block] 내부 문서 순서 텍스트 노드 인덱스 (notices 제외, 0부터 시작)
 * @param before 수정 전 텍스트 (불일치 시 409 대응을 위한 안전망)
 * @param after  수정 후 텍스트
 */
public record TextEdit(int index, String before, String after) {

    public TextEdit {
        if (index < 0) {
            throw new IllegalArgumentException("텍스트 인덱스는 0 이상이어야 합니다. 입력: " + index);
        }
        Objects.requireNonNull(before, "before 문구는 필수입니다.");
        Objects.requireNonNull(after, "after 문구는 필수입니다.");
    }
}
