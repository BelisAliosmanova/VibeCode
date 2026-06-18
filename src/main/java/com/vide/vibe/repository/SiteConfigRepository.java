package com.vide.vibe.repository;

import com.vide.vibe.model.SiteConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteConfigRepository extends JpaRepository<SiteConfig, UUID> {
    Optional<SiteConfig> findByConfigKey(String configKey);
}