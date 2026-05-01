package com.vide.vibe.repository;

import com.vide.vibe.model.AppClaimToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppClaimTokenRepository extends JpaRepository<AppClaimToken, UUID> {

    Optional<AppClaimToken> findByToken(String token);

    /** All tokens for an app, newest first — used to check pending / last-sent status. */
    List<AppClaimToken> findAllByAppIdOrderByCreatedAtDesc(UUID appId);

    /** Latest token for an app — quick existence check. */
    Optional<AppClaimToken> findTopByAppIdOrderByCreatedAtDesc(UUID appId);
}