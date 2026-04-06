package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * An admin's review of an App for one ReviewCategory.
 * The score is the average of its AppSubReviews.
 * visible controls whether it appears on the public app page.
 */
@Entity
@Table(
    name = "app_reviews",
    indexes = {
        @Index(name = "idx_app_review_app_id",    columnList = "app_id"),
        @Index(name = "idx_app_review_cat_id",    columnList = "review_category_id"),
        @Index(name = "idx_app_review_app_cat",   columnList = "app_id, review_category_id", unique = true)
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AppReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_app_review_app"))
    private App app;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_category_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_app_review_cat"))
    private ReviewCategory reviewCategory;

    /**
     * Computed average of all sub-review scores. Null until at least one sub-review exists.
     * Recomputed on save.
     */
    @Column(name = "score")
    private Double score;

    @Column(name = "visible", nullable = false)
    @Builder.Default
    private Boolean visible = true;

    @OneToMany(mappedBy = "appReview", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AppSubReview> subReviews = new ArrayList<>();
}