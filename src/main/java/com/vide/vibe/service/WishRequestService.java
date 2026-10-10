package com.vide.vibe.service;

import com.vide.vibe.model.WishRequest;
import com.vide.vibe.repository.WishRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class WishRequestService {

    private final WishRequestRepository repo;

    public WishRequestService(WishRequestRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public WishRequest submit(String description, String budget, String email) {
        WishRequest w = new WishRequest();
        w.setDescription(description.trim());
        w.setBudget(budget == null || budget.isBlank() ? null : budget.trim());
        w.setEmail(email.trim());
        return repo.save(w);
    }

    @Transactional(readOnly = true)
    public List<WishRequest> findAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void toggleHandled(Long id) {
        repo.findById(id).ifPresent(w -> w.setHandled(!w.isHandled()));
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}