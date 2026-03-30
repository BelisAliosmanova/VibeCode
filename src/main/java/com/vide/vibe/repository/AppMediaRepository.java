package com.vide.vibe.repository;

import com.vide.vibe.model.AppMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppMediaRepository extends JpaRepository<AppMedia, UUID> {
    List<AppMedia> findAllByAppIdOrderByPositionAsc(UUID appId);
    List<AppMedia> findAllByAppIdAndTypeOrderByPositionAsc(UUID appId, AppMedia.MediaType type);
}