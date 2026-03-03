package com.vide.vibe.repository;

import com.vide.vibe.model.CategoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryEntryRepository extends JpaRepository<CategoryEntry, UUID> {

    List<CategoryEntry> findAllByCategoryIdAndDeletedAtIsNullOrderByPositionAscInterestDesc(UUID categoryId);

    List<CategoryEntry> findAllByCategoryIdAndVisibilityTrueAndDeletedAtIsNullOrderByPositionAscInterestDesc(UUID categoryId);

    boolean existsBySlugAndDeletedAtIsNull(String slug);
}