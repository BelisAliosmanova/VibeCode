package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.model.AppCategoryEntry;
import com.vide.vibe.model.Category;
import com.vide.vibe.model.CategoryEntry;
import com.vide.vibe.model.HomeSection;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.CategoryService;
import com.vide.vibe.service.HomeSectionService;
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

    private final AppService appService;
    private final CategoryService categoryService;
    private final SiteConfigService siteConfigService;
    private final HomeSectionService homeSectionService;

    @GetMapping
    public String home(Model model) {
        List<App> allApps = appService.findAll();

        // ── Dynamic custom sections ───────────────────────────────────────────
        List<HomeSection> sections = homeSectionService.findAllOrdered();
        Map<UUID, List<App>> sectionListApps = homeSectionService.findAllListAppsBySection();
        Map<UUID, App> sectionFeatured = homeSectionService.findAllFeaturedAppsBySection();

        // ── Most popular feature entries ──────────────────────────────────────
        List<CategoryEntry> popularFeatures = new ArrayList<>();
        try {
            for (Category cat : categoryService.findAll()) {
                popularFeatures.addAll(
                        categoryService.findVisibleEntriesByCategoryId(cat.getId())
                );
            }
            popularFeatures.sort(Comparator.comparingInt(CategoryEntry::getInterest).reversed());
            if (popularFeatures.size() > 8) {
                popularFeatures = popularFeatures.subList(0, 8);
            }
        } catch (Exception ignored) {}

        // ── Vibe apps (random hourly seed) ───────────────────────────────────
        List<App> vibeApps = new ArrayList<>(allApps);
        Collections.shuffle(vibeApps, new Random(Instant.now().getEpochSecond() / 3600));
        vibeApps = vibeApps.stream().limit(6).collect(Collectors.toList());

        // ── Entry app counts ─────────────────────────────────────────────────
        Map<UUID, Long> entryAppCounts = categoryService.getAppCountByEntry();

        // ── Model ─────────────────────────────────────────────────────────────
        model.addAttribute("sections", sections);
        model.addAttribute("sectionListApps", sectionListApps);
        model.addAttribute("sectionFeatured", sectionFeatured);
        model.addAttribute("popularFeatures", popularFeatures);
        model.addAttribute("vibeApps", vibeApps);
        model.addAttribute("entryAppCounts", entryAppCounts);
        model.addAttribute("totalAppCount", allApps.size());

        return "index";
    }
}