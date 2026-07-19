package com.vide.vibe.repository;

import com.vide.vibe.model.AppMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface AppMediaRepository extends JpaRepository<AppMedia, UUID> {
    List<AppMedia> findAllByAppIdOrderByPositionAsc(UUID appId);
    List<AppMedia> findAllByAppIdAndTypeOrderByPositionAsc(UUID appId, AppMedia.MediaType type);
    @Query("SELECT DISTINCT m.app.id FROM AppMedia m WHERE m.app.id IN :appIds AND m.type = :type")
    Set<UUID> findAppIdsWithMediaType(@Param("appIds") Collection<UUID> appIds,
                                      @Param("type") AppMedia.MediaType type);
}