package com.newvent.user.dto.response;

import java.util.Arrays;
import java.util.List;

import com.newvent.user.domain.MembershipGrade;

// 등급 종류 안내용. 산정 기준(점수표·금액·기간)은 일부러 담지 않는다
// 등급은 별도 고객·과금 시스템이 산정한다는 전제라, 사용자에게는 어떤 등급이 있고 무엇을 뜻하는지만 알려준다.
public record MembershipGradeResponse(MembershipGrade grade, String name, String description) {

    public static List<MembershipGradeResponse> all() {
        return Arrays.stream(MembershipGrade.values())
                .map(grade -> new MembershipGradeResponse(grade, grade.label(), describe(grade)))
                .toList();
    }

    private static String describe(MembershipGrade grade) {
        return switch (grade) {
            case NORMAL -> "기본 등급입니다. 요금제와 가입 기간을 종합해 산정됩니다.";
            case EXCELLENT -> "일반 등급보다 높은 등급입니다. 일반 등급 이벤트와 우수 등급 이벤트에 참여할 수 있습니다.";
            case BEST -> "가장 높은 등급입니다. 모든 등급의 이벤트에 참여할 수 있습니다.";
        };
    }
}
