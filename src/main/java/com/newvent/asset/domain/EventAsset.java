package com.newvent.asset.domain;

import jakarta.persistence.*;

import com.newvent.common.domain.BaseTimeEntity;
import com.newvent.registry.Asset;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자가 이벤트에 올린 이미지 한 장.
 *
 * ★ 파일은 DB 에 안 넣는다. 박스가 1.9GB 라 pg_dump 와 버퍼캐시가 같이 커진다.
 *   여기 있는 건 "어디에 무엇이 있나" 뿐이고, 바이트는 AssetStore 가 쥔다.
 *
 * ★ id 를 UUID 로 두는 이유 — 공개 경로(/api/public/assets/*)라 1 · 2 · 3 이면
 *   남의 이벤트 이미지가 전부 열린다.
 */
@Getter
@Entity
@Table(name = "event_assets", indexes = @Index(name = "ix_event_assets_event", columnList = "event_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventAsset extends BaseTimeEntity {

    /** 32자리 16진수. 주소에 그대로 들어간다 */
    @Id
    @Column(length = 32, nullable = false, updatable = false)
    private String id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    /**
     * 이벤트별 고정 번호 — 관리자가 "2번 이미지" 라고 말하는 그 번호다.
     *
     * ★★ 목록 순서로 세면 1번을 지운 순간 2번이 1번이 된다.
     *   "1번 이미지를 배경으로" 가 다른 사진을 가리키게 되고 관리자는 그걸 모른다.
     * ★ 다시 쓰지 않는다. 지운 번호는 구멍으로 남는다 (1, 3, 4).
     */
    @Column(nullable = false, updatable = false)
    private int seq;

    /** ImageProbe 가 디코딩해서 알아낸 형식 — 브라우저가 보낸 값이 아니다 */
    @Column(nullable = false, length = 8, updatable = false)
    private String extension;

    @Column(name = "content_type", nullable = false, length = 40, updatable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false, updatable = false)
    private long byteSize;

    /** 절대 URL. 정화기 허용 목록에 이 값이 그대로 들어간다 */
    @Column(nullable = false, length = 500, updatable = false)
    private String url;

    /** MIXED 일 때의 덮개 농도 — 올릴 때 한 번 재서 박아 둔다. LIGHT · DARK 는 0 이다 */
    @Column(nullable = false, updatable = false)
    private double scrim;

    /**
     * 사진 위 글자를 어떻게 할 것인가 (LIGHT · DARK · MIXED).
     * ★ 문자열로 저장한다. ordinal 로 두면 enum 에 값을 끼워 넣는 순간 과거 행의 뜻이 바뀐다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private Asset.Tone tone;

    /** 관리자가 적은 설명. alt 로 쓴다. 비면 alt="" */
    @Column(length = 120)
    private String caption;

    public EventAsset(String id, Long eventId, int seq, String extension, String contentType,
                      long byteSize, String url, double scrim, Asset.Tone tone, String caption) {
        this.id = id;
        this.eventId = eventId;
        this.seq = seq;
        this.extension = extension;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.url = url;
        this.scrim = scrim;
        this.tone = tone;
        this.caption = caption;
    }

    public void rename(String caption) {
        this.caption = caption;
    }
}
