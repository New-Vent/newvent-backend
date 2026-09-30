package com.newvent.rag;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.rag.domain.Vectors;

/**
 * 변환 왕복 검증. DB 없이 돈다.
 */
public class VectorsTest {

	@Test
	@DisplayName("왕복하면 값이 보존된다.")
	void 왕복_보존() {
		float[] v = {1.0f, 0.0f, -0.5f};

		assertArrayEquals(v, Vectors.fromDb(Vectors.toDb(v)));
	}

	@Test
	@DisplayName("1024차원이 pgvector 형식으로 나온다.")
	void 형식_확인() {
		float[] v = new float[1024];

		String db = Vectors.toDb(v);

		assertTrue(db.startsWith("[") && db.endsWith("]"));
		assertEquals(1024, Vectors.fromDb(db).length);
	}
}
