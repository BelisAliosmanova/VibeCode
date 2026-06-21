package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A user-configurable section on the homepage (e.g. "Newest in Productivity").
 *
 * Two layouts are supported:
 *
 *  FIVE_PLUS_ONE — five apps in a list on one side, one "featured" app on the
 *                  other. Each side has its own independently editable title
 *                  ({@code title} for the list side, {@code featuredTitle}
 *                  for the featured side).
 *
 *  SIX_GRID      — six apps shown in a single 2x3 (or similar) grid, no
 *                  featured slot. Only {@code title} is used; featuredTitle
 *                  is ignored for this layout.
 *
 * Sections themselves are ordered via {@code position}, which is what gets
 * persisted when the admin drags a section to a new spot on the homepage.
 */
@Entity
@Table(
        name = "home_sections",
        indexes = {
                @Index(name = "idx_home_sections_position", columnList = "position")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeSection extends SoftDeletableEntity {

    public enum Layout {
        FIVE_PLUS_ONE,
        SIX_GRID
    }

    /** List-side title (FIVE_PLUS_ONE) or the single title (SIX_GRID). */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /** Featured-side title — only meaningful for FIVE_PLUS_ONE. */
    @Column(name = "featured_title", length = 255)
    private String featuredTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout", nullable = false, length = 30)
    @Builder.Default
    private Layout layout = Layout.FIVE_PLUS_ONE;

    /** Order among all homepage sections (built-in + custom). */
    @Column(name = "position", nullable = false)
    @Builder.Default
    private Integer position = 0;

    @OneToMany(mappedBy = "homeSection", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @Builder.Default
    private List<HomeSectionApp> apps = new ArrayList<>();
}