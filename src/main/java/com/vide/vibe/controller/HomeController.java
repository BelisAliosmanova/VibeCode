package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.model.AppCategoryEntry;
import com.vide.vibe.model.Category;
import com.vide.vibe.model.CategoryEntry;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.CategoryService;
import com.vide.vibe.service.SiteConfigService;
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

    // Default order — matches data-section-id values in the template
    private static final String DEFAULT_ORDER =
            "[\"newest\",\"reviewed\",\"features\",\"aow\",\"toprated\",\"vibe\"]";

    private static final String SECTION_ORDER_KEY = "home_section_order";

    private final AppService appService;
    private final CategoryService categoryService;
    private final SiteConfigService siteConfigService;

    @GetMapping
    public String home(Model model) {
        List<App> allApps = appService.findAll();

        // ── Newest apps (last 14 days, up to 6) ─────────────────────────────
        Instant twoWeeksAgo = Instant.now().minus(14, ChronoUnit.DAYS);

        List<App> newestApps = allApps.stream()
                .filter(a -> a.getCreatedAt() != null &&
                        a.getCreatedAt().isAfter(twoWeeksAgo))
                .sorted(Comparator.comparing(App::getCreatedAt).reversed())
                .limit(6)
                .collect(Collectors.toList());

        if (newestApps.isEmpty()) {
            newestApps = allApps.stream()
                    .sorted(Comparator.comparing(App::getCreatedAt).reversed())
                    .limit(6)
                    .collect(Collectors.toList());
        }

        // ── Reviewed today ─────────────────────────────────────────────────
        List<App> reviewedApps = allApps.stream()
                .filter(a -> a.getVerifiedScore() != null)
                .sorted(Comparator.comparingDouble(App::getVerifiedScore).reversed())
                .limit(1)
                .collect(Collectors.toList());

        // ── Most popular feature entries ───────────────────────────────────
        List<CategoryEntry> popularFeatures = new ArrayList<>();

        try {
            for (Category cat : categoryService.findAll()) {
                popularFeatures.addAll(
                        categoryService.findVisibleEntriesByCategoryId(cat.getId())
                );
            }

            popularFeatures.sort(
                    Comparator.comparingInt(CategoryEntry::getInterest)
                            .reversed()
            );

            if (popularFeatures.size() > 8) {
                popularFeatures = popularFeatures.subList(0, 8);
            }
        } catch (Exception ignored) {
        }

        // ── App of the week ────────────────────────────────────────────────
        Optional<App> appOfWeek = allApps.stream()
                .filter(a -> a.getVerifiedScore() != null)
                .max(Comparator.comparingDouble(App::getVerifiedScore));

        if (appOfWeek.isEmpty()) {
            appOfWeek = allApps.stream()
                    .max(Comparator.comparingDouble(App::getUserRatingAvg));
        }

        // ── Features for App of the Week ──────────────────────────────────
        Map<String, List<CategoryEntry>> appOfWeekFeatures = new LinkedHashMap<>();

        appOfWeek.ifPresent(app -> {
            List<AppCategoryEntry> selections =
                    categoryService.findAllSelectionsForApp(app.getId());

            for (AppCategoryEntry ace : selections) {
                String categoryName =
                        ace.getEntry().getCategory().getName();

                appOfWeekFeatures
                        .computeIfAbsent(categoryName,
                                k -> new ArrayList<>())
                        .add(ace.getEntry());
            }
        });

        // ── Features for Reviewed App ─────────────────────────────────────
        Map<String, List<CategoryEntry>> reviewedAppFeatures =
                new LinkedHashMap<>();

        if (!reviewedApps.isEmpty()) {
            App reviewedApp = reviewedApps.get(0);

            List<AppCategoryEntry> selections =
                    categoryService.findAllSelectionsForApp(reviewedApp.getId());

            for (AppCategoryEntry ace : selections) {
                String categoryName =
                        ace.getEntry().getCategory().getName();

                reviewedAppFeatures
                        .computeIfAbsent(categoryName,
                                k -> new ArrayList<>())
                        .add(ace.getEntry());
            }
        }

        // ── Top rated apps ────────────────────────────────────────────────
        List<App> topRatedApps = allApps.stream()
                .sorted(
                        Comparator.comparingDouble(App::getUserRatingAvg)
                                .reversed()
                                .thenComparingInt(App::getUserRatingCount)
                                .reversed()
                )
                .limit(5)
                .collect(Collectors.toList());

        // ── Vibe apps (random hourly seed) ────────────────────────────────
        List<App> vibeApps = new ArrayList<>(allApps);

        Collections.shuffle(
                vibeApps,
                new Random(Instant.now().getEpochSecond() / 3600)
        );

        vibeApps = vibeApps.stream()
                .limit(6)
                .collect(Collectors.toList());

        // ── Sidebar filter categories ─────────────────────────────────────
        List<Category> filterCategories =
                categoryService.findAllFilterVisible();

        Map<Category, List<CategoryEntry>> filterEntries =
                new LinkedHashMap<>();

        for (Category cat : filterCategories) {
            filterEntries.put(
                    cat,
                    categoryService.findVisibleEntriesByCategoryId(cat.getId())
            );
        }

        Map<UUID, Long> entryAppCounts =
                categoryService.getAppCountByEntry();

        // ── Global section order ──────────────────────────────────────────
        String sectionOrderJson =
                siteConfigService.getOrDefault(
                        SECTION_ORDER_KEY,
                        DEFAULT_ORDER
                );

        // ── Model attributes ──────────────────────────────────────────────
        model.addAttribute("newestApps", newestApps);
        model.addAttribute("reviewedApps", reviewedApps);
        model.addAttribute("reviewedAppFeatures", reviewedAppFeatures);

        model.addAttribute("popularFeatures", popularFeatures);

        model.addAttribute("appOfWeek", appOfWeek.orElse(null));
        model.addAttribute("appOfWeekFeatures", appOfWeekFeatures);

        model.addAttribute("topRatedApps", topRatedApps);
        model.addAttribute("vibeApps", vibeApps);

        model.addAttribute("filterEntries", filterEntries);
        model.addAttribute("entryAppCounts", entryAppCounts);
        model.addAttribute("totalAppCount", allApps.size());

        model.addAttribute("sectionOrderJson", sectionOrderJson);

        return "index";
    }
}