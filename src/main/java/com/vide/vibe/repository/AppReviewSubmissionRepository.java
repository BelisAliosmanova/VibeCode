package com.vide.vibe.repository;

import com.vide.vibe.model.AppReviewSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppReviewSubmissionRepository extends JpaRepository<AppReviewSubmission, UUID> {
    List<AppReviewSubmission> findAllByAppIdOrderByCreatedAtDesc(UUID appId);
    List<AppReviewSubmission> findAllByAppIdAndVisibleTrueOrderByCreatedAtDesc(UUID appId);
}