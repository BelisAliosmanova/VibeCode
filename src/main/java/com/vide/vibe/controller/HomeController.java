package com.vide.vibe.controller;

import com.vide.vibe.model.App;
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
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class HomeController {

    /** Pseudo-ids for the two fixed, non-deletable homepage blocks. */
    public static final String BLOCK_POPULAR_FEATURES = "popular-features";
    public static final String BLOCK_DIFFERENT_VIBE   = "different-vibe";
    private static final String SECTION_ORDER_KEY     = "home_section_order";

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

        // ── Merge custom sections + the two fixed blocks into one drag order ──
        List<String> blockOrder = resolveBlockOrder(sections);

        // Lookup map so the template can resolve a blockId straight to its
        // HomeSection without SpEL collection-selection in the markup.
        Map<String, HomeSection> sectionsById = new LinkedHashMap<>();
        for (HomeSection s : sections) sectionsById.put(s.getId().toString(), s);

        model.addAttribute("blockOrder",      blockOrder);
        model.addAttribute("blockPopular",    BLOCK_POPULAR_FEATURES);
        model.addAttribute("blockVibe",       BLOCK_DIFFERENT_VIBE);
        model.addAttribute("sections",        sections);
        model.addAttribute("sectionsById",    sectionsById);
        model.addAttribute("sectionListApps", sectionListApps);
        model.addAttribute("sectionFeatured", sectionFeatured);
        model.addAttribute("popularFeatures", popularFeatures);
        model.addAttribute("vibeApps",        vibeApps);
        model.addAttribute("entryAppCounts",  entryAppCounts);
        model.addAttribute("totalAppCount",   allApps.size());

        return "index";
    }

    /**
     * Builds the full drag order: custom section UUIDs (as strings) plus the
     * two fixed pseudo-ids, in whatever order was last saved to site config.
     * Anything saved that no longer exists (a deleted section) is dropped;
     * anything that exists but isn't in the saved order yet (a brand-new
     * section, or first run before any reorder has happened) is appended at
     * the end so it doesn't silently disappear from the page.
     */
    private List<String> resolveBlockOrder(List<HomeSection> sections) {
        Set<String> validIds = new LinkedHashSet<>();
        for (HomeSection s : sections) validIds.add(s.getId().toString());
        validIds.add(BLOCK_POPULAR_FEATURES);
        validIds.add(BLOCK_DIFFERENT_VIBE);

        String saved = siteConfigService.getOrDefault(SECTION_ORDER_KEY, "");
        List<String> order = new ArrayList<>();
        if (!saved.isBlank()) {
            for (String id : saved.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty() && validIds.contains(trimmed) && !order.contains(trimmed)) {
                    order.add(trimmed);
                }
            }
        }

        // Default order (first run / nothing saved yet): custom sections in
        // their own position order, then the two fixed blocks at the end —
        // matches the page's original layout.
        for (String id : validIds) {
            if (!order.contains(id)) order.add(id);
        }
        return order;
    }
}