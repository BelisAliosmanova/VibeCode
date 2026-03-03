package com.vide.vibe.model;

import com.vide.vibe.util.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Table(
    name = "workflows",
    indexes = {
        @Index(name = "idx_workflows_app_id",          columnList = "app_id"),
        @Index(name = "idx_workflows_app_id_position", columnList = "app_id, position")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow extends SoftDeletableEntity {

    // ── App ────────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_workflows_app"))
    private App app;

    // ── Fields ─────────────────────────────────────────────────────────────────

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata = new HashMap<>();

    // ── Steps ──────────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @Builder.Default
    private List<WorkflowStep> steps = new ArrayList<>();
}