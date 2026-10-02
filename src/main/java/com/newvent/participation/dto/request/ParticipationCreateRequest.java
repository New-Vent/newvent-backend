package com.newvent.participation.dto.request;

// 사용자가 제출할 값만 받음
public record ParticipationCreateRequest(
        String prediction,
        String phoneNumber,
        Integer pouchIndex
) {
}
