package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ranking_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RankingConfig {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "description_length_points", nullable = false)
    private int descriptionLengthPoints;   // description > 150 chars

    @Column(name = "fav_icon_points", nullable = false)
    private int favIconPoints;

    @Column(name = "video_points", nullable = false)
    private int videoPoints;

    @Column(name = "workflow_points", nullable = false)
    private int workflowPoints;            // 1st workflow
    @Column(name = "additional_workflow_points", nullable = false)
    private int additionalWorkflowPoints;  // each workflow after the 1st

    @Column(name = "image_points", nullable = false)
    private int imagePoints;               // 1st screenshot
    @Column(name = "additional_image_points", nullable = false)
    private int additionalImagePoints;     // each screenshot after the 1st

    @Column(name = "github_listing_points", nullable = false)
    private int githubListingPoints;

    @Column(name = "tags_points", nullable = false)
    private int tagsPoints;                // >= 2 category tags

    @Column(name = "claimed_points", nullable = false)
    private int claimedPoints;

    @Column(name = "verified_points", nullable = false)
    private int verifiedPoints;

    @Column(name = "high_rating_points", nullable = false)
    private int highRatingPoints;          // > 4.0 avg, >= 5 raters

    @Column(name = "paid_review_points", nullable = false)
    private int paidReviewPoints;

    public static RankingConfig defaults() {
        RankingConfig c = new RankingConfig();
        c.descriptionLengthPoints = 5;
        c.favIconPoints = 2;
        c.videoPoints = 7;
        c.workflowPoints = 5;
        c.additionalWorkflowPoints = 3;
        c.imagePoints = 3;
        c.additionalImagePoints = 1;
        c.githubListingPoints = 5;
        c.tagsPoints = 3;
        c.claimedPoints = 5;
        c.verifiedPoints = 10;
        c.highRatingPoints = 10;
        c.paidReviewPoints = 15;
        return c;
    }

    public void copyEditableFieldsFrom(RankingConfig other) {
        this.descriptionLengthPoints = other.descriptionLengthPoints;
        this.favIconPoints = other.favIconPoints;
        this.videoPoints = other.videoPoints;
        this.workflowPoints = other.workflowPoints;
        this.additionalWorkflowPoints = other.additionalWorkflowPoints;
        this.imagePoints = other.imagePoints;
        this.additionalImagePoints = other.additionalImagePoints;
        this.githubListingPoints = other.githubListingPoints;
        this.tagsPoints = other.tagsPoints;
        this.claimedPoints = other.claimedPoints;
        this.verifiedPoints = other.verifiedPoints;
        this.highRatingPoints = other.highRatingPoints;
        this.paidReviewPoints = other.paidReviewPoints;
    }
}