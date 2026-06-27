package com.vide.vibe.controller;

import com.vide.vibe.model.Workflow;
import com.vide.vibe.model.WorkflowStep;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.WorkflowService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/apps/{appId}/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final AppService appService;

    public WorkflowController(WorkflowService workflowService, AppService appService) {
        this.workflowService = workflowService;
        this.appService = appService;
    }

    @PreAuthorize("@appSecurity.canEdit(#appId, authentication)")
    @GetMapping
    public String list(@PathVariable UUID appId, Model model) {
        model.addAttribute("app", appService.findById(appId));
        model.addAttribute("workflows", workflowService.findByAppId(appId));
        return "workflows/list";
    }

    @PreAuthorize("@appSecurity.canEdit(#appId, authentication)")
    @GetMapping("/new")
    public String createForm(@PathVariable UUID appId, Model model) {
        model.addAttribute("app", appService.findById(appId));
        model.addAttribute("workflow", new Workflow());
        return "workflows/form";
    }

    @PreAuthorize("@appSecurity.canEdit(#appId, authentication)")
    @PostMapping
    public String create(@PathVariable UUID appId, @ModelAttribute Workflow workflow) {
        workflowService.create(appId, workflow);
        return "redirect:/apps/" + appId + "/workflows";
    }

    @PreAuthorize("@appSecurity.canEdit(#appId, authentication)")
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID appId,
                           @PathVariable UUID id, Model model) {
        model.addAttribute("app", appService.findById(appId));
        model.addAttribute("workflow", workflowService.findById(id));
        return "workflows/form";
    }

    @PreAuthorize("@appSecurity.canEdit(#appId, authentication)")
    @PostMapping("/{id}")
    public String update(@PathVariable UUID appId,
                         @PathVariable UUID id,
                         @ModelAttribute Workflow workflow) {
        workflowService.update(id, workflow);
        return "redirect:/apps/" + appId + "/workflows";
    }

    @PreAuthorize("@appSecurity.canEdit(#appId, authentication)")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID appId, @PathVariable UUID id) {
        workflowService.delete(id);
        return "redirect:/apps/" + appId + "/workflows";
    }

    @PreAuthorize("@appSecurity.canEdit(#appId, authentication)")
    @GetMapping("/{id}/steps")
    public String steps(@PathVariable UUID appId,
                        @PathVariable UUID id, Model model) {
        model.addAttribute("app", appService.findById(appId));
        model.addAttribute("workflow", workflowService.findById(id));
        model.addAttribute("steps", workflowService.findStepsByWorkflowId(id));
        model.addAttribute("newStep", new WorkflowStep());
        return "workflows/steps";
    }

    @PreAuthorize("@appSecurity.canEdit(#appId, authentication)")
    @PostMapping("/{id}/steps")
    public String createStep(@PathVariable UUID appId,
                             @PathVariable UUID id,
                             @ModelAttribute WorkflowStep step) {
        workflowService.createStep(id, step);
        return "redirect:/apps/" + appId + "/workflows/" + id + "/steps";
    }

    @PreAuthorize("@appSecurity.canEdit(#appId, authentication)")
    @PostMapping("/{workflowId}/steps/{stepId}/delete")
    public String deleteStep(@PathVariable UUID appId,
                             @PathVariable UUID workflowId,
                             @PathVariable UUID stepId) {
        workflowService.deleteStep(stepId);
        return "redirect:/apps/" + appId + "/workflows/" + workflowId + "/steps";
    }
}