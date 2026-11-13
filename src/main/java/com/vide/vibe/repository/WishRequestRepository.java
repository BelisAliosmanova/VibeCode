package com.vide.vibe.repository;

import com.vide.vibe.model.WishRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WishRequestRepository extends JpaRepository<WishRequest, Long> {

    List<WishRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByAppIdAndEmailIgnoreCase(java.util.UUID appId, String email);

    /** Wishes that are "like" an existing (non-deleted) app. */
    @Query("select distinct w from WishRequest w join fetch w.app a where a.deletedAt is null")
    List<WishRequest> findAllWithApp();
}