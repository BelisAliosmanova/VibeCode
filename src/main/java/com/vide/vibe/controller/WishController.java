package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.repository.AppOptRepository;
import com.vide.vibe.repository.AppOptView;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.WishRequestService;
import com.vide.vibe.service.WishRequestService.AppWishStats;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.Normalizer;
import java.util.*;

@Controller
public class WishController {

    private static final String EMAIL_RE = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final int SEARCH_LIMIT = 40;

    private final WishRequestService service;
    private final AppService appService;
    private final AppOptRepository optRepo;

    public WishController(WishRequestService service, AppService appService, AppOptRepository optRepo) {
        this.service = service;
        this.appService = appService;
        this.optRepo = optRepo;
    }

    /** Form backing object (separate from the entity so users can't bind id/createdAt/handled). */
    public static class WishForm {
        private String appId;       // set when the user picked one of our apps from the suggestions
        private String appName;     // what is typed in "An app like..." (free text)
        private String description;
        private String features;    // comma separated tags
        private String email;
        private String website;     // honeypot

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getDescription() { return description; }
        public void setDescription(String d) { this.description = d; }
        public String getFeatures() { return features; }
        public void setFeatures(String f) { this.features = f; }
        public String getEmail() { return email; }
        public void setEmail(String e) { this.email = e; }
        public String getWebsite() { return website; }
        public void setWebsite(String w) { this.website = w; }
    }

    // ── Dropdown search (JSON) ────────────────────────────────────────────────
    @GetMapping("/wish/apps")
    @ResponseBody
    public Map<String, Object> searchApps(@RequestParam(defaultValue = "") String q) {
        String n = escapeLike(norm(q.trim()));
        if (n.length() > 120) n = n.substring(0, 120);

        // Ask for one extra row so we know whether there are more than SEARCH_LIMIT
        List<AppOptView> rows = optRepo.search(n, App.Status.APPROVED, App.Visibility.PUBLIC,
                PageRequest.of(0, SEARCH_LIMIT + 1));

        boolean more = rows.size() > SEARCH_LIMIT;
        List<Map<String, String>> items = new ArrayList<>();
        for (AppOptView a : rows.subList(0, Math.min(SEARCH_LIMIT, rows.size()))) items.add(toMap(a));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("more", more);
        return out;
    }

    // ── Step 1 + 2: the wish form ─────────────────────────────────────────────
    @GetMapping("/wish")
    public String form(@RequestParam(required = false) String like,
                       @RequestParam(required = false) List<String> features,
                       Model model) {
        if (!model.containsAttribute("wishForm")) {
            WishForm f = new WishForm();
            if (like != null) {
                try {
                    App a = appService.findBySlug(like);
                    f.setAppId(a.getId().toString());
                    f.setAppName(a.getName());
                    Map<String, String> sel = new LinkedHashMap<>();
                    sel.put("id", a.getId().toString());
                    sel.put("name", a.getName());
                    sel.put("icon", a.getIconUrl() == null ? "" : a.getIconUrl());
                    model.addAttribute("selectedApp", sel);
                } catch (RuntimeException ignored) {}
            }
            if (features != null) f.setFeatures(String.join(",", features));
            model.addAttribute("wishForm", f);
        }
        populate(model);
        return "wish";
    }

    @PostMapping("/wish")
    public String submit(@ModelAttribute("wishForm") WishForm form, Model model, RedirectAttributes ra) {

        // Bots fill the hidden field: pretend success, store nothing
        if (form.getWebsite() != null && !form.getWebsite().isBlank()) {
            ra.addFlashAttribute("success", true);
            return "redirect:/wish";
        }

        String desc = form.getDescription() == null ? "" : form.getDescription().trim();
        String email = form.getEmail() == null ? "" : form.getEmail().trim();
        String typed = form.getAppName() == null ? "" : form.getAppName().trim();

        // 1) one of our apps (picked from suggestions, or typed exactly) -> link it
        //    2) anything else typed -> keep as free text
        // Only small, targeted queries here (no loading of all apps).
        AppOptView linked = null;
        if (form.getAppId() != null && !form.getAppId().isBlank() && typed.length() <= 120) {
            try {
                linked = optRepo.findById(UUID.fromString(form.getAppId()),
                        App.Status.APPROVED, App.Visibility.PUBLIC).orElse(null);
            } catch (IllegalArgumentException ignored) {}
            if (linked != null && !typed.isEmpty() && !typed.equalsIgnoreCase(linked.getName())) linked = null; // edited after picking
        }
        if (linked == null && !typed.isEmpty() && typed.length() <= 120) {
            linked = optRepo.findByName(typed, App.Status.APPROVED, App.Visibility.PUBLIC, PageRequest.of(0, 1))
                    .stream().findFirst().orElse(null);
        }
        String custom = linked == null && !typed.isEmpty() ? typed : null;

        String error = null;
        int step = 2;
        if (linked == null && custom == null && desc.length() < 10) {
            error = "Tell us which app you want one like, or describe your app in a bit more detail.";
            step = 1;
        } else if (typed.length() > 120) {
            error = "The app name is too long.";
            step = 1;
        } else if (desc.length() > 4000) {
            error = "The description is too long.";
            step = 1;
        } else if (!email.matches(EMAIL_RE) || email.length() > 255) {
            error = "Please enter a valid email address.";
        }
        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("startStep", step);
            if (linked != null) model.addAttribute("selectedApp", toMap(linked));
            populate(model);
            return "wish";
        }

