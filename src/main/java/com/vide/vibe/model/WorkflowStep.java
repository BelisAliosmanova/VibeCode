package com.vide.vibe.model;

import com.vide.vibe.util.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(
    name = "workflow_steps",
    indexes = {
        @Index(name = "idx_workflow_steps_workflow_id",          columnList = "workflow_id"),
        @Index(name = "idx_workflow_steps_workflow_id_position", columnList = "workflow_id, position")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStep extends SoftDeletableEntity {

    // ── Workflow ───────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_workflow_steps_workflow"))
    private Workflow workflow;

    // ── Fields ─────────────────────────────────────────────────────────────────

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata = new HashMap<>();
}