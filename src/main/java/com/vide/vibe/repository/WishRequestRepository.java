package com.vide.vibe.repository;

import com.vide.vibe.model.WishRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WishRequestRepository extends JpaRepository<WishRequest, Long> {

    List<WishRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByAppIdAndEmailIgnoreCase(java.util.UUID appId, String email);

    /** Wishes that are "like" an existing (non-deleted) app. Features are fetched in the same query (no N+1). */
    @Query("select distinct w from WishRequest w join fetch w.app a left join fetch w.features where a.deletedAt is null")
    List<WishRequest> findAllWithApp();

    /** [feature, count] rows, most wished first. Pass PageRequest.of(0, n) to limit. */
    @Query("select f, count(w) from WishRequest w join w.features f group by f order by count(w) desc, f asc")
    List<Object[]> topFeatureCounts(Pageable pageable);
}