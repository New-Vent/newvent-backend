package com.newvent.rag.seed;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/** prod 차단 가드 검증. 스프링 없이 돈다. */
class RagSeedDataRunnerTest {

    @Test
    @DisplayName("prod 프로파일이면 시작을 막는다 (대소문자 무시)")
    void rejectsProd() {
        assertThrows(IllegalStateException.class,
                () -> RagSeedDataRunner.rejectProdIfActive(new String[] { "prod" }));
        assertThrows(IllegalStateException.class,
                () -> RagSeedDataRunner.rejectProdIfActive(new String[] { "dev", "PROD" }));
    }

    @Test
    @DisplayName("prod 가 없으면 통과한다")
    void allowsNonProd() {
        assertDoesNotThrow(() -> RagSeedDataRunner.rejectProdIfActive(new String[] { "dev" }));
        assertDoesNotThrow(() -> RagSeedDataRunner.rejectProdIfActive(new String[0]));
    }

    @Test
    @DisplayName("--seed-admin 값을 읽는다. 없으면 null")
    void seedAdminOption() {
        assertNull(RagSeedDataRunner.seedAdminLoginId(new DefaultApplicationArguments()));
        assertEquals("admin1", RagSeedDataRunner.seedAdminLoginId(
                new DefaultApplicationArguments("--seed-admin=admin1")));
    }
}
