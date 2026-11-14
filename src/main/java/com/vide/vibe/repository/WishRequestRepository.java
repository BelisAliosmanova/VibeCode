package com.vide.vibe.repository;

import com.vide.vibe.model.App;
import com.vide.vibe.model.WishRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the public pages need is aggregated in the database and limited with a Pageable,
 * so nothing here loads "all wishes" or "all apps" into memory.
 *
 * Three kinds of wishes:
 *   app    - linked to one of our apps ("like Photoshop")
 *   custom - "like <typed name>" where the app is not in our catalog
 *   idea   - no "like" at all; other people can +1 it (rows with supportsId = idea id)
 */
public interface WishRequestRepository extends JpaRepository<WishRequest, Long>, JpaSpecificationExecutor<WishRequest> {

    List<WishRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByAppIdAndEmailIgnoreCase(UUID appId, String email);
    boolean existsBySupportsIdAndEmailIgnoreCase(Long supportsId, String email);
    long countByAppId(UUID appId);
    long countBySupportsId(Long supportsId);
    long countByHandled(boolean handled);
    void deleteBySupportsId(Long supportsId);

    // ── app ──────────────────────────────────────────────────────────────────
    /** [appId, count, lastWishAt] — most wished first, ties: most recent first. Apps without wishes never appear. */
    @Query("select a.id, count(w), max(w.createdAt) from WishRequest w join w.app a " +
           "where a.deletedAt is null and a.status <> :rejected and a.id <> :exclude " +
           "group by a.id order by count(w) desc, max(w.createdAt) desc, a.id")
    List<Object[]> topWishedAppIds(@Param("rejected") App.Status rejected,
                                   @Param("exclude") UUID exclude,
                                   Pageable pageable);

    /** [appId, count, lastWishAt] — most recently wished first. */
    @Query("select a.id, count(w), max(w.createdAt) from WishRequest w join w.app a " +
           "where a.deletedAt is null and a.status <> :rejected and a.id <> :exclude " +
           "group by a.id order by max(w.createdAt) desc, a.id")
    List<Object[]> newestWishedAppIds(@Param("rejected") App.Status rejected,
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

    // ── custom (typed app name, not in our catalog). key = lower-cased trimmed name ──
    /** [key, display name, count, lastWishAt] — most wished first, ties: most recent first. */
    @Query("select lower(w.customAppName), min(w.customAppName), count(w), max(w.createdAt) from WishRequest w " +
           "where w.app is null and w.customAppName is not null " +
           "group by lower(w.customAppName) order by count(w) desc, max(w.createdAt) desc, lower(w.customAppName)")
    List<Object[]> customGroups(Pageable pageable);

    /** [key, display name, count, lastWishAt] — most recently wished first. */
    @Query("select lower(w.customAppName), min(w.customAppName), count(w), max(w.createdAt) from WishRequest w " +
           "where w.app is null and w.customAppName is not null " +
           "group by lower(w.customAppName) order by max(w.createdAt) desc, lower(w.customAppName)")
    List<Object[]> newestCustomGroups(Pageable pageable);

    /** [key, feature, count] */
    @Query("select lower(w.customAppName), f, count(w) from WishRequest w join w.features f " +
           "where w.app is null and lower(w.customAppName) in :keys group by lower(w.customAppName), f")
    List<Object[]> featureCountsForCustom(@Param("keys") Collection<String> keys);

    @Query("select count(w) from WishRequest w where w.app is null and lower(w.customAppName) = :key")
    long customCount(@Param("key") String key);

    @Query("select min(w.customAppName) from WishRequest w where w.app is null and lower(w.customAppName) = :key")
    String customDisplayName(@Param("key") String key);

    /** [feature, count] */
    @Query("select f, count(w) from WishRequest w join w.features f where w.app is null and lower(w.customAppName) = :key " +
           "group by f order by count(w) desc, f asc")
    List<Object[]> customFeatureCounts(@Param("key") String key, Pageable pageable);

    @Query("select w.description from WishRequest w where w.app is null and lower(w.customAppName) = :key " +
           "and w.description <> '' order by w.createdAt desc")
    List<String> customDescriptions(@Param("key") String key, Pageable pageable);

    @Query("select count(w) > 0 from WishRequest w where w.app is null and lower(w.customAppName) = :key and lower(w.email) = :email")
    boolean existsCustomVote(@Param("key") String key, @Param("email") String email);

    // ── ideas (plain wishes) ─────────────────────────────────────────────────
    @Query("select w from WishRequest w where w.app is null and w.customAppName is null and w.supportsId is null " +
           "and w.description <> '' order by w.createdAt desc")
    List<WishRequest> ideas(Pageable pageable);

    @Query("select w from WishRequest w where w.id = :id and w.app is null and w.customAppName is null " +
           "and w.supportsId is null and w.description <> ''")
    Optional<WishRequest> findIdea(@Param("id") Long id);

    /** [ideaId, number of +1] */
    @Query("select w.supportsId, count(w) from WishRequest w where w.supportsId in :ids group by w.supportsId")
    List<Object[]> supportCounts(@Param("ids") Collection<Long> ids);

    // ── suggestions for the form (features that people typed themselves) ─────
    /** [feature, count] rows, most wished first. */
    @Query("select f, count(w) from WishRequest w join w.features f group by f order by count(w) desc, f asc")
    List<Object[]> topFeatureCounts(Pageable pageable);
}