package com.vide.vibe.repository;

import com.vide.vibe.model.HomeSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HomeSectionRepository extends JpaRepository<HomeSection, UUID> {
    List<HomeSection> findAllByDeletedAtIsNullOrderByPositionAsc();
}