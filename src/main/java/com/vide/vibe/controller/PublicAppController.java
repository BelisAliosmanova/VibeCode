package com.vide.vibe.controller;

import com.vide.vibe.model.*;
import com.vide.vibe.repository.AppMediaRepository;
import com.vide.vibe.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequiredArgsConstructor
public class PublicAppController {

    private final AppService        appService;
    private final ReviewService     reviewService;
    private final CategoryService   categoryService;
    private final WorkflowService   workflowService;
    private final AppMediaRepository appMediaRepository;

    /**
     * Public app listing page — /p/{slug}
     */
    @GetMapping("/p/{slug}")
    public String view(@PathVariable String slug, Model model) {

        App app = appService.findBySlug(slug);

        // ── Certified reviews: one averaged score per category, across all
        //    visible guest/staff submissions ─────────────────────────────
        List<CategoryScoreView> categoryAverages = reviewService.categoryAverages(app.getId());

        // Overall certified rating — kept in sync on App.verifiedScore
        // every time a review is submitted or deleted (see ReviewService).
        double certifiedRating = app.getVerifiedScore() != null ? app.getVerifiedScore() : 0.0;

        // ── Category selections (features, pricing, "made with", etc.) ────
        List<AppCategoryEntry> allSelections = categoryService.findAllSelectionsForApp(app.getId());
        // Group entry names by category name for display
        Map<String, List<CategoryEntry>> selectionsByCategory = new LinkedHashMap<>();
        for (AppCategoryEntry ace : allSelections) {
            String catName = ace.getEntry().getCategory().getName();
            selectionsByCategory
                    .computeIfAbsent(catName, k -> new ArrayList<>())
                    .add(ace.getEntry());
        }

        // ── Media ──────────────────────────────────────────────────────────
        List<AppMedia> media = appMediaRepository.findAllByAppIdOrderByPositionAsc(app.getId());

        // ── Workflows ──────────────────────────────────────────────────────
        List<Workflow> workflows = workflowService.findByAppId(app.getId());
        Map<UUID, List<WorkflowStep>> workflowSteps = new LinkedHashMap<>();
        for (Workflow wf : workflows) {
            workflowSteps.put(wf.getId(), workflowService.findStepsByWorkflowId(wf.getId()));
        }

        model.addAttribute("app",                  app);
        model.addAttribute("categoryAverages",      categoryAverages);
        model.addAttribute("certifiedRating",       certifiedRating);
        model.addAttribute("selectionsByCategory",  selectionsByCategory);
        model.addAttribute("media",                 media);
        model.addAttribute("workflows",             workflows);
        model.addAttribute("workflowSteps",         workflowSteps);

        return "public/app";
    }
}