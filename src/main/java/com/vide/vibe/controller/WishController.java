package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.WishRequestService;
import com.vide.vibe.service.WishRequestService.AppWishStats;
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
    private static final long CACHE_MS = 2 * 60 * 1000;

    private final WishRequestService service;
    private final AppService appService;

    /** Lightweight copy of an app, just what the dropdown needs. */
    private record AppOpt(UUID id, String name, String icon, String key) {}
    private record Snapshot(long at, List<AppOpt> items) {}
    private volatile Snapshot snapshot;

    public WishController(WishRequestService service, AppService appService) {
        this.service = service;
        this.appService = appService;
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
        String n = norm(q.trim());
        List<AppOpt> starts = new ArrayList<>(), has = new ArrayList<>();
        for (AppOpt a : appOptions()) {
            if (n.isEmpty() || a.key().startsWith(n)) starts.add(a);
            else if (a.key().contains(n)) has.add(a);
        }
        List<AppOpt> all = new ArrayList<>(starts);
        all.addAll(has);

        List<Map<String, String>> items = new ArrayList<>();
        for (AppOpt a : all.subList(0, Math.min(SEARCH_LIMIT, all.size()))) items.add(toMap(a));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("more", all.size() > SEARCH_LIMIT);
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
        List<AppOpt> apps = appOptions();
        AppOpt linked = null;
        if (form.getAppId() != null && !form.getAppId().isBlank()) {
            try {
                UUID id = UUID.fromString(form.getAppId());
                linked = apps.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
            } catch (IllegalArgumentException ignored) {}
            if (linked != null && !typed.isEmpty() && !typed.equalsIgnoreCase(linked.name())) linked = null; // edited after picking
        }
        if (linked == null && !typed.isEmpty()) {
            linked = apps.stream().filter(a -> a.name().equalsIgnoreCase(typed)).findFirst().orElse(null);
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

        service.submit(linked == null ? null : linked.id(), custom, desc, email,
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
        // NOTE: the apps are no longer inlined into the page; the dropdown calls /wish/apps
        model.addAttribute("suggested", service.suggestedFeatures());
    }

    /** Cached for a couple of minutes so typing in the dropdown doesn't hit the DB for all apps each time. */
    private List<AppOpt> appOptions() {
        Snapshot s = snapshot;
        long now = System.currentTimeMillis();
        if (s == null || now - s.at() > CACHE_MS) {
            List<AppOpt> items = visibleApps().stream()
                    .map(a -> new AppOpt(a.getId(), a.getName(),
                            a.getIconUrl() == null ? "" : a.getIconUrl(), norm(a.getName())))
                    .toList();
            s = new Snapshot(now, items);
            snapshot = s;
        }
        return s.items();
    }

    private static Map<String, String> toMap(AppOpt a) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", a.id().toString());
        m.put("name", a.name());
        m.put("icon", a.icon());
        return m;
    }

    private static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private List<App> visibleApps() {
        return appService.findAll().stream()
                .filter(a -> a.getStatus() == App.Status.APPROVED && a.getVisibility() == App.Visibility.PUBLIC)
                .sorted(Comparator.comparing(App::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}