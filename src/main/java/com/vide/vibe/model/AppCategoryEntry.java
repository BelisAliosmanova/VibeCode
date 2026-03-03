package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Links an App to a CategoryEntry.
 *
 * This single join table handles ALL category selections:
 *   - which main category an app belongs to
 *   - which features it has
 *   - what pricing model it uses
 *   - what subscription plans it offers
 *   - any future category the admin creates
 *
 * The category context is implicit via entry.category.
 */
@Entity
@Table(
    name = "app_category_entries",
    indexes = {
        @Index(name = "idx_ace_app_id",   columnList = "app_id"),
        @Index(name = "idx_ace_entry_id", columnList = "entry_id"),
        // Composite: useful for "give me all entries for this app in this category"
        @Index(name = "idx_ace_app_entry", columnList = "app_id, entry_id", unique = true)
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCategoryEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ace_app"))
    private App app;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ace_entry"))
    private CategoryEntry entry;

    /**
     * When the user made this selection — useful for analytics.
     */
    @Column(name = "selected_at", nullable = false)
    @Builder.Default
    private Instant selectedAt = Instant.now();
}