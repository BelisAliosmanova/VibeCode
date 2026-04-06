package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "app_sub_reviews",
    indexes = {
        @Index(name = "idx_app_subreview_review_id",   columnList = "app_review_id"),
        @Index(name = "idx_app_subreview_subcat_id",   columnList = "review_sub_category_id"),
        @Index(name = "idx_app_subreview_unique",      columnList = "app_review_id, review_sub_category_id", unique = true)
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AppSubReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_review_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_app_subreview_review"))
    private AppReview appReview;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_sub_category_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_app_subreview_subcat"))
    private ReviewSubCategory reviewSubCategory;

    /** 1–5 star rating */
    @Column(name = "score", nullable = false)
    @Builder.Default
    private Integer score = 0;

    /** Optional description — only used when reviewSubCategory.hasDescriptionBox = true */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}