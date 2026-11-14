package com.vide.vibe.service;

import com.vide.vibe.model.App;
import com.vide.vibe.model.WishRequest;
import com.vide.vibe.repository.AppRepository;
import com.vide.vibe.repository.WishRequestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WishRequestService {

    private static final UUID NONE = new UUID(0L, 0L);
    private static final int LIST_APPS = 40, LIST_CUSTOM = 40, LIST_IDEAS = 40;

    /** Shown as suggestions until real wishes provide better ones. */
    private static final List<String> CURATED = List.of(
            "local storage", "one time purchase", "offline mode", "no ads", "open source",
            "cross-platform", "dark mode", "privacy first", "plugins", "free");

    /** Numbers for one app ("wish an app like X" page). */
    @lombok.Value
    public static class AppWishStats {
        long count;
        List<String> topFeatures;
    }

    /**
     * One row of the public "All app wishes" list.
     * kind = "app"    -> like one of our apps (slug set, links to its detail page)
     *        "custom" -> like an app that is not in our catalog (free text)
     *        "idea"   -> a plain wish with no "like" (description is the wish)
     */
    @lombok.Value
    public static class WishListItem {
        String kind;
        String title;
        String slug;
        String iconUrl;
        String letter;
        long count;
        List<String> features;
        String description;
    }

    private final WishRequestRepository repo;
    private final AppRepository appRepository;

    public WishRequestService(WishRequestRepository repo, AppRepository appRepository) {
        this.repo = repo;
        this.appRepository = appRepository;
    }

    // ── writes ────────────────────────────────────────────────────────────────
    @Transactional
    public WishRequest submit(UUID appId, String customAppName, String description, String email, Set<String> features) {
        WishRequest w = new WishRequest();
        w.setApp(appId == null ? null : appRepository.findById(appId).orElse(null));
        w.setCustomAppName(customAppName == null || customAppName.isBlank() ? null : customAppName.trim());
        w.setDescription(description == null ? "" : description.trim());
        w.setEmail(email == null ? "" : email.trim());     // email is optional
        w.setFeatures(features);
        return repo.save(w);
    }

    /** Quick "+1". Returns false when this email already voted for the app (anonymous votes can't be checked here). */
    @Transactional
    public boolean plusOne(UUID appId, String email, Set<String> features) {
        String e = email == null ? "" : email.trim();
        if (!e.isEmpty() && repo.existsByAppIdAndEmailIgnoreCase(appId, e)) return false;
        submit(appId, null, "", e, features);
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

    // ── admin ─────────────────────────────────────────────────────────────────
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

    // ── "Wish an app like X" page ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public AppWishStats statsFor(UUID appId) {
        long count = repo.countByAppId(appId);
        List<String> features = count == 0 ? List.of()
                : repo.featureCountsForApp(appId, PageRequest.of(0, 10)).stream().map(r -> (String) r[0]).toList();
        return new AppWishStats(count, features);
    }

    /** The most wished apps (only apps with at least one wish), optionally without one app. */
    @Transactional(readOnly = true)
    public List<WishListItem> topWishedApps(UUID exclude, int limit) {
        return appItems(repo.topWishedAppIds(App.Status.APPROVED, App.Visibility.PUBLIC,
                exclude == null ? NONE : exclude, PageRequest.of(0, limit)));
    }

    // ── public "All app wishes" page ──────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<WishListItem> publicList() {
        List<WishListItem> groups = new ArrayList<>(
                appItems(repo.topWishedAppIds(App.Status.APPROVED, App.Visibility.PUBLIC, NONE, PageRequest.of(0, LIST_APPS))));
        groups.addAll(customItems(repo.customGroups(PageRequest.of(0, LIST_CUSTOM))));
        groups.sort(Comparator.comparingLong(WishListItem::getCount).reversed());   // stable: keeps app-before-custom on ties

        List<WishListItem> out = new ArrayList<>(groups);
        for (WishRequest w : repo.ideas(PageRequest.of(0, LIST_IDEAS))) {
            String d = w.getDescription().replaceAll("\\s+", " ").trim();
            out.add(new WishListItem("idea", d.length() > 80 ? d.substring(0, 77) + "…" : d, null, null, "✦", 1,
                    new ArrayList<>(w.getFeatures()), w.getDescription()));
        }
        return out;
    }

    // ── features suggested on the form ───────────────────────────────────────
    /** Most-wished first (one aggregate query), padded with the curated list up to 10. */
    @Transactional(readOnly = true)
    public List<String> suggestedFeatures() {
        LinkedHashSet<String> out = repo.topFeatureCounts(PageRequest.of(0, 10)).stream()
                .map(r -> (String) r[0])
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String c : CURATED) { if (out.size() >= 10) break; out.add(c); }
        return new ArrayList<>(out);
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private List<WishListItem> appItems(List<Object[]> rows) {
        if (rows.isEmpty()) return List.of();
        List<UUID> ids = rows.stream().map(r -> (UUID) r[0]).toList();
        Map<UUID, App> apps = appRepository.findAllById(ids).stream().collect(Collectors.toMap(App::getId, Function.identity()));
        Map<UUID, List<String>> feats = topPerKey(repo.featureCountsForApps(ids));

        List<WishListItem> out = new ArrayList<>();
        for (Object[] r : rows) {
            App a = apps.get((UUID) r[0]);
            if (a == null) continue;
            out.add(new WishListItem("app", a.getName(), a.getSlug(), a.getIconUrl(),
                    a.getName().substring(0, 1).toUpperCase(Locale.ROOT), (Long) r[1],
                    feats.getOrDefault(a.getId(), List.of()), a.getDescription()));
        }
        return out;
    }

    private List<WishListItem> customItems(List<Object[]> rows) {
        if (rows.isEmpty()) return List.of();
        List<String> keys = rows.stream().map(r -> (String) r[0]).toList();
        Map<String, List<String>> feats = topPerKey(repo.featureCountsForCustom(keys));

        List<WishListItem> out = new ArrayList<>();
        for (Object[] r : rows) {
            String display = (String) r[1];
            out.add(new WishListItem("custom", display, null, null,
                    display.substring(0, 1).toUpperCase(Locale.ROOT), (Long) r[2],
                    feats.getOrDefault((String) r[0], List.of()), null));
        }
        return out;
    }

    /** Rows are [key, feature, count]; returns key -> its 10 most wished features. */
    @SuppressWarnings("unchecked")
    private static <K> Map<K, List<String>> topPerKey(List<Object[]> rows) {
        Map<K, List<Object[]>> by = new HashMap<>();
        for (Object[] r : rows) by.computeIfAbsent((K) r[0], k -> new ArrayList<>()).add(r);
        Map<K, List<String>> out = new HashMap<>();
        by.forEach((k, list) -> out.put(k, list.stream()
                .sorted((a, b) -> {
                    int c = Long.compare((Long) b[2], (Long) a[2]);
                    return c != 0 ? c : ((String) a[1]).compareTo((String) b[1]);
                })
                .limit(10)
                .map(r -> (String) r[1])
                .toList()));
        return out;
    }
}