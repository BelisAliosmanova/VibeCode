package com.vide.vibe.repository;

import com.vide.vibe.model.App;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppRepository extends JpaRepository<App, UUID> {
    Optional<App> findBySlugAndDeletedAtIsNull(String slug);
    List<App> findAllByDeletedAtIsNull();
    List<App> findAllByOwnerIdAndDeletedAtIsNull(UUID ownerId);
    boolean existsBySlugAndDeletedAtIsNull(String slug);
}