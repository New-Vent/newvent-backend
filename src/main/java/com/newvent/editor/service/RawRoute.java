package com.newvent.editor.service;

/**
 * 라우터가 뱉은 연산 하나. **그대로다. 아직 아무것도 검증되지 않았다.**
 *
 */
public record RawRoute(String op, String target, String content) {}
