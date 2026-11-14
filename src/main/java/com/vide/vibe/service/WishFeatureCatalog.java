package com.vide.vibe.service;

import com.vide.vibe.model.Category;
import com.vide.vibe.model.CategoryEntry;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Feature suggestions for the wish form, taken from the same category entries that Explore uses as filters
 * (visible entries of the filter-visible categories), most used first. Cached for a few minutes so the wish
 * page does not hit the category tables on every request.
 */
@Component
public class WishFeatureCatalog {

    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final String MADE_WITH_CATEGORY = "Made with";   // tools used to build an app, not a feature

    private record Entry(String name, long count, int interest) {}

    private final CategoryService categoryService;
    private volatile List<Entry> cache = List.of();
    private volatile long loadedAt = 0;

    public WishFeatureCatalog(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** The n most used features (lowercase names). */
    public List<String> top(int n) {
        return entries().stream().limit(n).map(Entry::name).toList();
    }

    /** Features containing q (prefix matches first), max n. */
    public List<String> search(String q, int n) {
        String s = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return top(n);
        List<String> starts = new ArrayList<>(), has = new ArrayList<>();
        for (Entry e : entries()) {
            if (e.name().startsWith(s)) starts.add(e.name());
            else if (e.name().contains(s)) has.add(e.name());
        }
        starts.addAll(has);
        return starts.size() > n ? starts.subList(0, n) : starts;
    }

    private List<Entry> entries() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > TTL_MS || cache.isEmpty() && now - loadedAt > 15_000) {
            synchronized (this) {
                if (now - loadedAt > TTL_MS || cache.isEmpty() && now - loadedAt > 15_000) {
                    cache = load();
                    loadedAt = System.currentTimeMillis();
                }
            }
        }
        return cache;
    }

    private List<Entry> load() {
        try {
            Map<UUID, Long> counts = categoryService.getAppCountByEntry();
            Map<String, Entry> byName = new LinkedHashMap<>();
            for (Category cat : categoryService.findAllFilterVisible()) {
                if (MADE_WITH_CATEGORY.equalsIgnoreCase(cat.getName())) continue;
                for (CategoryEntry e : categoryService.findVisibleEntriesByCategoryId(cat.getId())) {
                    String name = e.getName() == null ? "" : e.getName().trim().toLowerCase(Locale.ROOT).replace(",", " ");
                    if (name.isEmpty() || name.length() > 40) continue;
                    long c = counts.getOrDefault(e.getId(), 0L);
                    byName.merge(name, new Entry(name, c, e.getInterest()),
                            (a, b) -> new Entry(name, a.count() + b.count(), Math.max(a.interest(), b.interest())));
                }
            }
            List<Entry> list = new ArrayList<>(byName.values());
            list.sort(Comparator.comparingLong(Entry::count).reversed()
                    .thenComparing(Comparator.comparingInt(Entry::interest).reversed())
                    .thenComparing(Entry::name));
            return list;
        } catch (RuntimeException ex) {
            return List.of();   // never break the wish page because of the catalog
        }
    }
}