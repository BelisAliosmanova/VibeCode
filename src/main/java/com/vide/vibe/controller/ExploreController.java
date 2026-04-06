package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.model.Category;
import com.vide.vibe.model.CategoryEntry;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/explore")
@RequiredArgsConstructor
public class ExploreController {

    private final AppService appService;
    private final CategoryService categoryService;

    @GetMapping
    public String explore(
            @RequestParam(required = false) List<UUID> entries,
            @RequestParam(required = false, defaultValue = "top-rated") String tab,
            Model model) {

        // ── Sidebar filter categories ──────────────────────────────────────────
        List<Category> filterCategories = categoryService.findAllFilterVisible();
        Map<Category, List<CategoryEntry>> filterEntries = new LinkedHashMap<>();
        for (Category cat : filterCategories) {
            filterEntries.put(cat, categoryService.findVisibleEntriesByCategoryId(cat.getId()));
        }

        // ── Selected sidebar entry IDs ─────────────────────────────────────────
        List<UUID> selectedUuids = entries != null ? entries : new ArrayList<>();
        // Convert to String for safe Thymeleaf JS inlining
        List<String> selectedIdStrings = selectedUuids.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

        // Group by category for AND-across-categories, OR-within-category logic
        Map<UUID, List<UUID>> selectedByCategoryId = new LinkedHashMap<>();
        for (UUID entryId : selectedUuids) {
            try {
                CategoryEntry entry = categoryService.findEntryById(entryId);
                UUID catId = entry.getCategory().getId();
                selectedByCategoryId.computeIfAbsent(catId, k -> new ArrayList<>()).add(entryId);
            } catch (RuntimeException ignored) { }
        }

        // ── Base pool: all non-deleted apps, filtered by sidebar ───────────────
        List<App> allApps = appService.findAll();
        List<App> pool;
        if (selectedByCategoryId.isEmpty()) {
            pool = allApps;
        } else {
            pool = allApps.stream()
                    .filter(app -> {
                        for (Map.Entry<UUID, List<UUID>> group : selectedByCategoryId.entrySet()) {
                            List<UUID> appEntryIds = categoryService.findSelectedEntryIds(
                                    app.getId(), group.getKey());
                            if (group.getValue().stream().noneMatch(appEntryIds::contains)) return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        // ── Two sections, determined by active tab ─────────────────────────────
        String section1Title;
        List<App> section1Apps;
        String section2Title;
        List<App> section2Apps;

        Instant now = Instant.now();

        if ("new".equals(tab)) {
            // Section 1: apps created in the last 7 days
            Instant oneWeekAgo  = now.minus(7,  ChronoUnit.DAYS);
            Instant oneMonthAgo = now.minus(30, ChronoUnit.DAYS);

            section1Title = "LAST WEEK";
            section1Apps  = pool.stream()
                    .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(oneWeekAgo))
                    .sorted(Comparator.comparing(App::getCreatedAt).reversed())
                    .limit(5).collect(Collectors.toList());

            // Section 2: apps created 8–30 days ago
            section2Title = "LAST MONTH";
            section2Apps  = pool.stream()
                    .filter(a -> a.getCreatedAt() != null
                            && a.getCreatedAt().isBefore(oneWeekAgo)
                            && a.getCreatedAt().isAfter(oneMonthAgo))
                    .sorted(Comparator.comparing(App::getCreatedAt).reversed())
                    .limit(5).collect(Collectors.toList());

        } else if ("verified".equals(tab)) {
            // Verified tab: top verified first, then top user-rated
            section1Title = "TOP VERIFIED APPS";
            section1Apps  = pool.stream()
                    .filter(a -> a.getVerifiedScore() != null)
                    .sorted(Comparator.comparingDouble(App::getVerifiedScore).reversed())
                    .limit(5).collect(Collectors.toList());

            section2Title = "TOP USER RATED APPS";
            section2Apps  = pool.stream()
                    .sorted(Comparator.comparingDouble(App::getUserRatingAvg).reversed()
                            .thenComparingInt(App::getUserRatingCount).reversed())
                    .limit(5).collect(Collectors.toList());

        } else {
            // Default "top-rated": top user-rated first, then top verified
            section1Title = "TOP USER RATED APPS";
            section1Apps  = pool.stream()
                    .sorted(Comparator.comparingDouble(App::getUserRatingAvg).reversed()
                            .thenComparingInt(App::getUserRatingCount).reversed())
                    .limit(5).collect(Collectors.toList());

            section2Title = "TOP VERIFIED APPS";
            section2Apps  = pool.stream()
                    .filter(a -> a.getVerifiedScore() != null)
                    .sorted(Comparator.comparingDouble(App::getVerifiedScore).reversed())
                    .limit(5).collect(Collectors.toList());
        }

        // ── Model ──────────────────────────────────────────────────────────────
        model.addAttribute("filterEntries",   filterEntries);
        model.addAttribute("selectedEntryIds", selectedIdStrings);
        model.addAttribute("tab",             tab);
        model.addAttribute("section1Title",   section1Title);
        model.addAttribute("section1Apps",    section1Apps);
        model.addAttribute("section2Title",   section2Title);
        model.addAttribute("section2Apps",    section2Apps);

        return "explore";
    }
}