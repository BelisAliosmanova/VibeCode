package com.vide.vibe.repository;

import com.vide.vibe.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByDeletedAtIsNullOrderByPositionAsc();

    List<Category> findAllByVisibilityTrueAndDeletedAtIsNullOrderByPositionAsc();

    List<Category> findAllByFilterVisibleTrueAndDeletedAtIsNullOrderByPositionAsc();

    Optional<Category> findBySlugAndDeletedAtIsNull(String slug);
}