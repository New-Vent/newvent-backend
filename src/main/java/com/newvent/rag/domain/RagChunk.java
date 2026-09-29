package com.newvent.rag.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Type;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventVersion;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RAG 검색용 임베딩 청크. V10__add_rag_tables.sql 매핑
 *
 * ★ embedding 은 String 으로 들고 있는다.
 * 	 Hibernate 7에서 float[] 직매핑은 별도 타입 라이브러리가 필요하다.
 *   PG JDBC 가 "[0.1, 0.2]" 를 그대로 보내면 Postgres 가 vector 로 받는다.
 *   변환은 toDb() / toVector() 한 곳에서만 한다.
 */
@Getter
@Entity
@Table(name = "rag_chunks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RagChunk {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false,
				foreignKey = @ForeignKey(name = "fk_rag_chunks_event"))
	private Event event;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "version_id", nullable = false,
				foreignKey = @ForeignKey(name = "fk_rag_chunks_version"))
	private EventVersion version;

	@Column(name = "block_key", nullable = false, length = 30)
	private String blockKey;

	@Column(name = "chunk_index", nullable = false)
	private int chunkIndex;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "embedding_model", nullable = false, length = 80)
	private String embeddingModel;

	@Column(nullable = false, columnDefinition = "vector(1024)")
	@Type(VectorType.class)
	private String embedding;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	private RagChunk(Event event, EventVersion version, String blockKey, int chunkIndex,
            String content, String embeddingModel, String embedding, Instant createdAt) {
        if (event == null) {
            throw new IllegalArgumentException("event는 필수입니다.");
        }
        this.event = event;
        this.version = version;
        this.blockKey = blockKey;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embeddingModel = embeddingModel;
        this.embedding = embedding;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

	public static RagChunk create(Event event, EventVersion version, String blockKey, int chunkIndex,
            String content, String embeddingModel, float[] embedding, Instant createdAt) {
        return new RagChunk(event, version, blockKey, chunkIndex, content,
                embeddingModel, Vectors.toDb(embedding), createdAt);
    }
}
