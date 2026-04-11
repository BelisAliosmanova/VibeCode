package com.vide.vibe.controller;

import com.vide.vibe.model.*;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
@RequestMapping("/apps/{appId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final AppService appService;

    /**
     * Admin page — view & edit certified reviews for an app.
     */
    @GetMapping
    public String manage(@PathVariable UUID appId, Model model) {
        App app = appService.findById(appId);
        List<ReviewCategory> allCategories = reviewService.findAllCategories();
        Map<UUID, Map<UUID, Integer>> subScoreMap = new LinkedHashMap<>();
        Map<UUID, Map<UUID, String>>  subDescMap  = new LinkedHashMap<>();

        // Existing reviews keyed by ReviewCategory
        Map<ReviewCategory, AppReview> existingReviews = new LinkedHashMap<>();
        // Sub-reviews keyed by ReviewCategory.id (for the template)
        Map<UUID, List<AppSubReview>> subReviewMap = new LinkedHashMap<>();
        // Sub-categories keyed by ReviewCategory.id
        Map<UUID, List<ReviewSubCategory>> subCatMap = new LinkedHashMap<>();

        for (ReviewCategory cat : allCategories) {
            List<ReviewSubCategory> subs = reviewService.findSubCategoriesByCategoryId(cat.getId());
            subCatMap.put(cat.getId(), subs);

            reviewService.findAppReview(appId, cat.getId()).ifPresent(rev -> {
                existingReviews.put(cat, rev);
                List<AppSubReview> subList = reviewService.findSubReviews(rev.getId());
                subReviewMap.put(cat.getId(), subList);

                Map<UUID, Integer> scores = new LinkedHashMap<>();
                Map<UUID, String>  descs  = new LinkedHashMap<>();
                for (AppSubReview sr : subList) {
                    scores.put(sr.getReviewSubCategory().getId(), sr.getScore());
                    descs.put(sr.getReviewSubCategory().getId(),
                            sr.getDescription() != null ? sr.getDescription() : "");
                }
                subScoreMap.put(cat.getId(), scores);
                subDescMap.put(cat.getId(),  descs);
            });
        }

        model.addAttribute("app", app);
        model.addAttribute("allCategories", allCategories);
        model.addAttribute("existingReviews", existingReviews);
        model.addAttribute("subReviewMap", subReviewMap);
        model.addAttribute("subCatMap", subCatMap);
        model.addAttribute("subScoreMap", subScoreMap);
        model.addAttribute("subDescMap",  subDescMap);

        return "review-categories/manage";
    }

    /**
     * AJAX — save / update a certified review for one review-category.
     *
     * Expects multipart form data:
     *   visible        – boolean
     *   score_{subId}  – integer 0-5 for each sub-category
     *   desc_{subId}   – optional description for sub-categories that have hasDescriptionBox=true
     */
    @PostMapping("/{catId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveReview(
            @PathVariable UUID appId,
            @PathVariable UUID catId,
            @RequestParam(defaultValue = "true") boolean visible,
            HttpServletRequest request) {
        try {
            Map<UUID, Integer> subScores        = new LinkedHashMap<>();
            Map<UUID, String>  subDescriptions  = new LinkedHashMap<>();

            for (String param : Collections.list(request.getParameterNames())) {
                if (param.startsWith("score_")) {
                    UUID subId = UUID.fromString(param.substring(6));
                    int  score = Integer.parseInt(request.getParameter(param));
                    subScores.put(subId, score);
                }
                if (param.startsWith("desc_")) {
                    UUID subId = UUID.fromString(param.substring(5));
                    subDescriptions.put(subId, request.getParameter(param));
                }
            }

            AppReview saved = reviewService.saveAppReview(
                    appId, catId, visible, subScores, subDescriptions);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok",    true);
            resp.put("score", saved.getScore() != null ? saved.getScore() : 0.0);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error",
                            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * AJAX — toggle visibility of a single AppReview.
     */
    @PostMapping("/{reviewId}/visibility")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleVisibility(
            @PathVariable UUID appId,
            @PathVariable UUID reviewId,
            @RequestParam boolean visible) {
        try {
            reviewService.updateVisibility(reviewId, visible);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error",
                            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }
}