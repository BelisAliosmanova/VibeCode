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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/explore")
@RequiredArgsConstructor
public class ExploreController {

    private static final int PAGE_SIZE = 5;

    private final AppService appService;
    private final CategoryService categoryService;
    private final AppMediaRepository appMediaRepository;
    private final WorkflowRepository workflowRepository;
    private final RankingService rankingService;

    @GetMapping
    public String explore(
            @RequestParam(required = false) List<UUID> entries,
            @RequestParam(required = false, defaultValue = "top-rated") String tab,
            Model model) {

        List<Category> filterCategories = categoryService.findAllFilterVisible();
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

        SectionsResult sections = computeSections(tab, entries);

        List<App> section1Apps = firstPage(sections.list1());
        List<App> section2Apps = firstPage(sections.list2());
        boolean hasMoreSection1 = sections.list1().size() > PAGE_SIZE;
        boolean hasMoreSection2 = sections.list2().size() > PAGE_SIZE;

        Set<UUID> visibleAppIds = new LinkedHashSet<>();
        section1Apps.forEach(a -> visibleAppIds.add(a.getId()));
        section2Apps.forEach(a -> visibleAppIds.add(a.getId()));

        Set<UUID> appsWithVideo = appMediaRepository.findAppIdsWithMediaType(visibleAppIds, AppMedia.MediaType.VIDEO);
        Set<UUID> appsWithImages = appMediaRepository.findAppIdsWithMediaType(visibleAppIds, AppMedia.MediaType.SCREENSHOT);
        Set<UUID> appsWithWorkflows = workflowRepository.findAppIdsWithWorkflows(visibleAppIds);

        // ── Model ──────────────────────────────────────────────────────────────
        model.addAttribute("filterEntries", filterEntries);
        model.addAttribute("selectedEntryIds", selectedIdStrings);
        model.addAttribute("tab", tab);
        model.addAttribute("section1Title", sections.title1());
        model.addAttribute("section1Apps", section1Apps);
        model.addAttribute("section2Title", sections.title2());
        model.addAttribute("section2Apps", section2Apps);
        model.addAttribute("hasMoreSection1", hasMoreSection1);
        model.addAttribute("hasMoreSection2", hasMoreSection2);

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
            @RequestParam(required = false, defaultValue = "top-rated") String tab,
            @RequestParam int section,
            @RequestParam(defaultValue = "0") int offset,
            Model model,
            HttpServletResponse response) {

        SectionsResult sections = computeSections(tab, entries);
        List<App> full = (section == 2) ? sections.list2() : sections.list1();

        int safeOffset = Math.max(offset, 0);
        List<App> batch = full.stream()
                .skip(safeOffset)
                .limit(PAGE_SIZE)
                .collect(Collectors.toList());

        boolean hasMore = full.size() > safeOffset + PAGE_SIZE;
        int nextOffset = safeOffset + PAGE_SIZE;

        response.setHeader("X-Has-More", Boolean.toString(hasMore));
        response.setHeader("X-Next-Offset", Integer.toString(nextOffset));

        Set<UUID> visibleAppIds = batch.stream().map(App::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> appsWithVideo = appMediaRepository.findAppIdsWithMediaType(visibleAppIds, AppMedia.MediaType.VIDEO);
        Set<UUID> appsWithImages = appMediaRepository.findAppIdsWithMediaType(visibleAppIds, AppMedia.MediaType.SCREENSHOT);
        Set<UUID> appsWithWorkflows = workflowRepository.findAppIdsWithWorkflows(visibleAppIds);

        model.addAttribute("apps", batch);
        model.addAttribute("appsWithVideo", appsWithVideo);
        model.addAttribute("appsWithImages", appsWithImages);
        model.addAttribute("appsWithWorkflows", appsWithWorkflows);

        return "fragments/app-cards :: cards(apps=${apps},appsWithVideo=${appsWithVideo},appsWithImages=${appsWithImages},appsWithWorkflows=${appsWithWorkflows})";
    }

    private SectionsResult computeSections(String tab, List<UUID> entries) {
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

        String section1Title;
        List<App> section1Apps;
        String section2Title;
        List<App> section2Apps;

        Instant now = Instant.now();

        if ("new".equals(tab)) {
            Instant oneWeekAgo = now.minus(7, ChronoUnit.DAYS);
            Instant oneMonthAgo = now.minus(30, ChronoUnit.DAYS);

            section1Title = "LAST WEEK";
            section1Apps = pool.stream()
                    .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(oneWeekAgo))
                    .sorted(Comparator.comparing(App::getCreatedAt).reversed())
                    .collect(Collectors.toList());

            section2Title = "LAST MONTH";
            section2Apps = pool.stream()
                    .filter(a -> a.getCreatedAt() != null
                            && a.getCreatedAt().isBefore(oneWeekAgo)
                            && a.getCreatedAt().isAfter(oneMonthAgo))
                    .sorted(Comparator.comparing(App::getCreatedAt).reversed())
                    .collect(Collectors.toList());

        } else if ("verified".equals(tab)) {
            section1Title = "TOP VERIFIED APPS";
            section1Apps = pool.stream()
                    .filter(a -> a.getVerifiedScore() != null)
                    .sorted(Comparator.comparingDouble(App::getVerifiedScore).reversed())
                    .collect(Collectors.toList());

            section2Title = "TOP USER RATED APPS";
            section2Apps = pool.stream()
                    .sorted(Comparator.comparingDouble(App::getUserRatingAvg).reversed()
                            .thenComparingInt(App::getUserRatingCount).reversed())
                    .collect(Collectors.toList());

        } else if ("ranked".equals(tab)) {
            RankingConfig cfg = rankingService.getConfig();

            Map<UUID, Integer> scoresById = pool.stream()
                    .collect(Collectors.toMap(App::getId, a -> rankingService.computeScore(a, cfg)));

            List<App> byScore = pool.stream()
                    .sorted(Comparator.comparingInt((App a) -> scoresById.get(a.getId())).reversed())
                    .collect(Collectors.toList());

            section1Title = "TOP RANKED APPS";
            section1Apps = byScore.stream().limit(PAGE_SIZE).collect(Collectors.toList());

            section2Title = "ALSO HIGHLY RANKED";
            section2Apps = byScore.stream().skip(PAGE_SIZE).collect(Collectors.toList());

        } else {
            section1Title = "TOP USER RATED APPS";
            section1Apps = pool.stream()
                    .sorted(Comparator.comparingDouble(App::getUserRatingAvg).reversed()
                            .thenComparingInt(App::getUserRatingCount).reversed())
                    .collect(Collectors.toList());

            section2Title = "TOP VERIFIED APPS";
            section2Apps = pool.stream()
                    .filter(a -> a.getVerifiedScore() != null)
                    .sorted(Comparator.comparingDouble(App::getVerifiedScore).reversed())
                    .collect(Collectors.toList());
        }

        return new SectionsResult(section1Title, section1Apps, section2Title, section2Apps);
    }

    private List<App> firstPage(List<App> full) {
        return full.stream().limit(PAGE_SIZE).collect(Collectors.toList());
    }

    private record SectionsResult(String title1, List<App> list1, String title2, List<App> list2) {
    }
}