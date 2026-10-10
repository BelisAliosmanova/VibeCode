package com.vide.vibe.repository;

import com.vide.vibe.model.WishRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WishRequestRepository extends JpaRepository<WishRequest, Long> {
    List<WishRequest> findAllByOrderByCreatedAtDesc();
}