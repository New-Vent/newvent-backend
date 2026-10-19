package com.newvent.event.dto.request;

import com.newvent.event.exception.DirectEditErrorCode;
import com.newvent.event.exception.DirectEditException;

/** 문구 전체에 적용할 서식. null 항목은 유지하며 reset은 문구별 서식만 초기화한다. */
public record TextFormat(String color, Integer size, Boolean bold, Boolean reset) {
    public TextFormat {
        reset = Boolean.TRUE.equals(reset);
        if ((color != null && !color.matches("#[0-9a-fA-F]{6}"))
                || (size != null && (size < 10 || size > 96))) {
            throw new DirectEditException(DirectEditErrorCode.INVALID_TEXT_FORMAT);
        }
    }
}
