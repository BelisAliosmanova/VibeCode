package com.vide.vibe.repository;

import com.vide.vibe.model.App;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lightweight, DB-side queries for the "An app like..." dropdown.
 * Separate from AppRepository so you don't have to merge anything into it.
 */
public interface AppOptRepository extends Repository<App, UUID> {

    /** Prefix matches first, then contains matches. Empty q returns everything (paged). */
    @Query("""
        select a.id as id, a.name as name, a.iconUrl as iconUrl
        from App a
        where a.status = :status and a.visibility = :vis
          and (:q = '' or lower(a.name) like concat('%', :q, '%') escape '\\')
        order by case when lower(a.name) like concat(:q, '%') escape '\\' then 0 else 1 end,
                 lower(a.name)
        """)
    List<AppOptView> search(@Param("q") String q,
                            @Param("status") App.Status status,
                            @Param("vis") App.Visibility vis,
                            Pageable page);

    @Query("""
        select a.id as id, a.name as name, a.iconUrl as iconUrl
        from App a
        where a.status = :status and a.visibility = :vis and lower(a.name) = lower(:name)
        """)
    List<AppOptView> findByName(@Param("name") String name,
                                @Param("status") App.Status status,
                                @Param("vis") App.Visibility vis,
                                Pageable page);

    @Query("""
        select a.id as id, a.name as name, a.iconUrl as iconUrl
        from App a
        where a.id = :id and a.status = :status and a.visibility = :vis
        """)
    Optional<AppOptView> findById(@Param("id") UUID id,
                                  @Param("status") App.Status status,
                                  @Param("vis") App.Visibility vis);
}