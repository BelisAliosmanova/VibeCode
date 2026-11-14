package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.repository.AppOptRepository;
import com.vide.vibe.repository.AppOptView;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.WishRequestService;
import com.vide.vibe.service.WishRequestService.AppWishStats;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class WishController {

    private static final String EMAIL_RE = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final int SEARCH_LIMIT = 40;
    private static final int OTHERS_LIMIT = 12;
    private static final String VOTE_COOKIE = "wish_plus";

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
        private String appName;     // what is typed in "An app like..." (free text, optional)
        private String description;
        private String features;    // comma separated tags
        private String email;       // optional
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
    /**
     * Prefix matches first (fast, index friendly), then "contains" matches when the query has 3+ letters.
     * Always limited, never loads the whole catalog.
     */
    @GetMapping("/wish/apps")
    @ResponseBody
    public Map<String, Object> searchApps(@RequestParam(defaultValue = "") String q) {
        q = q.trim().toLowerCase(Locale.ROOT);
        if (q.length() > 120) q = q.substring(0, 120);
        String esc = escapeLike(q);

        List<AppOptView> rows = new ArrayList<>(optRepo.searchLike(esc + "%",
                App.Status.APPROVED, App.Visibility.PUBLIC, PageRequest.of(0, SEARCH_LIMIT + 1)));

        if (rows.size() <= SEARCH_LIMIT && q.length() >= 3) {
            Set<UUID> seen = new HashSet<>();
            rows.forEach(a -> seen.add(a.getId()));
            for (AppOptView a : optRepo.searchLike("%" + esc + "%",
                    App.Status.APPROVED, App.Visibility.PUBLIC, PageRequest.of(0, SEARCH_LIMIT + 1))) {
                if (rows.size() > SEARCH_LIMIT) break;
                if (seen.add(a.getId())) rows.add(a);
            }
        }

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
            App a = like == null ? null : publicApp(like);
            if (a != null) {
                f.setAppId(a.getId().toString());
                f.setAppName(a.getName());
                Map<String, String> sel = new LinkedHashMap<>();
                sel.put("id", a.getId().toString());
                sel.put("name", a.getName());
                sel.put("icon", a.getIconUrl() == null ? "" : a.getIconUrl());
                model.addAttribute("selectedApp", sel);
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
        // 2) anything else typed -> keep as free text
        // Only small, targeted queries here (no loading of all apps).
        AppOptView linked = null;
        if (form.getAppId() != null && !form.getAppId().isBlank() && typed.length() <= 120) {
            try {
                linked = optRepo.findOpt(UUID.fromString(form.getAppId()),
                        App.Status.APPROVED, App.Visibility.PUBLIC).orElse(null);
            } catch (IllegalArgumentException ignored) {}
            if (linked != null && !typed.isEmpty() && !typed.equalsIgnoreCase(linked.getName())) linked = null; // edited after picking
        }
        if (linked == null && !typed.isEmpty() && typed.length() <= 120) {
            linked = optRepo.findByNameIgnoreCase(typed, App.Status.APPROVED, App.Visibility.PUBLIC, PageRequest.of(0, 1))
                    .stream().findFirst().orElse(null);
        }
        String custom = linked == null && !typed.isEmpty() ? typed : null;

        String error = null;
        int step = 2;
        if (typed.length() > 120) {
            error = "The app name is too long.";
            step = 1;
        } else if (linked == null && custom == null && desc.length() < 10) {
            error = "Describe the app you wish for in a bit more detail, or tell us which app it should be like.";
            step = 1;
        } else if (desc.length() > 4000) {
            error = "The description is too long.";
            step = 1;
        } else if (!email.isEmpty() && (!email.matches(EMAIL_RE) || email.length() > 255)) {   // email is optional
            error = "That email doesn't look right. Fix it or leave it empty.";
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
        model.addAttribute("items", service.publicList());
        return "wishes";
    }

    // ── "Wish an app like X" ──────────────────────────────────────────────────
    @GetMapping("/wish/like/{slug}")
    public String like(@PathVariable String slug, Model model) {
        App app = publicApp(slug);
        if (app == null) return "redirect:/wish";

        AppWishStats mine = service.statsFor(app.getId());
        model.addAttribute("app", app);
        model.addAttribute("topFeatures", mine.getTopFeatures());
        model.addAttribute("wishCount", mine.getCount());
        // only apps that actually have wishes, most wished first, capped
        model.addAttribute("others", service.topWishedApps(app.getId(), OTHERS_LIMIT));
        return "wish-like";
    }

    /** Quick "+1": the person just adds their vote to this app's count. Email is optional. */
    @PostMapping("/wish/like/{slug}/plus-one")
    public String plusOne(@PathVariable String slug,
                          @RequestParam(required = false, defaultValue = "") String email,
                          @RequestParam(required = false) String features,
                          @RequestParam(required = false) String website,
                          @CookieValue(name = VOTE_COOKIE, required = false) String voted,
                          HttpServletResponse resp,
                          RedirectAttributes ra) {
        App app = publicApp(slug);
        if (app == null) return "redirect:/wish";
        String back = "redirect:/wish/like/" + slug;

        if (website != null && !website.isBlank()) {            // honeypot
            ra.addFlashAttribute("plusOne", "added");
            return back;
        }
        email = email.trim();
        if (!email.isEmpty() && (!email.matches(EMAIL_RE) || email.length() > 255)) {
            ra.addFlashAttribute("plusOneError", "That email doesn't look right. Fix it or leave it empty.");
            return back;
        }

        // Anonymous votes: a cookie remembers which apps this browser already voted for.
        LinkedHashSet<String> votedSlugs = new LinkedHashSet<>();
        if (voted != null && voted.length() < 3000) votedSlugs.addAll(Arrays.asList(voted.split("\\.")));
        if (email.isEmpty() && votedSlugs.contains(slug)) {
            ra.addFlashAttribute("plusOne", "exists");
            return back;
        }

        boolean added = service.plusOne(app.getId(), email, WishRequestService.parseFeatures(features));
        if (added && email.isEmpty()) {
            votedSlugs.add(slug);
            List<String> keep = new ArrayList<>(votedSlugs);
            if (keep.size() > 40) keep = keep.subList(keep.size() - 40, keep.size());
            Cookie c = new Cookie(VOTE_COOKIE, String.join(".", keep));
            c.setPath("/");
            c.setMaxAge(365 * 24 * 3600);
            c.setHttpOnly(true);
            resp.addCookie(c);
        }
        ra.addFlashAttribute("plusOne", added ? "added" : "exists");
        return back;
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private void populate(Model model) {
        // NOTE: the apps are not inlined into the page; the dropdown calls /wish/apps
        model.addAttribute("suggested", service.suggestedFeatures());
    }

    /** The app for this slug, only if it is approved + public (drafts/private apps are not wishable). */
    private App publicApp(String slug) {
        try {
            App a = appService.findBySlug(slug);
            return a.getStatus() == App.Status.APPROVED && a.getVisibility() == App.Visibility.PUBLIC ? a : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, String> toMap(AppOptView a) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("name", a.getName());
        m.put("icon", a.getIconUrl() == null ? "" : a.getIconUrl());
        return m;
    }

    /** Escape LIKE wildcards with '!' (matches `escape '!'` in AppOptRepository). */
    private static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}