package com.vide.vibe.service;

import com.vide.vibe.model.App;
import com.vide.vibe.model.WishRequest;
import com.vide.vibe.repository.AppRepository;
import com.vide.vibe.repository.WishRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WishRequestService {

    /** Shown as suggestions until real wishes provide better ones. */
    private static final List<String> CURATED = List.of(
            "local storage", "one time purchase", "offline mode", "no ads", "open source",
            "cross-platform", "dark mode", "privacy first", "plugins", "free");

    /** Per-app summary used by the "all wishes" and "wish an app like X" pages. */
    @lombok.Value
    public static class AppWishStats {
        App app;
        long count;
        List<String> topFeatures;
    }

    private final WishRequestRepository repo;
    private final AppRepository appRepository;

    public WishRequestService(WishRequestRepository repo, AppRepository appRepository) {
        this.repo = repo;
        this.appRepository = appRepository;
    }

    @Transactional
    public WishRequest submit(UUID appId, String customAppName, String description, String email, Set<String> features) {
        WishRequest w = new WishRequest();
        w.setApp(appId == null ? null : appRepository.findById(appId).orElse(null));
        w.setCustomAppName(customAppName == null || customAppName.isBlank() ? null : customAppName.trim());
        w.setDescription(description == null ? "" : description.trim());
        w.setEmail(email.trim());
        w.setFeatures(features);
        return repo.save(w);
    }

    /** Quick "+1": adds one to the count of an existing app. Returns false if this email already voted for it. */
    @Transactional
    public boolean plusOne(UUID appId, String email, Set<String> features) {
        if (repo.existsByAppIdAndEmailIgnoreCase(appId, email.trim())) return false;
        submit(appId, null, "", email, features);
        return true;
    }

    /** "a, B ,c" -> [a, b, c] (lowercase, max 10, max 40 chars each). */
    public static Set<String> parseFeatures(String csv) {
        if (csv == null || csv.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty() && s.length() <= 40)
                .limit(10)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public List<WishRequest> findAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AppWishStats> rankedStats() {
        return repo.findAllWithApp().stream()
                .collect(Collectors.groupingBy(w -> w.getApp().getId()))
                .values().stream()
                .map(list -> new AppWishStats(list.get(0).getApp(), list.size(), topFeatures(list, 10)))
                .sorted(Comparator.comparingLong(AppWishStats::getCount).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, AppWishStats> statsByAppId() {
        return rankedStats().stream()
                .collect(Collectors.toMap(s -> s.getApp().getId(), s -> s));
    }

    /** Top features for the form: most-wished first, padded with the curated list up to 10. */
    @Transactional(readOnly = true)
    public List<String> suggestedFeatures() {
        LinkedHashSet<String> out = new LinkedHashSet<>(topFeatures(repo.findAll(), 10));
        for (String c : CURATED) { if (out.size() >= 10) break; out.add(c); }
        return new ArrayList<>(out);
    }

    private static List<String> topFeatures(Collection<WishRequest> wishes, int limit) {
        return wishes.stream()
                .flatMap(w -> w.getFeatures().stream())
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
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