package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "review_sub_categories",
    indexes = {
        @Index(name = "idx_review_subcat_cat_id", columnList = "review_category_id"),
        @Index(name = "idx_review_subcat_position", columnList = "position")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewSubCategory extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_category_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_review_subcat_cat"))
    private ReviewCategory reviewCategory;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** If true, a text box is shown below the stars when filling in this sub-review. */
    @Column(name = "has_description_box", nullable = false)
    @Builder.Default
    private Boolean hasDescriptionBox = false;

    @Column(name = "position", nullable = false)
    @Builder.Default
    private Integer position = 0;
}