package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "apps",
        indexes = {
                @Index(name = "idx_apps_owner_id",   columnList = "owner_id"),
                @Index(name = "idx_apps_status",     columnList = "status"),
                @Index(name = "idx_apps_visibility", columnList = "visibility"),
                @Index(name = "idx_apps_deleted_at", columnList = "deleted_at")
                // Partial unique index on slug created via Flyway:
                // CREATE UNIQUE INDEX idx_apps_slug ON apps(slug) WHERE deleted_at IS NULL;
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class App extends SoftDeletableEntity {

    public enum Status {
        DRAFT, SUBMITTED, APPROVED, REJECTED
    }

    public enum Visibility {
        PUBLIC, PRIVATE
    }

    // ── Owner ─────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_apps_owner"))
    private User owner;

    // ── Collaborators ─────────────────────────────────────────────────────────

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "app_collaborators",
            joinColumns        = @JoinColumn(name = "app_id",  foreignKey = @ForeignKey(name = "fk_colabs_app")),
            inverseJoinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_colabs_user"))
    )
    @Builder.Default
    private Set<User> collaborators = new LinkedHashSet<>();

    // ── Core fields ───────────────────────────────────────────────────────────

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, length = 255)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "url", length = 2048)
    private String url;

    // ── Status / visibility ───────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "version", length = 50)
    @Builder.Default
    private String version = "1";

    // ── Relationships ─────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "app", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @Builder.Default
    private List<AppMedia> media = new ArrayList<>();

    @OneToMany(mappedBy = "app", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @Builder.Default
    private List<Workflow> workflows = new ArrayList<>();

    /**
     * All category entry selections for this app — covers main category,
     * features, pricing, subscription plans, and any future category.
     * Use AppCategoryEntryRepository to query by category when needed.
     */
    @OneToMany(mappedBy = "app", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AppCategoryEntry> categorySelections = new ArrayList<>();
}