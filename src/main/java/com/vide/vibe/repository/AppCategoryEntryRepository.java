package com.vide.vibe.repository;

import com.vide.vibe.model.AppCategoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppCategoryEntryRepository extends JpaRepository<AppCategoryEntry, UUID> {

    // All selections for an app (all categories combined)
    List<AppCategoryEntry> findAllByAppId(UUID appId);

    // All selections for an app within a specific category
    @Query("""
        SELECT ace FROM AppCategoryEntry ace
        WHERE ace.app.id = :appId
          AND ace.entry.category.id = :categoryId
        """)
    List<AppCategoryEntry> findByAppIdAndCategoryId(UUID appId, UUID categoryId);

    // Check if an app already selected a specific entry
    boolean existsByAppIdAndEntryId(UUID appId, UUID entryId);

    // Remove all selections for an app within a specific category
    // (used when user re-submits a step)
    @Modifying
    @Query("""
        DELETE FROM AppCategoryEntry ace
        WHERE ace.app.id = :appId
          AND ace.entry.category.id = :categoryId
        """)
    void deleteByAppIdAndCategoryId(UUID appId, UUID categoryId);

    // Remove a specific selection
    void deleteByAppIdAndEntryId(UUID appId, UUID entryId);
}