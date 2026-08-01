package com.vide.vibe.service;

import com.vide.vibe.model.App;
import com.vide.vibe.model.User;
import com.vide.vibe.repository.AppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AppService {

    private final AppRepository appRepository;
    private final UserService userService;

    public List<App> findAll() {
        return appRepository.findAllByDeletedAtIsNull();
    }

    private static final Pattern GITHUB_URL_PATTERN =
            Pattern.compile("^https://github\\.com/[\\w.-]+/[\\w.-]+/?$");

    private String normalizeGithubUrl(String githubUrl) {
        if (githubUrl == null || githubUrl.isBlank()) return null;
        String trimmed = githubUrl.trim();
        if (!GITHUB_URL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Please enter a valid GitHub repository URL, e.g. https://github.com/owner/repo");
        }
        return trimmed;
    }

    public App findById(UUID id) {
        return appRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("App not found: " + id));
    }

    public App findBySlug(String slug) {
        return appRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new RuntimeException("App not found: " + slug));
    }

    @Transactional
    public App create(App app, UUID ownerId) {
        User owner = userService.findById(ownerId);
        app.setOwner(owner);
        app.setSlug(generateUniqueSlug(app.getName()));
        app.setStatus(App.Status.DRAFT);
        return appRepository.save(app);
    }

    /**
     * Updates basic listing info. If ownerEmail is provided and non-blank,
     * reassigns the owner too (find-or-create by email) — used by the
     * staff-only inline owner field on the manage page.
     */
    @Transactional
    public App updateInfo(UUID id, String name, String description, String ownerEmail, String githubUrl) {
        App app = findById(id);
        if (name != null && !name.isBlank()) {
            app.setName(name);
        }
        app.setDescription(description); // allow clearing to blank/null
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            User newOwner = userService.findOrCreateByEmail(ownerEmail);
            app.setOwner(newOwner);
        }
        app.setGithubUrl(normalizeGithubUrl(githubUrl));
        return appRepository.save(app);
    }

    @Transactional
    public App save(App app) {
        return appRepository.save(app);
    }

    @Transactional
    public App update(UUID id, App updated) {
        App existing = findById(id);
        if (updated.getName() != null && !updated.getName().isBlank())
            existing.setName(updated.getName());
        if (updated.getDescription() != null)
            existing.setDescription(updated.getDescription());
        if (updated.getUrl() != null && !updated.getUrl().isBlank())
            existing.setUrl(updated.getUrl());
        if (updated.getStatus() != null)
            existing.setStatus(updated.getStatus());
        if (updated.getVisibility() != null)
            existing.setVisibility(updated.getVisibility());
        if (updated.getVersion() != null)
            existing.setVersion(updated.getVersion());
        return appRepository.save(existing);
    }

    @Transactional
    public App updateIconUrl(UUID id, String iconUrl) {
        App app = findById(id);
        app.setIconUrl(iconUrl);
        return appRepository.save(app);
    }

    @Transactional
    public App setVerifiedScore(UUID id, Double score) {
        if (score != null && (score < 0.0 || score > 5.0)) {
            throw new IllegalArgumentException("Verified score must be between 0 and 5");
        }
        App app = findById(id);
        app.setVerifiedScore(score);
        return appRepository.save(app);
    }

    /**
     * Admin-only: reassign the app's owner by email. If a user with that
     * email exists, the app is linked to them. If not, a new bare-bones
     * account is created (no password) and linked instead.
     */
    @Transactional
    public App changeOwnerByEmail(UUID appId, String email) {
        App app = findById(appId);
        User newOwner = userService.findOrCreateByEmail(email);
        app.setOwner(newOwner);
        return appRepository.save(app);
    }

    @Transactional
    public App submit(UUID id) {
        App app = findById(id);
        app.setStatus(App.Status.SUBMITTED);
        return appRepository.save(app);
    }

    @Transactional
    public App approve(UUID id) {
        App app = findById(id);
        app.setStatus(App.Status.APPROVED);
        app.setVisibility(App.Visibility.PUBLIC);
        return appRepository.save(app);
    }

    @Transactional
    public App reject(UUID id) {
        App app = findById(id);
        app.setStatus(App.Status.REJECTED);
        return appRepository.save(app);
    }

    @Transactional
    public void delete(UUID id) {
        App app = findById(id);
        app.softDelete();
        appRepository.save(app);
    }

    private String generateUniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]+", "-");
        String slug = base;
        int counter = 1;
        while (appRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }
}