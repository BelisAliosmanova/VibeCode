package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A Category is the universal container for any selectable group.
 *
 * Examples the admin can create:
 *   - "Main Category"      (max_selected=3,  visibility=true)
 *   - "Features"           (max_selected=10, visibility=true)
 *   - "Pricing"            (max_selected=1,  visibility=true)
 *   - "Subscription Plans" (max_selected=1,  visibility=true)
 *   - anything else in the future...
 *
 * The submit flow renders one step per visible category automatically.
 * The explore page renders one filter group per filterVisible category.
 */
@Entity
@Table(
        name = "categories",
        indexes = {
                @Index(name = "idx_categories_slug",           columnList = "slug", unique = true),
                @Index(name = "idx_categories_visibility",     columnList = "visibility"),
                @Index(name = "idx_categories_filter_visible", columnList = "filter_visible"),
                @Index(name = "idx_categories_position",       columnList = "position")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    /**
     * Controls whether this category appears on the submit flow.
     */
    @Column(name = "visibility", nullable = false)
    @Builder.Default
    private Boolean visibility = true;

    /**
     * Controls whether this category appears as a filter group
     * in the public Explore page sidebar.
     */
    @Column(name = "filter_visible", nullable = false)
    @Builder.Default
    private Boolean filterVisible = false;

    /**
     * How many entries the user can select from this category.
     * 1  = single select (e.g. Pricing)
     * >1 = multi select  (e.g. Features, Main Category)
     * null = unlimited
     */
    @Column(name = "max_selected")
    private Integer maxSelected;

    /**
     * Controls the order categories appear in the submit flow.
     */
    @Column(name = "position", nullable = false)
    @Builder.Default
    private Integer position = 0;

    /**
     * Optional hint shown to the user on the submit step
     * e.g. "Choose the best category for your app"
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * If true, the user can type in a custom entry not in the list.
     * Useful for "Features" but not for "Pricing".
     */
    @Column(name = "allow_custom", nullable = false)
    @Builder.Default
    private Boolean allowCustom = false;

    // ── Entries ───────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC, name ASC")
    @Builder.Default
    private List<CategoryEntry> entries = new ArrayList<>();
}