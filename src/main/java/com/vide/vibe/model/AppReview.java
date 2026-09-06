package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * One category's star rating within a single AppReviewSubmission
 * (one reviewer's rating for one ReviewCategory, e.g. "Reliability").
 * The score is the average of its AppSubReviews.
 */
@Entity
@Table(
        name = "app_reviews",
        indexes = {
                @Index(name = "idx_app_review_submission_id", columnList = "app_review_submission_id"),
                @Index(name = "idx_app_review_cat_id",         columnList = "review_category_id"),
                @Index(name = "idx_app_review_submission_cat", columnList = "app_review_submission_id, review_category_id", unique = true)
        }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AppReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_review_submission_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_app_review_submission"))
    private AppReviewSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_app_review_cat"))
    private ReviewCategory reviewCategory;

    /** Average of this category's sub-review scores, within THIS submission only. */
    @Column(name = "score")
    private Double score;

    @OneToMany(mappedBy = "appReview", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AppSubReview> subReviews = new ArrayList<>();
}