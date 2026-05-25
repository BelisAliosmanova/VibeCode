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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class HomeController {

    private final AppService appService;
    private final CategoryService categoryService;

    @GetMapping
    public String home(Model model) {
        List<App> allApps = appService.findAll();

        // ── Newest apps (last 14 days, up to 6) ─────────────────────────────
        Instant twoWeeksAgo = Instant.now().minus(14, ChronoUnit.DAYS);
        List<App> newestApps = allApps.stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(twoWeeksAgo))
                .sorted(Comparator.comparing(App::getCreatedAt).reversed())
                .limit(6)
                .collect(Collectors.toList());
        // Fallback: just take newest regardless of date
        if (newestApps.isEmpty()) {
            newestApps = allApps.stream()
                    .sorted(Comparator.comparing(App::getCreatedAt).reversed())
                    .limit(6)
                    .collect(Collectors.toList());
        }

        // ── Reviewed today (has verifiedScore, recently updated, up to 1 featured) ─
        List<App> reviewedApps = allApps.stream()
                .filter(a -> a.getVerifiedScore() != null)
                .sorted(Comparator.comparingDouble(App::getVerifiedScore).reversed())
                .limit(1)
                .collect(Collectors.toList());

        // ── Most popular feature entries (by interest) ─────────────────────
        List<CategoryEntry> popularFeatures = new ArrayList<>();
        try {
            List<Category> allCategories = categoryService.findAll();
            for (Category cat : allCategories) {
                List<CategoryEntry> entries = categoryService.findVisibleEntriesByCategoryId(cat.getId());
                popularFeatures.addAll(entries);
            }
            popularFeatures.sort(Comparator.comparingInt(CategoryEntry::getInterest).reversed());
            if (popularFeatures.size() > 8) popularFeatures = popularFeatures.subList(0, 8);
        } catch (Exception ignored) {}

        // ── App of the week (highest verified score) ─────────────────────
        Optional<App> appOfWeek = allApps.stream()
                .filter(a -> a.getVerifiedScore() != null)
                .max(Comparator.comparingDouble(App::getVerifiedScore));
        if (appOfWeek.isEmpty()) {
            appOfWeek = allApps.stream()
                    .max(Comparator.comparingDouble(App::getUserRatingAvg));
        }

        // ── Top rated apps (by user rating, up to 5) ─────────────────────
        List<App> topRatedApps = allApps.stream()
                .sorted(Comparator.comparingDouble(App::getUserRatingAvg).reversed()
                        .thenComparingInt(App::getUserRatingCount).reversed())
                .limit(5)
                .collect(Collectors.toList());

        // ── Apps with a different vibe (random selection, up to 6) ────────
        List<App> vibeApps = new ArrayList<>(allApps);
        Collections.shuffle(vibeApps, new Random(Instant.now().getEpochSecond() / 3600));
        vibeApps = vibeApps.stream().limit(6).collect(Collectors.toList());

        // ── Sidebar filter categories ─────────────────────────────────────
        List<Category> filterCategories = categoryService.findAllFilterVisible();
        Map<Category, List<CategoryEntry>> filterEntries = new LinkedHashMap<>();
        for (Category cat : filterCategories) {
            filterEntries.put(cat, categoryService.findVisibleEntriesByCategoryId(cat.getId()));
        }

        Map<UUID, Long> entryAppCounts = categoryService.getAppCountByEntry();

        model.addAttribute("newestApps",      newestApps);
        model.addAttribute("reviewedApps",    reviewedApps);
        model.addAttribute("popularFeatures", popularFeatures);
        model.addAttribute("appOfWeek",       appOfWeek.orElse(null));
        model.addAttribute("topRatedApps",    topRatedApps);
        model.addAttribute("vibeApps",        vibeApps);
        model.addAttribute("filterEntries",   filterEntries);
        model.addAttribute("entryAppCounts",  entryAppCounts);
        model.addAttribute("totalAppCount",   allApps.size());

        return "index";
    }
}