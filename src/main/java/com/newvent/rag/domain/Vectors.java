package com.newvent.rag.domain;

/**
 * float[] ↔ pgvector 텍스트 형식 변환
 *
 * ★ DB·JPA를 모른다. 순수 함수라 단위 테스트가 DB 없이 돈다.
 * 	 형식 "[0.1,0.2,...]" 약속은 여기서만 안다.
 *   RagChunkRepositoryTest 가 변환을 복제하지 않는 이유다.
 */
public final class Vectors {
	private Vectors() {}

	// float[] → "[0.1, 0.2, ...]"
	public static String toDb(float[] vector) {
		StringBuilder sb = new StringBuilder("[");
		for(int i = 0; i < vector.length; i++) {
			if(i > 0) sb.append(',');
			sb.append(Float.toString(vector[i]));
		}
		return sb.append(']').toString();
	}

	// "[0.1, 0.2, ...]" → float[]
	public static float[] fromDb(String db) {
		String s = db.substring(1, db.length() - 1);
		String[] parts = s.split(",");
		float[] out = new float[parts.length];
		for(int i = 0; i < parts.length; i++) {
			out[i] = Float.parseFloat(parts[i]);
		}
		return out;
	}
	
	public static double cosine(float[] a, float[] b) {
		if(a.length != b.length) {
			throw new IllegalArgumentException("벡터 길이가 다릅니다.");
		}
		double dot = 0, na = 0, nb = 0;
		for(int i = 0; i < a.length; i++) {
			dot += (double) a[i] * b[i];
			na += (double) a[i] * a[i];
			nb += (double) b[i] * b[i];
		}
		if (na == 0 || nb == 0) {
			return 0.0;
		}
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}
}
