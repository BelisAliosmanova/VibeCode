package com.vide.vibe.repository;

import com.vide.vibe.model.HomeSectionApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HomeSectionAppRepository extends JpaRepository<HomeSectionApp, UUID> {

    List<HomeSectionApp> findAllByHomeSectionIdOrderByPositionAsc(UUID homeSectionId);

    @Modifying
    @Query("DELETE FROM HomeSectionApp hsa WHERE hsa.homeSection.id = :homeSectionId")
    void deleteAllByHomeSectionId(UUID homeSectionId);
}