package com.vide.vibe.repository;

import com.vide.vibe.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // All non-deleted categories ordered by position — used for submit flow
    List<Category> findAllByDeletedAtIsNullOrderByPositionAsc();

    // Only visible ones — shown to regular users
    List<Category> findAllByVisibilityTrueAndDeletedAtIsNullOrderByPositionAsc();

    Optional<Category> findBySlugAndDeletedAtIsNull(String slug);
}