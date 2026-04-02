package com.vide.vibe.controller;

import com.vide.vibe.model.*;
import com.vide.vibe.repository.AppMediaRepository;
import com.vide.vibe.repository.AppRepository;
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
    private final AppRepository appRepository;
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

    @PostMapping("/info")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateInfo(
            @PathVariable UUID appId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String url) {
        try {
            App app = appRepository.findById(appId)
                    .orElseThrow(() -> new RuntimeException("App not found: " + appId));
            if (name != null && !name.isBlank())  app.setName(name.trim());
            if (description != null)              app.setDescription(description.trim());
            if (url != null)                      app.setUrl(url.trim().isEmpty() ? null : url.trim());
            appRepository.save(app);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    @PostMapping("/categories/{categoryId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateCategorySelections(
            @PathVariable UUID appId,
            @PathVariable UUID categoryId,
            @RequestParam(required = false) List<UUID> entryIds) {
        try {
            App app = appRepository.findById(appId)
                    .orElseThrow(() -> new RuntimeException("App not found: " + appId));
            categoryService.saveAppSelections(app, categoryId, entryIds != null ? entryIds : List.of());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    @PostMapping("/workflows")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addWorkflow(
            @PathVariable UUID appId,
            @RequestParam String title,
            @RequestParam(required = false) String description) {
        try {
            Workflow wf = new Workflow();
            wf.setTitle(title.trim());
            if (description != null) wf.setDescription(description);
            Workflow saved = workflowService.create(appId, wf);
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId().toString(),
                    "title", saved.getTitle()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    @PostMapping("/workflows/{workflowId}/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateWorkflow(
            @PathVariable UUID appId,
            @PathVariable UUID workflowId,
            @RequestParam String title,
            @RequestParam(required = false) String description) {
        try {
            Workflow wf = workflowService.findById(workflowId);
            if (title != null && !title.isBlank()) wf.setTitle(title.trim());
            if (description != null) wf.setDescription(description);
            workflowService.saveDirectly(wf);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    @PostMapping("/workflows/{workflowId}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteWorkflow(
            @PathVariable UUID appId,
            @PathVariable UUID workflowId) {
        try {
            workflowService.delete(workflowId);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    @PostMapping("/workflows/{workflowId}/steps")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addStep(
            @PathVariable UUID appId,
            @PathVariable UUID workflowId,
            @RequestParam String text) {
        try {
            if (text.length() > 70)
                return ResponseEntity.badRequest().body(Map.of("error", "Max 70 characters"));
            WorkflowStep step = new WorkflowStep();
            step.setText(text.trim());
            WorkflowStep saved = workflowService.createStep(workflowId, step);
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId().toString(),
                    "text", saved.getText(),
                    "position", saved.getPosition()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    @PostMapping("/workflows/{workflowId}/steps/{stepId}/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateStep(
            @PathVariable UUID appId,
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId,
            @RequestParam String text) {
        try {
            if (text == null || text.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Text cannot be empty"));
            if (text.length() > 70)
                return ResponseEntity.badRequest().body(Map.of("error", "Max 70 characters"));
            WorkflowStep step = workflowService.findStepById(stepId);
            step.setText(text.trim());
            workflowService.saveStep(step);
            return ResponseEntity.ok(Map.of("ok", true, "text", step.getText()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    @PostMapping("/workflows/{workflowId}/steps/{stepId}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteStep(
            @PathVariable UUID appId,
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId) {
        try {
            workflowService.deleteStep(stepId);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }
}