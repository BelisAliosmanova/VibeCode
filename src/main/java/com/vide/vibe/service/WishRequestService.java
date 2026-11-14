package com.vide.vibe.service;

import com.vide.vibe.model.App;
import com.vide.vibe.model.WishRequest;
import com.vide.vibe.repository.AppRepository;
import com.vide.vibe.repository.WishRequestRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WishRequestService {

    private static final UUID NONE = new UUID(0L, 0L);
    private static final int LIST_APPS = 40, LIST_CUSTOM = 40, LIST_IDEAS = 40;

    /** Numbers for one wish group (detail page). */
    @lombok.Value
    public static class AppWishStats {
        long count;
        List<String> topFeatures;
    }

    /**
     * One row of the public list / one card on a detail page.
     * kind = "app" | "custom" | "idea"; path = where its detail page lives (relative to the context path).
     */
    @lombok.Value
    public static class WishListItem {
        String kind;
        String title;
        String path;
        String iconUrl;
        String letter;
        long count;
        List<String> features;
        String description;
        LocalDateTime lastAt;
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

    /** +1 on an app. False when this email already voted for it. */
    @Transactional
    public boolean plusOne(UUID appId, String email, Set<String> features) {
        String e = clean(email);
        if (!e.isEmpty() && repo.existsByAppIdAndEmailIgnoreCase(appId, e)) return false;
        submit(appId, null, "", e, features);
        return true;
    }

    /** +1 on "like <typed name>". */
    @Transactional
    public boolean plusOneCustom(String displayName, String email, Set<String> features) {
        String e = clean(email);
        if (!e.isEmpty() && repo.existsCustomVote(key(displayName), e.toLowerCase(Locale.ROOT))) return false;
        submit(null, displayName, "", e, features);
        return true;
    }

    /** +1 on a plain wish. */
    @Transactional
    public boolean plusOneIdea(Long ideaId, String email, Set<String> features) {
        WishRequest idea = repo.findIdea(ideaId).orElse(null);
        if (idea == null) return false;
        String e = clean(email);
        if (!e.isEmpty() && (e.equalsIgnoreCase(idea.getEmail()) || repo.existsBySupportsIdAndEmailIgnoreCase(ideaId, e))) return false;
        WishRequest w = new WishRequest();
        w.setSupportsId(ideaId);
        w.setDescription("");
        w.setEmail(e);
        w.setFeatures(features);
        repo.save(w);
        return true;
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
    public static String key(String name) { return name == null ? "" : name.trim().toLowerCase(Locale.ROOT); }

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
    public List<WishRequest> findAll() { return repo.findAllByOrderByCreatedAtDesc(); }

    @Transactional
    public void toggleHandled(Long id) { repo.findById(id).ifPresent(w -> w.setHandled(!w.isHandled())); }

    @Transactional
    public void delete(Long id) {
        repo.deleteBySupportsId(id);      // +1 rows that point at this wish
        repo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long countAll() { return repo.count(); }

    @Transactional(readOnly = true)
    public long countHandled(boolean handled) { return repo.countByHandled(handled); }

    /**
     * Admin list: status = all|open|handled, type = all|app|custom|idea|votes, q = free text
     * (wish text, typed app, app name, email). Filtered and paged in the database.
     */
    @Transactional(readOnly = true)
    public Page<WishRequest> adminSearch(String status, String type, String q, int page, int size) {
        return repo.findAll(adminSpec(status, type, q),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    private static Specification<WishRequest> adminSpec(String status, String type, String q) {
        return (root, query, cb) -> {
            boolean count = query.getResultType() == Long.class || query.getResultType() == long.class;
            if (!count) root.fetch("app", JoinType.LEFT);          // load the app together with the wish (no N+1 in the list)
            List<Predicate> ps = new ArrayList<>();
            if ("open".equals(status)) ps.add(cb.isFalse(root.get("handled")));
            if ("handled".equals(status)) ps.add(cb.isTrue(root.get("handled")));
            switch (type) {
                case "app" -> ps.add(cb.isNotNull(root.get("app")));
                case "custom" -> { ps.add(cb.isNull(root.get("app"))); ps.add(cb.isNotNull(root.get("customAppName"))); }
                case "idea" -> {
                    ps.add(cb.isNull(root.get("app"))); ps.add(cb.isNull(root.get("customAppName")));
                    ps.add(cb.notEqual(root.get("description"), ""));
                }
                case "votes" -> ps.add(cb.equal(root.get("description"), ""));
                default -> { }
            }
            if (q != null && !q.isBlank()) {
                Join<WishRequest, App> app = root.join("app", JoinType.LEFT);   // separate join, only for searching by app name
                String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.<String>get("description")), like, '!'),
                        cb.like(cb.lower(root.<String>get("email")), like, '!'),
                        cb.like(cb.lower(root.<String>get("customAppName")), like, '!'),
                        cb.like(cb.lower(app.<String>get("name")), like, '!')));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** Where the detail page of this wish lives (relative to the context path). */
    public static String detailPath(WishRequest w) {
        if (w.getApp() != null) return "/wish/like/" + w.getApp().getSlug();
        if (w.getCustomAppName() != null) return "/wish/similar?name=" + URLEncoder.encode(w.getCustomAppName(), StandardCharsets.UTF_8);
        return "/wish/about/" + w.getId();
    }

    // ── detail pages ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public AppWishStats statsFor(UUID appId) {
        long count = repo.countByAppId(appId);
        List<String> features = count == 0 ? List.of()
                : repo.featureCountsForApp(appId, PageRequest.of(0, 10)).stream().map(r -> (String) r[0]).toList();
        return new AppWishStats(count, features);
    }

    @Transactional(readOnly = true)
    public AppWishStats statsForCustom(String key) {
        long count = repo.customCount(key);
        List<String> features = count == 0 ? List.of()
                : repo.customFeatureCounts(key, PageRequest.of(0, 10)).stream().map(r -> (String) r[0]).toList();
        return new AppWishStats(count, features);
    }

    @Transactional(readOnly = true)
    public String customDisplayName(String key) { return repo.customDisplayName(key); }

    @Transactional(readOnly = true)
    public List<String> customDescriptions(String key, int limit) {
        return repo.customDescriptions(key, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public Optional<WishRequest> findIdea(Long id) { return repo.findIdea(id); }

    @Transactional(readOnly = true)
    public long ideaCount(Long id) { return 1 + repo.countBySupportsId(id); }

    /** Other wishes shown under a detail page: everything that has wishes, most wished first, without the current one. */
    @Transactional(readOnly = true)
    public List<WishListItem> others(String excludePath, int limit) {
        return publicList("top").stream().filter(i -> !i.getPath().equals(excludePath)).limit(limit).toList();
    }

    // ── public "All app wishes" page ──────────────────────────────────────────
    /**
     * sort = "top"  : most wished first, ties: most recent first
     *        "new"  : most recently wished first
     */
    @Transactional(readOnly = true)
    public List<WishListItem> publicList(String sort) {
        boolean newest = "new".equals(sort);
        List<WishListItem> out = new ArrayList<>(appItems(newest
                ? repo.newestWishedAppIds(App.Status.REJECTED, NONE, PageRequest.of(0, LIST_APPS))
                : repo.topWishedAppIds(App.Status.REJECTED, NONE, PageRequest.of(0, LIST_APPS))));
        out.addAll(customItems(newest
                ? repo.newestCustomGroups(PageRequest.of(0, LIST_CUSTOM))
                : repo.customGroups(PageRequest.of(0, LIST_CUSTOM))));

        List<WishRequest> ideas = repo.ideas(PageRequest.of(0, LIST_IDEAS));
        Map<Long, Long> support = new HashMap<>();
        if (!ideas.isEmpty()) {
            for (Object[] r : repo.supportCounts(ideas.stream().map(WishRequest::getId).toList())) {
                support.put((Long) r[0], (Long) r[1]);
            }
        }
        for (WishRequest w : ideas) {
            String d = w.getDescription().replaceAll("\\s+", " ").trim();
            out.add(new WishListItem("idea", d.length() > 80 ? d.substring(0, 77) + "…" : d,
                    "/wish/about/" + w.getId(), null, "✦", 1 + support.getOrDefault(w.getId(), 0L),
                    new ArrayList<>(w.getFeatures()), w.getDescription(), w.getCreatedAt()));
        }

        Comparator<WishListItem> byLast = Comparator.comparing(WishListItem::getLastAt,
                Comparator.nullsLast(Comparator.reverseOrder()));
        out.sort(newest ? byLast : Comparator.comparingLong(WishListItem::getCount).reversed().thenComparing(byLast));
        return out;
    }

    // ── features shown on the form ────────────────────────────────────────────
    /** The category-based suggestions first, padded with what people typed themselves, max 10. */
    @Transactional(readOnly = true)
    public List<String> suggestedFeatures(List<String> catalogTop) {
        LinkedHashSet<String> out = new LinkedHashSet<>(catalogTop);
        if (out.size() < 10) {
            for (Object[] r : repo.topFeatureCounts(PageRequest.of(0, 10))) {
                if (out.size() >= 10) break;
                out.add((String) r[0]);
            }
        }
        return out.stream().limit(10).toList();
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
            out.add(new WishListItem("app", a.getName(), "/wish/like/" + a.getSlug(), a.getIconUrl(),
                    a.getName().substring(0, 1).toUpperCase(Locale.ROOT), (Long) r[1],
                    feats.getOrDefault(a.getId(), List.of()), a.getDescription(), (LocalDateTime) r[2]));
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
            out.add(new WishListItem("custom", display,
                    "/wish/similar?name=" + URLEncoder.encode(display, StandardCharsets.UTF_8), null,
                    display.substring(0, 1).toUpperCase(Locale.ROOT), (Long) r[2],
                    feats.getOrDefault((String) r[0], List.of()), null, (LocalDateTime) r[3]));
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