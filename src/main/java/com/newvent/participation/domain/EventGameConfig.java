package com.newvent.participation.domain;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.newvent.event.domain.Event;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "event_game_configs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_game_configs_event_game",
                columnNames = {"event_id", "game_id"}
        )
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventGameConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config = new LinkedHashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "game_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_event_game_configs_game")
    )
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "event_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_event_game_configs_event")
    )
    private Event event;

    /**
     * 관리자가 참여 설정을 처음 저장할 때.
     *
     * ★ config 는 검증을 통과한 값만 들어온다 (ParticipationValidator.validateConfig).
     *   여기서 다시 검사하지 않는다 — 규칙이 두 곳에 있으면 갈라진다.
     */
    public static EventGameConfig create(Event event, Game game, Map<String, Object> config) {
        EventGameConfig created = new EventGameConfig();
        created.event = event;
        created.game = game;
        created.config = copyOf(config);
        return created;
    }

    /**
     * 참여 방식과 설정을 바꾼다.
     *
     * ★ 이벤트당 설정은 하나다. 그래서 방식이 바뀌어도 행을 새로 만들지 않고 이 행을 고친다 —
     *   새로 만들면 둘이 되어 ParticipationService 가 size() != 1 로 거부한다.
     * ★ 참여 기록이 있는지는 **서비스가** 본다. 엔티티는 참여를 모른다.
     */
    public void changeConfig(Game game, Map<String, Object> config) {
        this.game = game;
        this.config = copyOf(config);
    }

    // 넘어온 Map 을 그대로 들고 있으면 호출한 쪽이 나중에 고칠 수 있다. 복사해서 끊는다.
    private static Map<String, Object> copyOf(Map<String, Object> config) {
        return config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
    }
}
