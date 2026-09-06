package com.vide.vibe.service;

import com.vide.vibe.model.*;
import com.vide.vibe.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewCategoryRepository reviewCategoryRepository;
    private final ReviewSubCategoryRepository reviewSubCategoryRepository;
    private final AppReviewSubmissionRepository appReviewSubmissionRepository;
    private final AppReviewRepository appReviewRepository;
    private final AppSubReviewRepository appSubReviewRepository;
    private final AppService appService;

    // ── Review Categories ────────────────────────────────────────────────
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

    // ── Review Sub-Categories ─────────────────────────────────────────────
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

    @Transactional
    public AppReviewSubmission submitReview(UUID appId,
                                            String submitterName,
                                            Map<UUID, Map<UUID, Integer>> scoresByCategory,
                                            Map<UUID, Map<UUID, String>> descriptionsByCategory) {

        boolean hasAnyScore = scoresByCategory != null &&
                scoresByCategory.values().stream().anyMatch(m -> m != null && !m.isEmpty());
        if (!hasAnyScore) {
            throw new IllegalArgumentException("Please rate at least one item before submitting.");
        }

        App app = appService.findById(appId);

        AppReviewSubmission submission = AppReviewSubmission.builder()
                .app(app)
                .submitterName(blankToNull(submitterName))
                .visible(true)
                .build();
        submission = appReviewSubmissionRepository.save(submission);

        for (var catEntry : scoresByCategory.entrySet()) {
            Map<UUID, Integer> subScores = catEntry.getValue();
            if (subScores == null || subScores.isEmpty()) continue;

            UUID categoryId = catEntry.getKey();
            ReviewCategory category = findCategoryById(categoryId);
            Map<UUID, String> subDescs = descriptionsByCategory != null
                    ? descriptionsByCategory.getOrDefault(categoryId, Map.of())
                    : Map.of();

            AppReview review = appReviewRepository.save(
                    AppReview.builder().submission(submission).reviewCategory(category).build());

            List<Integer> saved = new ArrayList<>();
            for (var subEntry : subScores.entrySet()) {
                ReviewSubCategory subCat = findSubCategoryById(subEntry.getKey());
                int score = clampScore(subEntry.getValue());
                String desc = subDescs.get(subEntry.getKey());

                appSubReviewRepository.save(AppSubReview.builder()
                        .appReview(review)
                        .reviewSubCategory(subCat)
                        .score(score)
                        .description(blankToNull(desc))
                        .build());
                saved.add(score);
            }

            review.setScore(saved.stream().mapToInt(Integer::intValue).average().orElse(0.0));
            appReviewRepository.save(review);
        }

        recomputeVerifiedScore(appId);
        return submission;
    }

    public List<AppReviewSubmission> findSubmissionsForApp(UUID appId) {
        return appReviewSubmissionRepository.findAllByAppIdOrderByCreatedAtDesc(appId);
    }

    public List<AppReviewSubmission> findVisibleSubmissionsForApp(UUID appId) {
        return appReviewSubmissionRepository.findAllByAppIdAndVisibleTrueOrderByCreatedAtDesc(appId);
    }

    /** Admin-only in the controller layer — deletes one person's whole review. */
    @Transactional
    public void deleteSubmission(UUID submissionId) {
        AppReviewSubmission submission = appReviewSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Review not found: " + submissionId));
        UUID appId = submission.getApp().getId();
        appReviewSubmissionRepository.delete(submission); // cascades to AppReview + AppSubReview
        recomputeVerifiedScore(appId);
    }

    /** One averaged score per category across all visible submissions — for the app page's review chips. */
    public List<CategoryScoreView> categoryAverages(UUID appId) {
        List<AppReview> reviews = appReviewRepository.findAllVisibleByAppId(appId);

        Map<ReviewCategory, List<Double>> byCategory = reviews.stream()
                .filter(r -> r.getScore() != null)
                .collect(Collectors.groupingBy(AppReview::getReviewCategory,
                        Collectors.mapping(AppReview::getScore, Collectors.toList())));

        return byCategory.entrySet().stream()
                .map(e -> new CategoryScoreView(e.getKey(),
                        e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0)))
                .sorted(Comparator.comparing(v -> v.reviewCategory().getPosition()))
                .toList();
    }

    private void recomputeVerifiedScore(UUID appId) {
        List<AppReview> reviews = appReviewRepository.findAllVisibleByAppId(appId);
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
        if (score == null) return 1;
        return Math.max(1, Math.min(5, score));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}