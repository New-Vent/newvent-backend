package com.newvent.registry;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 정화가 **공백을 건드리는지** 만 본다.
 *
 * ★ 왜 이 테스트가 필요한가
 *   Bedrock 스모크에서 "혜택 문구를 짧게" 한 마디에 diff 가 42줄 → 66줄로 번졌다.
 *   내용은 단어 몇 개만 바뀌었는데 블록 전체가 다시 들여쓰기된 것처럼 보였다.
 *
 *   들여쓰기가 매번 바뀌면 두 가지가 깨진다.
 *     ① 버전 이력을 사람이 못 읽는다 — 한 단어 수정이 블록 교체로 보인다.
 *     ② 직접 편집과 채팅 수정이 겹쳤을 때 무엇이 실제로 달라졌는지 판정할 수 없다.
 *        직접 편집은 원래 공백을 유지하는데 채팅 수정만 재정렬하면,
 *        코드로도 사람으로도 두 변경을 구분할 방법이 없다.
 *
 * ★ 이 테스트가 무엇을 확정하는가
 *   초록불이면 정화는 결백하고, 그 재정렬은 **모델이 그렇게 써서 보낸 것**이다.
 *   그러면 고칠 대상은 정화가 아니라 프롬프트이거나, 아예 문제가 아니다.
 *
 *   빨간불이면 BlockValidator.clean() 의 prettyPrint(false) 가
 *   지금 쓰는 jsoup(1.18.1)에서 안 먹는다는 뜻이다. 그건 한 줄로 고칠 수 있다.
 *
 * ★ 왜 문자열 전체를 assertEquals 하지 않는가
 *   정화는 속성 순서나 실체참조 표기를 바꿀 수 있고 그건 정상이다.
 *   재정렬의 **지문** 두 개만 본다 — 들여쓰기 폭과, 텍스트가 자기 줄로 빠지는지.
 */
class SanitizeWhitespaceTest {

    /**
     * 템플릿 1 의 혜택 블록에서 그대로 떠온 모양이다.
     * 들여쓰기가 8칸이고, 텍스트는 태그와 같은 줄에 있다.
     */
    private static final String 원본 = """
            <section class="ev-block block-benefits" data-block="benefits">
                    <div class="block-header">
                      <div class="sub-label">Event Rewards</div>
                      <h2 class="title">승리의 기쁨을 나눌 응원 선물</h2>
                    </div>
                    <div class="benefits-list">
                      <div class="benefit-card">
                        <div class="benefit-icon">⚽</div>
                        <div class="benefit-name">국가대표 공식 유니폼</div>
                      </div>
                    </div>
                  </section>""";

    @Test
    @DisplayName("① 텍스트가 자기 줄로 빠지지 않는다")
    void 텍스트는_태그와_같은_줄에_남는다() {
        String out = BlockValidator.sanitizeEdited(원본);

        // ★ jsoup 의 pretty-print 는 블록 요소 안의 텍스트를 반드시 자기 줄로 내린다.
        //   <div class="benefit-icon">\n  ⚽\n</div> 가 그 지문이다.
        assertTrue(out.contains("<div class=\"sub-label\">Event Rewards</div>"),
                "sub-label 의 텍스트가 자기 줄로 빠졌습니다 — pretty-print 가 켜져 있습니다.\n"
                + "─── 출력 ───\n" + out);

        assertTrue(out.contains("<div class=\"benefit-icon\">⚽</div>"),
                "benefit-icon 의 텍스트가 자기 줄로 빠졌습니다 — pretty-print 가 켜져 있습니다.\n"
                + "─── 출력 ───\n" + out);
    }

    @Test
    @DisplayName("② 들여쓰기 폭이 줄어들지 않는다")
    void 들여쓰기가_보존된다() {
        String out = BlockValidator.sanitizeEdited(원본);

        // ★ pretty-print 는 indentAmount 기본값 1 로 다시 쓴다. 8칸이 1칸이 된다.
        assertTrue(out.contains("\n        <div class=\"block-header\">"),
                "block-header 의 8칸 들여쓰기가 사라졌습니다 — pretty-print 가 다시 썼습니다.\n"
                + "─── 출력 ───\n" + out);
    }

    @Test
    @DisplayName("③ ★ 같은 입력을 두 번 정화하면 같은 것이 나온다")
    void 두_번_정화해도_같다() {
        // ★ 왜 이것도 보는가
        //   정화가 한 번은 안 바꾸고 두 번째부터 바꾸는(수렴하지 않는) 경우를 잡는다.
        //   그런 함수는 "수정 안 한 블록도 저장하면 달라진다" 를 만든다.
        String 한번 = BlockValidator.sanitizeEdited(원본);
        String 두번 = BlockValidator.sanitizeEdited(한번);

        assertEquals(한번, 두번,
                "정화가 멱등이 아닙니다. 수정하지 않은 블록도 저장할 때마다 달라집니다.");
    }
}
