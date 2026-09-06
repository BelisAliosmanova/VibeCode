package com.vide.vibe.controller;

import com.vide.vibe.model.*;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/apps/{appId}/reviews")
public class ReviewSubmissionController {

    private final AppService appService;
    private final ReviewService reviewService;

    @GetMapping
    public String showReviews(@PathVariable UUID appId, Model model) {
        App app = appService.findById(appId);

        // TODO: replace with the same check used for canEdit on the manage-listing page
        // (owner match or staff role).
        boolean canEdit = false;

        List<ReviewCategory> categories = reviewService.findAllCategories();
        Map<UUID, List<ReviewSubCategory>> subCatMap = new LinkedHashMap<>();
        for (ReviewCategory cat : categories) {
            subCatMap.put(cat.getId(), reviewService.findSubCategoriesByCategoryId(cat.getId()));
        }

        model.addAttribute("app", app);
        model.addAttribute("canEdit", canEdit);
        model.addAttribute("allCategories", categories);
        model.addAttribute("subCatMap", subCatMap);
        model.addAttribute("submissions",
                canEdit ? reviewService.findSubmissionsForApp(appId)
                        : reviewService.findVisibleSubmissionsForApp(appId));

        return "reviews";
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitReview(@PathVariable UUID appId,
                                                            @RequestParam(required = false) String submitterName,
                                                            @RequestParam Map<String, String> allParams) {
        Map<UUID, Map<UUID, Integer>> scoresByCategory = new HashMap<>();
        Map<UUID, Map<UUID, String>>  descsByCategory  = new HashMap<>();

        for (ReviewCategory cat : reviewService.findAllCategories()) {
            Map<UUID, Integer> subScores = new HashMap<>();
            Map<UUID, String>  subDescs  = new HashMap<>();

            for (ReviewSubCategory sub : reviewService.findSubCategoriesByCategoryId(cat.getId())) {
                String rawScore = allParams.get("score_" + sub.getId());
                if (rawScore != null && !rawScore.isBlank() && !"0".equals(rawScore)) {
                    subScores.put(sub.getId(), Integer.parseInt(rawScore));
                }
                String rawDesc = allParams.get("desc_" + sub.getId());
                if (rawDesc != null && !rawDesc.isBlank()) {
                    subDescs.put(sub.getId(), rawDesc);
                }
            }
            if (!subScores.isEmpty()) scoresByCategory.put(cat.getId(), subScores);
            if (!subDescs.isEmpty())  descsByCategory.put(cat.getId(), subDescs);
        }

        try {
            reviewService.submitReview(appId, submitterName, scoresByCategory, descsByCategory);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{submissionId}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteReview(@PathVariable UUID appId,
                                              @PathVariable UUID submissionId) {
        // TODO: verify the caller is owner/staff for this app before allowing delete.
        // if (!currentUserCanEdit(appId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not allowed"));
        reviewService.deleteSubmission(submissionId);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}