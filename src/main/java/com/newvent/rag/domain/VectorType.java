package com.newvent.rag.domain;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

/**
 * String ↔ vector 매핑.
 *
 * ★ Types.OTHER 로 바인딩한다.
 *   String 그대로 보내면 Hibernate 가 varchar 로 묶어서
 *   "character varying → vector" 캐스트 오류가 난다 (실측).
 *   OTHER 로 보내면 Postgres 가 문맥에서 vector 로 받는다.
 *
 * ★ 읽기는 getString 이다. vector 출력 형식이 텍스트라 그대로 온다.
 */
public class VectorType implements UserType<String>{

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(String x, String y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(String x) {
        return Objects.hashCode(x);
    }

    @Override
    public String nullSafeGet(ResultSet rs, int position,
            SharedSessionContractImplementor session, Object owner) throws SQLException {
        String value = rs.getString(position);
        return rs.wasNull() ? null : value;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, String value, int index,
            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value, Types.OTHER);
        }
    }

    @Override
    public String deepCopy(String value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(String value) {
        return value;
    }

    @Override
    public String assemble(Serializable cached, Object owner) {
        return (String) cached;
    }

    @Override
    public String replace(String detached, String managed, Object owner) {
        return detached;
    }
}
