package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "category_entries",
        indexes = {
                @Index(name = "idx_category_entries_slug",        columnList = "slug", unique = true),
                @Index(name = "idx_category_entries_category_id", columnList = "category_id"),
                @Index(name = "idx_category_entries_visibility",  columnList = "visibility"),
                @Index(name = "idx_category_entries_position",    columnList = "position")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryEntry extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_category_entry_category"))
    private Category category;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    /** Optional logo/icon URL — shown in pills (SVG preferred, any image accepted). */
    @Column(name = "icon_url", length = 2048)
    private String iconUrl;

    @Column(name = "interest", nullable = false)
    @Builder.Default
    private Integer interest = 0;

    @Column(name = "position", nullable = false)
    @Builder.Default
    private Integer position = 0;

    @Column(name = "visibility", nullable = false)
    @Builder.Default
    private Boolean visibility = true;
}