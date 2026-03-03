package com.vide.vibe.repository;

import com.vide.vibe.model.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
    List<Workflow> findAllByAppIdAndDeletedAtIsNullOrderByPositionAsc(UUID appId);
}