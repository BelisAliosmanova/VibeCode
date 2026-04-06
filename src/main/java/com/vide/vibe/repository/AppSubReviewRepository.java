package com.vide.vibe.repository;

import com.vide.vibe.model.AppSubReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppSubReviewRepository extends JpaRepository<AppSubReview, UUID> {
    List<AppSubReview> findAllByAppReviewId(UUID appReviewId);
    Optional<AppSubReview> findByAppReviewIdAndReviewSubCategoryId(UUID appReviewId, UUID subCategoryId);
}