package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.model.AppCategoryEntry;
import com.vide.vibe.model.Category;
import com.vide.vibe.model.CategoryEntry;
import com.vide.vibe.model.HomeSection;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.CategoryService;
import com.vide.vibe.service.HomeSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin page for building/editing the manual homepage sections shown in the
 * "Add/Edit home section" screen — pick a layout, pick apps (via the same
 * category filters used on /explore), give it a title (or two, for the
 * 5+1 layout), and add it.
 *
 * No auth gate for now — every visitor can manage these, matching the rest
 * of the app's current "no security yet" state.
 */
@Controller
@RequestMapping("/admin/home-sections")
@RequiredArgsConstructor
public class HomeSectionController {

    private final HomeSectionService homeSectionService;
    private final AppService         appService;
    private final CategoryService    categoryService;

    @GetMapping
    public String editor(Model model) {
        List<App> allApps = appService.findAll();

        // Sidebar filter categories — identical source to /explore.
        List<Category> filterCategories = categoryService.findAllFilterVisible();
        Map<Category, List<CategoryEntry>> filterEntries = new LinkedHashMap<>();
        for (Category cat : filterCategories) {
            filterEntries.put(cat, categoryService.findVisibleEntriesByCategoryId(cat.getId()));
        }

        // "Top user rated" / "Top verified" quick-add lists, same logic as /explore.
        List<App> topUserRated = allApps.stream()
                .sorted((a, b) -> {
                    int byAvg = Double.compare(b.getUserRatingAvg(), a.getUserRatingAvg());
                    if (byAvg != 0) return byAvg;
                    return Integer.compare(b.getUserRatingCount(), a.getUserRatingCount());
                })
                .limit(5)
                .toList();

        List<App> topVerified = allApps.stream()
                .filter(a -> a.getVerifiedScore() != null)
                .sorted((a, b) -> Double.compare(b.getVerifiedScore(), a.getVerifiedScore()))
                .limit(5)
                .toList();

        // Existing sections + their current selections, for editing/reordering.
        List<HomeSection> sections = homeSectionService.findAllOrdered();
        Map<UUID, List<App>> sectionListApps   = homeSectionService.findAllListAppsBySection();
        Map<UUID, App>       sectionFeatured   = homeSectionService.findAllFeaturedAppsBySection();

        // app.id -> list of entry IDs it's tagged with, so the picker's sidebar
        // filters can narrow the app list client-side without another round trip.
        Map<UUID, List<UUID>> appEntryIds = new LinkedHashMap<>();
        for (App app : allApps) {
            List<UUID> entryIds = categoryService.findAllSelectionsForApp(app.getId())
                    .stream()
                    .map(ace -> ace.getEntry().getId())
                    .distinct()
                    .collect(Collectors.toList());
            appEntryIds.put(app.getId(), entryIds);
        }

        model.addAttribute("allApps",        allApps);
        model.addAttribute("filterEntries",  filterEntries);
        model.addAttribute("appEntryIds",    appEntryIds);
        model.addAttribute("topUserRated",   topUserRated);
        model.addAttribute("topVerified",    topVerified);
        model.addAttribute("sections",       sections);
        model.addAttribute("sectionListApps", sectionListApps);
        model.addAttribute("sectionFeatured", sectionFeatured);

        return "admin/home-sections";
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String title,
            @RequestParam(required = false) String featuredTitle,
            @RequestParam(defaultValue = "FIVE_PLUS_ONE") HomeSection.Layout layout,
            @RequestParam(required = false) List<UUID> listAppIds,
            @RequestParam(required = false) UUID featuredAppId) {
        try {
            HomeSection saved = homeSectionService.create(title, featuredTitle, layout, listAppIds, featuredAppId);
            return ResponseEntity.ok(Map.of("ok", true, "id", saved.getId().toString()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String featuredTitle,
            @RequestParam(required = false) HomeSection.Layout layout,
            @RequestParam(required = false) List<UUID> listAppIds,
            @RequestParam(required = false) UUID featuredAppId) {
        try {
            homeSectionService.update(id, title, featuredTitle, layout, listAppIds, featuredAppId);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }

    /**
     * Lightweight endpoint used by the homepage's inline contenteditable
     * titles — saves whichever of the two titles are supplied without
     * touching layout or app selections.
     */
    @PostMapping("/{id}/titles")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateTitles(
            @PathVariable UUID id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String featuredTitle) {
        try {
            homeSectionService.updateTitles(id, title, featuredTitle);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        try {
            homeSectionService.delete(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }

    /** Persists the new drag-and-drop order of the custom homepage sections. */
    @PostMapping("/reorder")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reorder(@RequestBody List<UUID> orderedIds) {
        try {
            homeSectionService.reorder(orderedIds);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }

    @PostMapping("/{id}/swap-sides")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> swapSides(@PathVariable UUID id) {
        try {
            homeSectionService.swapSides(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }
}