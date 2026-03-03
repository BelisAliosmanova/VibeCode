package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single selectable option within a Category.
 *
 * Examples:
 *   Category "Main Category" → entries: Marketing, Gen AI, Productivity, Games...
 *   Category "Features"      → entries: AI Translation, Text Generation, Image Generation...
 *   Category "Pricing"       → entries: Free, Free + IAPs, One-Time Purchase, Subscription...
 *   Category "Subscription Plans" → entries: Monthly, Yearly, Lifetime...
 */
@Entity
@Table(
        name = "category_entries",
        indexes = {
                @Index(name = "idx_category_entries_slug",       columnList = "slug", unique = true),
                @Index(name = "idx_category_entries_category_id", columnList = "category_id"),
                @Index(name = "idx_category_entries_visibility", columnList = "visibility"),
                @Index(name = "idx_category_entries_position",   columnList = "position")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryEntry extends SoftDeletableEntity {

    // ── Parent ────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_category_entry_category"))
    private Category category;

    // ── Fields ────────────────────────────────────────────────────────────────

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    /**
     * Popularity / relevance score — used for sorting entries by interest.
     * Higher = shown first in the submit flow.
     */
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