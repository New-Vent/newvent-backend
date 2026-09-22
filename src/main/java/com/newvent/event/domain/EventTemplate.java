package com.newvent.event.domain;

import com.newvent.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "event_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "html_content", nullable = false, columnDefinition = "text")
    private String htmlContent;

    @Column(name = "is_builtin", nullable = false)
    private boolean builtin = false;

    @Column(name = "thumbnail_path", length = 255)
    private String thumbnailPath;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
