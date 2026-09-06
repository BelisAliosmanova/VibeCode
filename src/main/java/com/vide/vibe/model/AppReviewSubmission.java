package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One person's full review of an App — submitted anonymously by a guest,
 * or (going forward) by staff using the same flow. Groups one AppReview
 * per ReviewCategory the reviewer rated, plus an optional name/comment.
 */
@Entity
@Table(
    name = "app_review_submissions",
    indexes = {
        @Index(name = "idx_review_submission_app_id", columnList = "app_id")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AppReviewSubmission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_review_submission_app"))
    private App app;

    /** Optional — guests aren't required to give a name. */
    @Column(name = "submitter_name", length = 120)
    private String submitterName;

    /** Optional free-text comment for the whole review. */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /** Lets an admin hide a review from averages/listing without deleting it. */
    @Column(name = "visible", nullable = false)
    @Builder.Default
    private Boolean visible = true;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AppReview> reviews = new ArrayList<>();
}