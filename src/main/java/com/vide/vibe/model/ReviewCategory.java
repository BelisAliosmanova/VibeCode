package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "review_categories",
    indexes = {
        @Index(name = "idx_review_cat_position", columnList = "position")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewCategory extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Column(name = "position", nullable = false)
    @Builder.Default
    private Integer position = 0;

    @OneToMany(mappedBy = "reviewCategory", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @Builder.Default
    private List<ReviewSubCategory> subCategories = new ArrayList<>();
}