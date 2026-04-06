package com.vide.vibe.service;

import com.vide.vibe.model.*;
import com.vide.vibe.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewCategoryRepository reviewCategoryRepository;
    private final ReviewSubCategoryRepository reviewSubCategoryRepository;
    private final AppReviewRepository appReviewRepository;
    private final AppSubReviewRepository appSubReviewRepository;
    private final AppService appService;

    public List<ReviewCategory> findAllCategories() {
        return reviewCategoryRepository.findAllByDeletedAtIsNullOrderByPositionAsc();
    }

    public ReviewCategory findCategoryById(UUID id) {
        return reviewCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ReviewCategory not found: " + id));
    }

    @Transactional
    public ReviewCategory createCategory(ReviewCategory category) {
        return reviewCategoryRepository.save(category);
    }

    @Transactional
    public ReviewCategory updateCategory(UUID id, ReviewCategory updated) {
        ReviewCategory existing = findCategoryById(id);
        existing.setName(updated.getName());
        existing.setSlug(updated.getSlug());
        existing.setPosition(updated.getPosition());
        return reviewCategoryRepository.save(existing);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        ReviewCategory cat = findCategoryById(id);
        cat.softDelete();
        reviewCategoryRepository.save(cat);
    }

    // ── Review Sub-Categories ──────────────────────────────────────────────

    public List<ReviewSubCategory> findSubCategoriesByCategoryId(UUID categoryId) {
        return reviewSubCategoryRepository
                .findAllByReviewCategoryIdAndDeletedAtIsNullOrderByPositionAsc(categoryId);
    }

    public ReviewSubCategory findSubCategoryById(UUID id) {
        return reviewSubCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ReviewSubCategory not found: " + id));
    }

    @Transactional
    public ReviewSubCategory createSubCategory(UUID categoryId, ReviewSubCategory sub) {
        ReviewCategory cat = findCategoryById(categoryId);
        sub.setReviewCategory(cat);
        return reviewSubCategoryRepository.save(sub);
    }

    @Transactional
    public ReviewSubCategory updateSubCategory(UUID id, ReviewSubCategory updated) {
        ReviewSubCategory existing = findSubCategoryById(id);
        existing.setName(updated.getName());
        existing.setHasDescriptionBox(updated.getHasDescriptionBox());
        existing.setPosition(updated.getPosition());
        return reviewSubCategoryRepository.save(existing);
    }

    @Transactional
    public void deleteSubCategory(UUID id) {
        ReviewSubCategory sub = findSubCategoryById(id);
        sub.softDelete();
        reviewSubCategoryRepository.save(sub);
    }

    public List<AppReview> findReviewsForApp(UUID appId) {
        return appReviewRepository.findAllByAppId(appId);
    }

    public List<AppReview> findVisibleReviewsForApp(UUID appId) {
        return appReviewRepository.findAllByAppIdAndVisibleTrue(appId);
    }

    public Optional<AppReview> findAppReview(UUID appId, UUID reviewCategoryId) {
        return appReviewRepository.findByAppIdAndReviewCategoryId(appId, reviewCategoryId);
    }

    public AppReview findAppReviewById(UUID id) {
        return appReviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AppReview not found: " + id));
    }

    @Transactional
    public AppReview saveAppReview(UUID appId, UUID reviewCategoryId,
                                   boolean visible,
                                   java.util.Map<UUID, Integer> subScores,
                                   java.util.Map<UUID, String> subDescriptions) {

        App app = appService.findById(appId);
        ReviewCategory cat = findCategoryById(reviewCategoryId);

        AppReview review = appReviewRepository
                .findByAppIdAndReviewCategoryId(appId, reviewCategoryId)
                .orElseGet(() -> AppReview.builder().app(app).reviewCategory(cat).build());

        review.setVisible(visible);

        for (var entry : subScores.entrySet()) {
            UUID subCatId = entry.getKey();
            int score = clampScore(entry.getValue());
            String desc = subDescriptions != null ? subDescriptions.get(subCatId) : null;

            ReviewSubCategory subCat = findSubCategoryById(subCatId);

            AppSubReview subReview = appSubReviewRepository
                    .findByAppReviewIdAndReviewSubCategoryId(
                            review.getId() != null ? review.getId() : UUID.randomUUID(),
                            subCatId)
                    .orElseGet(() -> AppSubReview.builder()
                            .appReview(review)
                            .reviewSubCategory(subCat)
                            .build());

            subReview.setScore(score);
            subReview.setDescription(desc);
            review.getSubReviews().add(subReview);
        }

        AppReview saved = appReviewRepository.save(review);

        // Now upsert sub-reviews properly (review ID is stable now)
        for (var entry : subScores.entrySet()) {
            UUID subCatId = entry.getKey();
            int score = clampScore(entry.getValue());
            String desc = subDescriptions != null ? subDescriptions.get(subCatId) : null;

            ReviewSubCategory subCat = findSubCategoryById(subCatId);

            AppReview finalSaved = saved;
            AppSubReview subReview = appSubReviewRepository
                    .findByAppReviewIdAndReviewSubCategoryId(saved.getId(), subCatId)
                    .orElseGet(() -> AppSubReview.builder()
                            .appReview(finalSaved)
                            .reviewSubCategory(subCat)
                            .build());

            subReview.setScore(score);
            subReview.setDescription(desc);
            appSubReviewRepository.save(subReview);
        }

        List<AppSubReview> allSubs = appSubReviewRepository.findAllByAppReviewId(saved.getId());
        if (!allSubs.isEmpty()) {
            double avg = allSubs.stream()
                    .mapToInt(AppSubReview::getScore)
                    .average()
                    .orElse(0.0);
            saved.setScore(avg);
        } else {
            saved.setScore(null);
        }

        saved = appReviewRepository.save(saved);

        recomputeVerifiedScore(appId);

        return saved;
    }

    @Transactional
    public void updateVisibility(UUID appReviewId, boolean visible) {
        AppReview review = findAppReviewById(appReviewId);
        review.setVisible(visible);
        appReviewRepository.save(review);
        recomputeVerifiedScore(review.getApp().getId());
    }

    private void recomputeVerifiedScore(UUID appId) {
        List<AppReview> reviews = appReviewRepository.findAllByAppId(appId);
        OptionalDouble avg = reviews.stream()
                .filter(r -> r.getScore() != null)
                .mapToDouble(AppReview::getScore)
                .average();

        App app = appService.findById(appId);
        app.setVerifiedScore(avg.isPresent() ? avg.getAsDouble() : null);
        appService.save(app);
    }

    public List<AppSubReview> findSubReviews(UUID appReviewId) {
        return appSubReviewRepository.findAllByAppReviewId(appReviewId);
    }

    private int clampScore(Integer score) {
        if (score == null) return 0;
        return Math.max(0, Math.min(5, score));
    }
}