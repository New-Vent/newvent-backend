package com.newvent.editor.service;

import java.util.regex.Pattern;

/**
 * 직전 요청의 <b>"어떻게"</b> 가 생략된 후속 요청인가. — 모델을 부르지 않는다. 글자만 본다.
 *
 * ★ 왜 필요한가 (실측)
 *   "참여방법을 반말로 바꿔줘" 다음에 "자주 묻는 질문도 그렇게 바꿔줘" 를 보내면,
 *   수정 호출에 가는 글자는 뒤 문장뿐이라 <b>반말</b> 이 어디에도 없다. 모델은 추측하고,
 *   자주 묻는 질문의 답이 "아니요" 로 바뀌었다.
 *   "일정 색상을 초록색으로" 다음의 "참여방법도 바꿔줘" 도 같다 — 초록색이 안 간다.
 *
 * ★ 판정기는 이런 요청을 <b>일부러 통과시킨다</b>
 *   PromptBuilder.editJudge() — "앞선 질문에 대한 짧은 대답도 true 다".
 *   통과시켜 놓고 받아 줄 데가 없던 것이 문제였다.
 *
 * ★ 넓게 잡지 않는다
 *   맞으면 직전 요청 한 줄이 프롬프트에 붙을 뿐이고, 틀려도 모델이 안 쓰면 그만이다.
 *   다만 "속도 바꿔줘" 처럼 낱말 안의 '도' 가 걸릴 수 있어 서술어를 같이 본다.
 */
final class FollowUp {

    /** 직전 요청을 가리키는 말 */
    private static final Pattern POINTS_BACK = Pattern.compile(
            "(그렇게|그런 ?식으로|그처럼|저렇게|똑같이|같은 ?식으로|마찬가지로|동일하게)");

    /** "참여방법도 바꿔줘" — 대상에 '도' 가 붙고 어떻게가 없다 */
    private static final Pattern ALSO = Pattern.compile(
            "도\\s*(바꿔|고쳐|수정|적용|해 ?줘|해주세요)");

    private FollowUp() {}

    static boolean needsPrevious(String requestText) {
        if (requestText == null || requestText.isBlank()) {
            return false;
        }
        String t = requestText.strip();
        return POINTS_BACK.matcher(t).find() || ALSO.matcher(t).find();
    }
}
