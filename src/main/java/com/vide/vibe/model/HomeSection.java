package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A user-configurable section on the homepage (e.g. "Newest in Productivity").
 *
 * Each section has a layout (currently just "5+1": five apps in a list on
 * one side, one "featured" app on the other) and an ordered list of manually
 * chosen apps (HomeSectionApp). Sections themselves are ordered via
 * {@code position}, which is what gets persisted when the admin drags a
 * section to a new spot on the homepage.
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
        FIVE_PLUS_ONE
    }

    @Column(name = "title", nullable = false, length = 255)
    private String title;

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