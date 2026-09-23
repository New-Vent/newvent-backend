package com.newvent.event.support;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;

import com.newvent.common.domain.BaseTimeEntity;

/** 인메모리 시드용. common BaseTimeEntity 는 수정하지 않는다. */
public final class EntityTimestamps {

    private EntityTimestamps() {
    }

    public static void set(BaseTimeEntity entity, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        setField(entity, "createdAt", createdAt);
        setField(entity, "updatedAt", updatedAt);
    }

    private static void setField(BaseTimeEntity entity, String name, OffsetDateTime value) {
        try {
            Field field = BaseTimeEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set " + name, e);
        }
    }
}
