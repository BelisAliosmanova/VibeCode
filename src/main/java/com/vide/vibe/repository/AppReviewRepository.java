package com.vide.vibe.repository;

import com.vide.vibe.model.AppReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppReviewRepository extends JpaRepository<AppReview, UUID> {
    List<AppReview> findAllByAppId(UUID appId);
    List<AppReview> findAllByAppIdAndVisibleTrue(UUID appId);
    Optional<AppReview> findByAppIdAndReviewCategoryId(UUID appId, UUID reviewCategoryId);
}