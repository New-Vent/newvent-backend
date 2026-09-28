package com.newvent.editor.service;

import com.newvent.common.exception.code.ErrorCode;
import com.newvent.registry.Block;

/**
 * Gate 결과.
 *
 * ★ Run 의 op 은 원래 op 과 다를 수 있다.
 *   Add 를 필수 영역에 넣으면 EDIT 이 된다. 원래 op 가 뭔지는 여기서 잃어버림.
 *
 * ★ Reject 의 message 는 사용자에게 그대로 보여줄 문장
 *   코드만으로는 왜 거절됐는지 설명이 안됨.
 *
 * ★ AskBack 은 모델을 부르지 않음.
 *   항목 값은 관리자가 정함. 모델이 채우게 두면 지어낼 수 있음.
 */
public sealed interface Decision permits Decision.Run, Decision.AskBack, Decision.Reject{

	// 그대로 실행
	record Run(Block block, Op op, String content) implements Decision {}

	// 되묻기. 질문은 사용자에게 그대로 보여줄 문장
	record AskBack(String question) implements Decision {}

	// 거부. 메시지는 사용자에게 그대로 보여줄 문장
	record Reject(ErrorCode code, String message) implements Decision {}
}
