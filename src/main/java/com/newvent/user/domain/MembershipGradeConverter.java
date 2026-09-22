package com.newvent.user.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * MembershipGrade ↔ DB 문자열 변환.
 *   DB CHECK 제약이 enum 상수 이름('NORMAL'/'EXCELLENT'/'BEST')을 요구하므로 name()을 그대로
 *   저장한다 (V1__init_schema.sql 참고). label()은 화면 표시용으로만 쓰고 저장하지 않는다.
 */
@Converter
public class MembershipGradeConverter implements AttributeConverter<MembershipGrade, String> {

    @Override
    public String convertToDatabaseColumn(MembershipGrade grade) {
        return grade == null ? null : grade.name();
    }

    @Override
    public MembershipGrade convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : MembershipGrade.valueOf(dbValue);
    }
}
