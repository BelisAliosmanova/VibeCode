package com.vide.vibe.repository;

import com.vide.vibe.model.AppReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AppReviewRepository extends JpaRepository<AppReview, UUID> {

    List<AppReview> findAllBySubmissionId(UUID submissionId);

    @Query("select r from AppReview r where r.submission.app.id = :appId")
    List<AppReview> findAllByAppId(@Param("appId") UUID appId);

    @Query("select r from AppReview r where r.submission.app.id = :appId and r.submission.visible = true")
    List<AppReview> findAllVisibleByAppId(@Param("appId") UUID appId);
}