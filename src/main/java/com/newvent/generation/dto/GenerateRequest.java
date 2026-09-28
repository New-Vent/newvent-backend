package com.newvent.generation.dto;

import jakarta.validation.constraints.Size;

/**
 * 생성 요청 본문.
 *
 * ★ 제목·기간·참여링크가 빠졌다 — 이제 `events` 에서 읽는다
 */
public record GenerateRequest(

        @Size(max = 50, message = "템플릿 코드가 너무 깁니다.")
        String templateCode,

        @Size(max = 500, message = "요청이 너무 깁니다. 500자 이내로 줄여 주세요.")
        String requestText) {}
