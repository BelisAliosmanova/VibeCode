package com.vide.vibe.model;

import com.vide.vibe.util.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(
    name = "app_media",
    indexes = {
        @Index(name = "idx_app_media_app_id",          columnList = "app_id"),
        @Index(name = "idx_app_media_app_id_position", columnList = "app_id, position")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppMedia extends BaseEntity {

    public enum MediaType {
        LOGO, SCREENSHOT, VIDEO, THUMBNAIL
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_app_media_app"))
    private App app;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MediaType type;

    /**
     * For LOGO: may be stored as a relative path / CDN key.
     * For VIDEO: YouTube URL or equivalent.
     * For SCREENSHOT / THUMBNAIL: CDN URL.
     */
    @Column(name = "url", length = 2048)
    private String url;

    /**
     * Explicit ordering; videos are always sorted first at the application layer
     * (or via an ORDER BY CASE WHEN type = 'VIDEO' THEN 0 ELSE 1 END, position).
     */
    @Column(name = "position", nullable = false)
    private Integer position;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata = new HashMap<>();
}