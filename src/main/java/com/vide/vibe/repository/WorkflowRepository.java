package com.vide.vibe.repository;

import com.vide.vibe.model.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
    List<Workflow> findAllByAppIdAndDeletedAtIsNullOrderByPositionAsc(UUID appId);
    @Query("SELECT DISTINCT w.app.id FROM Workflow w WHERE w.app.id IN :appIds")
    Set<UUID> findAppIdsWithWorkflows(@Param("appIds") Collection<UUID> appIds);
}