package com.vide.vibe.service;

import com.vide.vibe.model.App;
import com.vide.vibe.model.Workflow;
import com.vide.vibe.model.WorkflowStep;
import com.vide.vibe.repository.WorkflowRepository;
import com.vide.vibe.repository.WorkflowStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final AppService appService;

    // ── Workflow ──────────────────────────────────────────────────────────────

    public List<Workflow> findByAppId(UUID appId) {
        return workflowRepository.findAllByAppIdAndDeletedAtIsNullOrderByPositionAsc(appId);
    }

    public Workflow findById(UUID id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + id));
    }

    @Transactional
    public Workflow create(UUID appId, Workflow workflow) {
        App app = appService.findById(appId);
        workflow.setApp(app);
        List<Workflow> existing = findByAppId(appId);
        workflow.setPosition(existing.size() + 1);
        return workflowRepository.save(workflow);
    }

    /**
     * Save a workflow entity directly — used when caller has already
     * mutated only safe fields (title, description) and must not
     * risk overwriting position with null.
     */
    @Transactional
    public Workflow saveDirectly(Workflow workflow) {
        return workflowRepository.save(workflow);
    }

    /**
     * Full update — only used from admin flows that supply all fields.
     * Never called from the manage page to avoid position=null NPE.
     */
    @Transactional
    public Workflow update(UUID id, Workflow updated) {
        Workflow existing = findById(id);
        if (updated.getTitle() != null && !updated.getTitle().isBlank())
            existing.setTitle(updated.getTitle());
        if (updated.getDescription() != null)
            existing.setDescription(updated.getDescription());
        if (updated.getPosition() != null)
            existing.setPosition(updated.getPosition());
        if (updated.getIsFeatured() != null)
            existing.setIsFeatured(updated.getIsFeatured());
        return workflowRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        Workflow workflow = findById(id);
        workflow.softDelete();
        workflowRepository.save(workflow);
    }

    // ── Workflow Step ─────────────────────────────────────────────────────────

    public List<WorkflowStep> findStepsByWorkflowId(UUID workflowId) {
        return workflowStepRepository.findAllByWorkflowIdAndDeletedAtIsNullOrderByPositionAsc(workflowId);
    }

    public WorkflowStep findStepById(UUID id) {
        return workflowStepRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("WorkflowStep not found: " + id));
    }

    @Transactional
    public WorkflowStep createStep(UUID workflowId, WorkflowStep step) {
        Workflow workflow = findById(workflowId);
        step.setWorkflow(workflow);
        List<WorkflowStep> existing = findStepsByWorkflowId(workflowId);
        step.setPosition(existing.size() + 1);
        return workflowStepRepository.save(step);
    }

    @Transactional
    public WorkflowStep updateStep(UUID id, WorkflowStep updated) {
        WorkflowStep existing = findStepById(id);
        if (updated.getText() != null)     existing.setText(updated.getText());
        if (updated.getPosition() != null) existing.setPosition(updated.getPosition());
        return workflowStepRepository.save(existing);
    }

    @Transactional
    public void deleteStep(UUID id) {
        WorkflowStep step = findStepById(id);
        step.softDelete();
        workflowStepRepository.save(step);
    }
}