package com.vide.vibe.repository;

import com.vide.vibe.model.App;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Same visibility rule as the Explore page: every non-deleted app that is not REJECTED.
 * (Pass App.Status.REJECTED as "rejected".)
 */
public interface AppOptRepository extends JpaRepository<App, UUID> {

    /**
     * pattern is already lower-cased and LIKE-escaped with '!' (see WishController.escapeLike).
     * "abc%"  -> prefix match (can use an index on lower(name))
     * "%abc%" -> contains match
     */
    @Query("select a.id as id, a.name as name, a.slug as slug, a.iconUrl as iconUrl from App a " +
           "where a.deletedAt is null and a.status <> :rejected " +
           "and lower(a.name) like :pattern escape '!' order by a.name")
    List<AppOptView> searchLike(@Param("pattern") String pattern,
                                @Param("rejected") App.Status rejected,
                                Pageable pageable);

    @Query("select a.id as id, a.name as name, a.slug as slug, a.iconUrl as iconUrl from App a " +
           "where a.id = :id and a.deletedAt is null and a.status <> :rejected")
    Optional<AppOptView> findOpt(@Param("id") UUID id, @Param("rejected") App.Status rejected);

    @Query("select a.id as id, a.name as name, a.slug as slug, a.iconUrl as iconUrl from App a " +
           "where lower(a.name) = lower(:name) and a.deletedAt is null and a.status <> :rejected order by a.name")
    List<AppOptView> findByNameIgnoreCase(@Param("name") String name,
                                          @Param("rejected") App.Status rejected,
                                          Pageable pageable);
}