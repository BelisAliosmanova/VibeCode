package com.vide.vibe.repository;

import com.vide.vibe.model.ReviewSubCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewSubCategoryRepository extends JpaRepository<ReviewSubCategory, UUID> {
    List<ReviewSubCategory> findAllByReviewCategoryIdAndDeletedAtIsNullOrderByPositionAsc(UUID reviewCategoryId);
}