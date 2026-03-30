package com.vide.vibe.controller;

import com.vide.vibe.model.*;
import com.vide.vibe.repository.AppMediaRepository;
import com.vide.vibe.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/apps/{appId}/manage")
@RequiredArgsConstructor
public class ManageController {

    private final AppService appService;
    private final CategoryService categoryService;
    private final WorkflowService workflowService;
    private final AppMediaRepository appMediaRepository;

    @GetMapping
    public String manage(@PathVariable UUID appId, Model model) {
        App app = appService.findById(appId);

        List<Category> categories = categoryService.findAllVisible();
        Map<Category, List<CategoryEntry>> categorySelections = new LinkedHashMap<>();
        Map<UUID, List<CategoryEntry>> allEntries = new LinkedHashMap<>();

        for (Category category : categories) {
            List<UUID> selectedIds = categoryService.findSelectedEntryIds(appId, category.getId());
            List<CategoryEntry> entries = categoryService.findVisibleEntriesByCategoryId(category.getId());
            allEntries.put(category.getId(), entries);
            categorySelections.put(category, entries.stream()
                    .filter(e -> selectedIds.contains(e.getId()))
                    .collect(Collectors.toList()));
        }

        List<Workflow> workflows = workflowService.findByAppId(appId);
        Map<UUID, List<WorkflowStep>> workflowSteps = new LinkedHashMap<>();
        for (Workflow wf : workflows) {
            workflowSteps.put(wf.getId(), workflowService.findStepsByWorkflowId(wf.getId()));
        }

        model.addAttribute("app", app);
        model.addAttribute("categories", categories);
        model.addAttribute("categorySelections", categorySelections);
        model.addAttribute("allEntries", allEntries);
        model.addAttribute("workflows", workflows);
        model.addAttribute("workflowSteps", workflowSteps);
        model.addAttribute("media", appMediaRepository.findAllByAppIdOrderByPositionAsc(appId));

        return "manage/index";
    }

    // ── App info ──────────────────────────────────────────────────────────────

    @PostMapping("/info")
    @ResponseBody
    public ResponseEntity<?> updateInfo(
            @PathVariable UUID appId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String url) {
        App patch = new App();
        patch.setName(name);
        patch.setDescription(description);
        patch.setUrl(url);
        appService.update(appId, patch);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Categories ────────────────────────────────────────────────────────────

    @PostMapping("/categories/{categoryId}")
    @ResponseBody
    public ResponseEntity<?> updateCategorySelections(
            @PathVariable UUID appId,
            @PathVariable UUID categoryId,
            @RequestParam(required = false) List<UUID> entryIds) {
        App app = appService.findById(appId);
        categoryService.saveAppSelections(app, categoryId, entryIds != null ? entryIds : List.of());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Workflows — return JSON so JS builds DOM without reload ───────────────

    @PostMapping("/workflows")
    @ResponseBody
    public ResponseEntity<?> addWorkflow(
            @PathVariable UUID appId,
            @RequestParam String title,
            @RequestParam(required = false) String description) {
        Workflow wf = new Workflow();
        wf.setTitle(title);
        wf.setDescription(description);
        Workflow saved = workflowService.create(appId, wf);
        return ResponseEntity.ok(Map.of(
                "id", saved.getId().toString(),
                "title", saved.getTitle()
        ));
    }

    @PostMapping("/workflows/{workflowId}/update")
    @ResponseBody
    public ResponseEntity<?> updateWorkflow(
            @PathVariable UUID appId,
            @PathVariable UUID workflowId,
            @RequestParam String title,
            @RequestParam(required = false) String description) {
        Workflow wf = workflowService.findById(workflowId);
        wf.setTitle(title);
        if (description != null) wf.setDescription(description);
        workflowService.update(workflowId, wf);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/workflows/{workflowId}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteWorkflow(
            @PathVariable UUID appId,
            @PathVariable UUID workflowId) {
        workflowService.delete(workflowId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Steps — return JSON so JS builds step boxes without reload ────────────

    @PostMapping("/workflows/{workflowId}/steps")
    @ResponseBody
    public ResponseEntity<?> addStep(
            @PathVariable UUID appId,
            @PathVariable UUID workflowId,
            @RequestParam String text) {
        WorkflowStep step = new WorkflowStep();
        step.setText(text);
        WorkflowStep saved = workflowService.createStep(workflowId, step);
        return ResponseEntity.ok(Map.of(
                "id", saved.getId().toString(),
                "text", saved.getText(),
                "position", saved.getPosition()
        ));
    }

    @PostMapping("/workflows/{workflowId}/steps/{stepId}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteStep(
            @PathVariable UUID appId,
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId) {
        workflowService.deleteStep(stepId);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}