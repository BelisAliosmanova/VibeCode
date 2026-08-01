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
    private int descriptionLengthPoints;

    @Column(name = "fav_icon_points", nullable = false)
    private int favIconPoints;

    @Column(name = "video_points", nullable = false)
    private int videoPoints;

    @Column(name = "workflow_points", nullable = false)
    private int workflowPoints;
    @Column(name = "additional_workflow_points", nullable = false)
    private int additionalWorkflowPoints;

    @Column(name = "image_points", nullable = false)
    private int imagePoints;
    @Column(name = "additional_image_points", nullable = false)
    private int additionalImagePoints;

    @Column(name = "github_listing_points", nullable = false)
    private int githubListingPoints;

    @Column(name = "tags_points", nullable = false)
    private int tagsPoints;

    @Column(name = "claimed_points", nullable = false)
    private int claimedPoints;

    @Column(name = "verified_points", nullable = false)
    private int verifiedPoints;

    @Column(name = "high_rating_points", nullable = false)
    private int highRatingPoints;

    // ── thresholds (previously hardcoded static finals in RankingService) ──
    @Column(name = "min_description_length", nullable = false)
    private int minDescriptionLength;

    @Column(name = "min_tags_for_bonus", nullable = false)
    private int minTagsForBonus;

    @Column(name = "min_rating_for_bonus", nullable = false)
    private double minRatingForBonus;

    @Column(name = "min_raters_for_bonus", nullable = false)
    private int minRatersForBonus;

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

        c.minDescriptionLength = 150;
        c.minTagsForBonus = 2;
        c.minRatingForBonus = 4.0;
        c.minRatersForBonus = 5;
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

        this.minDescriptionLength = other.minDescriptionLength;
        this.minTagsForBonus = other.minTagsForBonus;
        this.minRatingForBonus = other.minRatingForBonus;
        this.minRatersForBonus = other.minRatersForBonus;
    }
}