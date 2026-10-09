package com.newvent.editor.service;

import java.util.Arrays;

import com.newvent.editor.exception.RouterErrorCode;
import com.newvent.registry.Block;

/**
 * 라우터 출력을 실행 가능한 결정으로 바꾼다.
 *
 * ★ String target 이 Block 객체가 되는 유일한 지점 -> 아래로는 String target 이 존재 X
 *
 * ★ 순수 함수. LLM · DB · HTML 을 전혀 보지 않음.
 *   RawRoute 하나를 받아 Decision 하나를 돌려줄 뿐 -> 테스트에 Mock 필요 X
 *
 * ★ 왜 서버가 판단하나?
 * 	 모델에게 시키면 불확실성이 모델로 넘어감.
 *
 * ★ DELETE 는 EDIT 으로 바꾸지 않음
 *   "유의사항 지워줘" 를 조용히 수정으로 바꾸면 관리자는 지운 줄 앎. 거부하고 이유를 말해야 함.
 *
 * ★ 판정 순서가 정본
 *   (target -> op -> SERVER -> 삭제금지 -> 폼값항목 -> 항목유무 -> 교정 순서로 감)
 *   순서를 바꾸면 다른 결과가 출력
 */
public class Gate {

	// Block.of() 를 쓰지 않음. 모르는 key 에 IllegalArgumentException 을 던지는데 여기선 Reject
	private static Block blockOf(String key) {
		if(key == null || key.isBlank()) return null;
		return Arrays.stream(Block.values())
				.filter(b -> b.key().equals(key))
				.findFirst()
				.orElse(null);
	}

	// 항목 값은 관리자가 정한다. 모델이 지어내면 게시된 페이지에서 사고
	private static String askBackQuestion(Block block) {
	    return block.desc() + " — 어떤 내용을 추가할까요?";
	}

	public Decision decide(RawRoute raw) {
		if(raw == null) {
			return new Decision.Reject(RouterErrorCode.UNKNOWN_OP, "요청을 읽지 못했습니다.");
		}

		// 1. 영역부터 봄. target 이 없으면 op 를 봐도 소용없다.
		Block block = blockOf(raw.target());
		if(block == null) {
			return new Decision.Reject(RouterErrorCode.TARGET_NOT_FOUND,
					"알 수 없는 영역입니다. 다시 말씀해 주세요.");
		}

		// 2. ★ op 확인이 SERVER 판정보다 먼저다.
        Op op = Op.find(raw.op()).orElse(null);
        if (op == null) {
            return new Decision.Reject(RouterErrorCode.UNKNOWN_OP,
                    "무엇을 하실지 알아내지 못했습니다. 다시 말씀해 주세요.");
        }

	    // 3. ★ 서버 소유 — op 과 무관하게 거부한다.
        if (block.source() == Block.Source.SERVER) {
            return new Decision.Reject(RouterErrorCode.NOT_ALLOWED,
                    block.desc() + " 영역은 시스템이 관리합니다. 채팅으로 바꿀 수 없습니다.");
        }

    	// 4. 필수 영역은 지울 수 없음.
		if(!block.canDelete() && op == Op.DELETE) {
			String why = block.denyReason(op.name());
			return new Decision.Reject(RouterErrorCode.NOT_ALLOWED,
					why != null ? why : block.key() + " 영역은 지울 수 없습니다.");
		}

		// 5. ★★ 전에는 항목이 폼 값인 영역(혜택 · 경품)의 ADD 를 **무조건 거절**했다.
		//    막으려던 건 "모델이 지어낸 혜택이 게시되는 것" 이지, **관리자가 적어 준 것**이
		//    아니다. "혜택에 커피쿠폰 추가해" 는 관리자가 지급을 결정한 것이다.
		//    내용이 없는 경우는 바로 아래 6번이 되묻는다 — 거절보다 그쪽이 맞다.

		// 6. 항목 추가인데 내용이 없다 → 되묻는다. 교정보다 먼저.
        if (op == Op.ADD && (raw.content() == null || raw.content().isBlank())) {
            return new Decision.AskBack(askBackQuestion(block));
        }

		// 7. 필수 영역에 ADD 하면 EDIT 으로 교정 — 필수 영역은 늘 있으니 "새로 넣기" 가 성립 안 한다.
		//    ★★ 단, 항목을 나열하는 영역(container)은 빼야 한다.
		//    benefits 는 required=true → canCreate()=false 라서 여기서 ADD 가 EDIT 으로 바뀌었고,
		//    그러면 "항목 하나 더" 라는 의도가 op 에서 사라진다. 뒤에서 그 깃발로 검증기와
		//    프롬프트를 여는데 깃발이 영원히 꺼져 있어서, 5번을 풀어도 혜택 추가는 여전히
		//    ITEM_ADDED 로 4회 전부 터졌다(실측 — "혜택 4번째에 커피쿠폰 추가해줘").
		//    container 가 없는 필수 영역(hero · cta)은 그대로 교정한다.
		if (op == Op.ADD && !block.canCreate() && block.container() == null) {
			return new Decision.Run(block, Op.EDIT, raw.content());
		}

		// 8. 나머지는 통과
		return new Decision.Run(block, op, raw.content());
	}
}
