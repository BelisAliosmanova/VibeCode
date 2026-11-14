package com.vide.vibe.repository;

import com.vide.vibe.model.App;
import com.vide.vibe.model.WishRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Everything the public pages need is aggregated in the database and limited with a Pageable,
 * so nothing here loads "all wishes" or "all apps" into memory.
 */
public interface WishRequestRepository extends JpaRepository<WishRequest, Long> {

    List<WishRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByAppIdAndEmailIgnoreCase(UUID appId, String email);

    long countByAppId(UUID appId);

    // ── wishes "like" one of our apps ────────────────────────────────────────
    /** [appId, count] rows, most wished first. Only public approved apps. Pass a "none" UUID to exclude nothing. */
    @Query("select a.id, count(w) from WishRequest w join w.app a " +
            "where a.deletedAt is null and a.status = :status and a.visibility = :vis and a.id <> :exclude " +
            "group by a.id order by count(w) desc, a.id")
    List<Object[]> topWishedAppIds(@Param("status") App.Status status,
                                   @Param("vis") App.Visibility vis,
                                   @Param("exclude") UUID exclude,
                                   Pageable pageable);

    /** [appId, feature, count] for a set of apps. */
    @Query("select w.app.id, f, count(w) from WishRequest w join w.features f " +
            "where w.app.id in :ids group by w.app.id, f")
    List<Object[]> featureCountsForApps(@Param("ids") Collection<UUID> ids);

    /** [feature, count] for one app, most wished first. */
    @Query("select f, count(w) from WishRequest w join w.features f where w.app.id = :appId " +
            "group by f order by count(w) desc, f asc")
    List<Object[]> featureCountsForApp(@Param("appId") UUID appId, Pageable pageable);

    // ── wishes "like" an app that is NOT in our catalog (free text) ──────────
    /** [lowercased name, display name, count], most wished first. */
    @Query("select lower(w.customAppName), min(w.customAppName), count(w) from WishRequest w " +
            "where w.app is null and w.customAppName is not null " +
            "group by lower(w.customAppName) order by count(w) desc, lower(w.customAppName)")
    List<Object[]> customGroups(Pageable pageable);

    /** [lowercased name, feature, count] */
    @Query("select lower(w.customAppName), f, count(w) from WishRequest w join w.features f " +
            "where w.app is null and lower(w.customAppName) in :keys group by lower(w.customAppName), f")
    List<Object[]> featureCountsForCustom(@Param("keys") Collection<String> keys);

    // ── plain wishes (no "like" at all) ──────────────────────────────────────
    @Query("select w from WishRequest w where w.app is null and w.customAppName is null " +
            "and w.description <> '' order by w.createdAt desc")
    List<WishRequest> ideas(Pageable pageable);

    // ── suggestions for the form ─────────────────────────────────────────────
    /** [feature, count] rows, most wished first. */
    @Query("select f, count(w) from WishRequest w join w.features f group by f order by count(w) desc, f asc")
    List<Object[]> topFeatureCounts(Pageable pageable);
}