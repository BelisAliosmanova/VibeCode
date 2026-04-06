package com.vide.vibe.repository;

import com.vide.vibe.model.ReviewCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewCategoryRepository extends JpaRepository<ReviewCategory, UUID> {
    List<ReviewCategory> findAllByDeletedAtIsNullOrderByPositionAsc();
    boolean existsBySlugAndDeletedAtIsNull(String slug);
}