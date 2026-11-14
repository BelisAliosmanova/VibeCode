package com.vide.vibe.repository;

import com.vide.vibe.model.App;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppOptRepository extends JpaRepository<App, UUID> {

    /**
     * pattern is already lower-cased and LIKE-escaped with '!' (see WishController.escapeLike).
     * "abc%"  -> prefix match (can use an index on lower(name))
     * "%abc%" -> contains match
     */
    @Query("select a.id as id, a.name as name, a.iconUrl as iconUrl from App a " +
           "where a.deletedAt is null and a.status = :status and a.visibility = :vis " +
           "and lower(a.name) like :pattern escape '!' order by a.name")
    List<AppOptView> searchLike(@Param("pattern") String pattern,
                                @Param("status") App.Status status,
                                @Param("vis") App.Visibility vis,
                                Pageable pageable);

    @Query("select a.id as id, a.name as name, a.iconUrl as iconUrl from App a " +
           "where a.id = :id and a.deletedAt is null and a.status = :status and a.visibility = :vis")
    Optional<AppOptView> findOpt(@Param("id") UUID id,
                                 @Param("status") App.Status status,
                                 @Param("vis") App.Visibility vis);

    @Query("select a.id as id, a.name as name, a.iconUrl as iconUrl from App a " +
           "where lower(a.name) = lower(:name) and a.deletedAt is null and a.status = :status and a.visibility = :vis " +
           "order by a.name")
    List<AppOptView> findByNameIgnoreCase(@Param("name") String name,
                                          @Param("status") App.Status status,
                                          @Param("vis") App.Visibility vis,
                                          Pageable pageable);
}