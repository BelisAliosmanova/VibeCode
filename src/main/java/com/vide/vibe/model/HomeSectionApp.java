package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * One manually-selected app inside a HomeSection.
 *
 * slot=LIST     -> shown in the 5-item list side of the "5+1" layout
 * slot=FEATURED -> the single bigger "featured" card on the other side
 *
 * position orders entries within the same slot (e.g. list item 1..5).
 */
@Entity
@Table(
        name = "home_section_apps",
        indexes = {
                @Index(name = "idx_hsa_section_id", columnList = "home_section_id"),
                @Index(name = "idx_hsa_app_id",     columnList = "app_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeSectionApp extends BaseEntity {

    public enum Slot {
        LIST, FEATURED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_section_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hsa_section"))
    private HomeSection homeSection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_hsa_app"))
    private App app;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot", nullable = false, length = 20)
    @Builder.Default
    private Slot slot = Slot.LIST;

    @Column(name = "position", nullable = false)
    @Builder.Default
    private Integer position = 0;
}