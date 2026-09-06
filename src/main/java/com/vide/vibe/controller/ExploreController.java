package com.vide.vibe.controller;

import com.vide.vibe.model.*;
import com.vide.vibe.repository.AppMediaRepository;
import com.vide.vibe.repository.WorkflowRepository;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.CategoryService;
import com.vide.vibe.service.RankingService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/explore")
@RequiredArgsConstructor
public class ExploreController {

    private static final int PAGE_SIZE = 50;
    private static final String MADE_WITH_CATEGORY = "Made with";

    private final AppService appService;
    private final CategoryService categoryService;
    private final AppMediaRepository appMediaRepository;
    private final WorkflowRepository workflowRepository;
    private final RankingService rankingService;

    @GetMapping
    public String explore(
            @RequestParam(required = false) List<UUID> entries,
            Model model) {

        List<Category> filterCategories = orderCategoriesMadeWithLast(categoryService.findAllFilterVisible());
        Map<UUID, Long> entryAppCounts = categoryService.getAppCountByEntry();

        Map<Category, List<CategoryEntry>> filterEntries = new LinkedHashMap<>();
        Map<UUID, Long> categoryAppCounts = new LinkedHashMap<>();
        for (Category cat : filterCategories) {
            List<CategoryEntry> catEntries = categoryService.findVisibleEntriesByCategoryId(cat.getId())
                    .stream()
                    .filter(e -> entryAppCounts.getOrDefault(e.getId(), 0L) > 0)
                    .sorted(Comparator.comparingLong(
                            (CategoryEntry e) -> entryAppCounts.getOrDefault(e.getId(), 0L)).reversed())
                    .collect(Collectors.toList());

            if (!catEntries.isEmpty()) {
                long sum = catEntries.stream()
                        .mapToLong(e -> entryAppCounts.getOrDefault(e.getId(), 0L))
                        .sum();
                filterEntries.put(cat, catEntries);
                categoryAppCounts.put(cat.getId(), sum);
            }
        }

        List<String> selectedIdStrings = (entries != null ? entries : List.<UUID>of()).stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

        List<App> rankedApps = computeRankedApps(entries);
        List<App> pageApps = firstPage(rankedApps);
        boolean hasMore = rankedApps.size() > PAGE_SIZE;

        Set<UUID> visibleAppIds = pageApps.stream()
                .map(App::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> appsWithVideo = appMediaRepository.findAppIdsWithMediaType(visibleAppIds, AppMedia.MediaType.VIDEO);
        Set<UUID> appsWithImages = appMediaRepository.findAppIdsWithMediaType(visibleAppIds, AppMedia.MediaType.SCREENSHOT);
        Set<UUID> appsWithWorkflows = workflowRepository.findAppIdsWithWorkflows(visibleAppIds);

        // ── Model ──────────────────────────────────────────────────────────────
        model.addAttribute("filterEntries", filterEntries);
        model.addAttribute("selectedEntryIds", selectedIdStrings);
        model.addAttribute("apps", pageApps);
        model.addAttribute("hasMore", hasMore);

        model.addAttribute("entryAppCounts", entryAppCounts);
        model.addAttribute("categoryAppCounts", categoryAppCounts);

        model.addAttribute("appsWithVideo", appsWithVideo);
        model.addAttribute("appsWithImages", appsWithImages);
        model.addAttribute("appsWithWorkflows", appsWithWorkflows);

        return "explore";
    }

    @GetMapping("/more")
    public String more(
            @RequestParam(required = false) List<UUID> entries,
            @RequestParam(defaultValue = "0") int offset,
            Model model,
            HttpServletResponse response) {

        List<App> rankedApps = computeRankedApps(entries);

        int safeOffset = Math.max(offset, 0);
        List<App> batch = rankedApps.stream()
                .skip(safeOffset)
                .limit(PAGE_SIZE)
                .collect(Collectors.toList());

        boolean hasMore = rankedApps.size() > safeOffset + PAGE_SIZE;
        int nextOffset = safeOffset + PAGE_SIZE;

        response.setHeader("X-Has-More", Boolean.toString(hasMore));
        response.setHeader("X-Next-Offset", Integer.toString(nextOffset));

        Set<UUID> visibleAppIds = batch.stream()
                .map(App::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> appsWithVideo = appMediaRepository.findAppIdsWithMediaType(visibleAppIds, AppMedia.MediaType.VIDEO);
        Set<UUID> appsWithImages = appMediaRepository.findAppIdsWithMediaType(visibleAppIds, AppMedia.MediaType.SCREENSHOT);
        Set<UUID> appsWithWorkflows = workflowRepository.findAppIdsWithWorkflows(visibleAppIds);

        model.addAttribute("apps", batch);
        model.addAttribute("appsWithVideo", appsWithVideo);
        model.addAttribute("appsWithImages", appsWithImages);
        model.addAttribute("appsWithWorkflows", appsWithWorkflows);

        return "fragments/app-cards :: cards(apps=${apps},appsWithVideo=${appsWithVideo},appsWithImages=${appsWithImages},appsWithWorkflows=${appsWithWorkflows})";
    }

    /**
     * Single source of truth for app ordering on the whole Explore page.
     * There are no more tabs — every list (initial page and every "load more"
     * batch) is sorted by the ranking score, always, using whatever filters
     * (category entries) are currently selected.
     */
    private List<App> computeRankedApps(List<UUID> entries) {
        List<UUID> selectedUuids = entries != null ? entries : new ArrayList<>();

        Map<UUID, List<UUID>> selectedByCategoryId = new LinkedHashMap<>();
        for (UUID entryId : selectedUuids) {
            try {
                CategoryEntry entry = categoryService.findEntryById(entryId);
                UUID catId = entry.getCategory().getId();
                selectedByCategoryId.computeIfAbsent(catId, k -> new ArrayList<>()).add(entryId);
            } catch (RuntimeException ignored) {
            }
        }

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

        RankingConfig cfg = rankingService.getConfig();
        Map<UUID, Integer> scoresById = pool.stream()
                .collect(Collectors.toMap(App::getId, a -> rankingService.computeScore(a, cfg)));

        return pool.stream()
                .sorted(Comparator.comparingInt((App a) -> scoresById.get(a.getId())).reversed())
                .collect(Collectors.toList());
    }

    /**
     * The "Made with" filter category is always pushed to the bottom of the
     * sidebar, after every other filter card. Everything else keeps whatever
     * order categoryService.findAllFilterVisible() already returns.
     */
    private List<Category> orderCategoriesMadeWithLast(List<Category> categories) {
        List<Category> ordered = new ArrayList<>();
        List<Category> madeWith = new ArrayList<>();
        for (Category c : categories) {
            if (MADE_WITH_CATEGORY.equalsIgnoreCase(c.getName())) {
                madeWith.add(c);
            } else {
                ordered.add(c);
            }
        }
        ordered.addAll(madeWith);
        return ordered;
    }

    private List<App> firstPage(List<App> full) {
        return full.stream().limit(PAGE_SIZE).collect(Collectors.toList());
    }
}