        service.submit(linked == null ? null : linked.getId(), custom, desc, email,
                WishRequestService.parseFeatures(form.getFeatures()));
        ra.addFlashAttribute("success", true);
        return "redirect:/wish";
    }

    // ── "All app wishes" ──────────────────────────────────────────────────────
    @GetMapping("/wishes")
    public String all(Model model) {
        model.addAttribute("stats", service.rankedStats());
        return "wishes";
    }

    // ── "Wish an app like X" ──────────────────────────────────────────────────
    @GetMapping("/wish/like/{slug}")
    public String like(@PathVariable String slug, Model model) {
        App app;
        try { app = appService.findBySlug(slug); } catch (RuntimeException e) { return "redirect:/wish"; }

        Map<UUID, AppWishStats> stats = service.statsByAppId();
        AppWishStats mine = stats.get(app.getId());
        model.addAttribute("app", app);
        model.addAttribute("topFeatures", mine == null ? List.of() : mine.getTopFeatures());
        model.addAttribute("wishCount", mine == null ? 0 : mine.getCount());

        List<Map<String, Object>> others = new ArrayList<>();
        for (App a : visibleApps()) {
            if (a.getId().equals(app.getId())) continue;
            AppWishStats s = stats.get(a.getId());
            others.add(Map.of("app", a, "count", s == null ? 0L : s.getCount()));
        }
        others.sort((x, y) -> Long.compare((Long) y.get("count"), (Long) x.get("count")));
        model.addAttribute("others", others);
        return "wish-like";
    }

    /** Quick "+1": the person just adds their vote to this app's count. */
    @PostMapping("/wish/like/{slug}/plus-one")
    public String plusOne(@PathVariable String slug,
                          @RequestParam String email,
                          @RequestParam(required = false) String features,
                          @RequestParam(required = false) String website,
                          RedirectAttributes ra) {
        App app;
        try { app = appService.findBySlug(slug); } catch (RuntimeException e) { return "redirect:/wish"; }

        if (website != null && !website.isBlank()) { // honeypot
            ra.addFlashAttribute("plusOne", "added");
            return "redirect:/wish/like/" + slug;
        }
        email = email.trim();
        if (!email.matches(EMAIL_RE) || email.length() > 255) {
            ra.addFlashAttribute("plusOneError", "Please enter a valid email address.");
            return "redirect:/wish/like/" + slug;
        }
        boolean added = service.plusOne(app.getId(), email, WishRequestService.parseFeatures(features));
        ra.addFlashAttribute("plusOne", added ? "added" : "exists");
        return "redirect:/wish/like/" + slug;
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private void populate(Model model) {
        // NOTE: the apps are not inlined into the page; the dropdown calls /wish/apps
        model.addAttribute("suggested", service.suggestedFeatures());
    }

    private static Map<String, String> toMap(AppOptView a) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("name", a.getName());
        m.put("icon", a.getIconUrl() == null ? "" : a.getIconUrl());
        return m;
    }

    /** Lowercase + strip accents. */
    private static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Escape LIKE wildcards so user input like "%" or "_" is matched literally. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Still used by the "wish like X" page only (not by the dropdown). */
    private List<App> visibleApps() {
        return appService.findAll().stream()
                .filter(a -> a.getStatus() == App.Status.APPROVED && a.getVisibility() == App.Visibility.PUBLIC)
                .sorted(Comparator.comparing(App::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}