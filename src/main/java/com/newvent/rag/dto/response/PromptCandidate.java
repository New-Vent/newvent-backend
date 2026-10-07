package com.newvent.rag.dto.response;

// 프롬프트 후보 1건. 프론트는 prompt 를 채팅창에 그대로 입력
public record PromptCandidate(Long eventId, String eventTitle, String blockKey,
		String prompt) {

}
