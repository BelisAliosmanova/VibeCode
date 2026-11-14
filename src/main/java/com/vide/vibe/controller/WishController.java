package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.model.WishRequest;
import com.vide.vibe.repository.AppOptRepository;
import com.vide.vibe.repository.AppOptView;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.WishFeatureCatalog;
import com.vide.vibe.service.WishRequestService;
import com.vide.vibe.service.WishRequestService.AppWishStats;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;

@Controller
public class WishController {

    private static final String EMAIL_RE = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final int SEARCH_LIMIT = 40;
    private static final int OTHERS_LIMIT = 12;
    private static final String VOTE_COOKIE = "wish_plus";

    private final WishRequestService service;
    private final AppService appService;
    private final AppOptRepository optRepo;
    private final WishFeatureCatalog featureCatalog;

    public WishController(WishRequestService service, AppService appService,
                          AppOptRepository optRepo, WishFeatureCatalog featureCatalog) {
        this.service = service;
        this.appService = appService;
        this.optRepo = optRepo;
        this.featureCatalog = featureCatalog;
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

    // ── JSON: "An app like…" suggestions ──────────────────────────────────────
    /**
     * Prefix matches first (fast, index friendly), then "contains" matches when the query has 3+ letters.
     * Always limited, never loads the whole catalog. Same app rule as Explore: not deleted, not rejected.
     */
    @GetMapping("/wish/apps")
    @ResponseBody
    public Map<String, Object> searchApps(@RequestParam(defaultValue = "") String q) {
        q = q.trim().toLowerCase(Locale.ROOT);
        if (q.length() > 120) q = q.substring(0, 120);
        String esc = escapeLike(q);

        List<AppOptView> rows = new ArrayList<>(optRepo.searchLike(esc + "%", App.Status.REJECTED,
                PageRequest.of(0, SEARCH_LIMIT + 1)));

        if (rows.size() <= SEARCH_LIMIT && q.length() >= 3) {
            Set<UUID> seen = new HashSet<>();
            rows.forEach(a -> seen.add(a.getId()));
            for (AppOptView a : optRepo.searchLike("%" + esc + "%", App.Status.REJECTED,
                    PageRequest.of(0, SEARCH_LIMIT + 1))) {
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

    /** JSON: feature suggestions from the category entries (same ones as the Explore filters). */
    @GetMapping("/wish/features")
    @ResponseBody
    public List<String> searchFeatures(@RequestParam(defaultValue = "") String q) {
        return featureCatalog.search(q.length() > 60 ? q.substring(0, 60) : q, 8);
    }

    // ── Step 1 + 2: the wish form ─────────────────────────────────────────────
    @GetMapping("/wish")
    public String form(@RequestParam(required = false) String like,
                       @RequestParam(required = false) String likeName,
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
            } else if (likeName != null && !likeName.isBlank()) {
                String n = likeName.trim();
                f.setAppName(n.length() > 120 ? n.substring(0, 120) : n);
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
        AppOptView linked = null;
        if (form.getAppId() != null && !form.getAppId().isBlank() && typed.length() <= 120) {
            try {
                linked = optRepo.findOpt(UUID.fromString(form.getAppId()), App.Status.REJECTED).orElse(null);
            } catch (IllegalArgumentException ignored) {}
            if (linked != null && !typed.isEmpty() && !typed.equalsIgnoreCase(linked.getName())) linked = null; // edited after picking
        }
        if (linked == null && !typed.isEmpty() && typed.length() <= 120) {
            linked = optRepo.findByNameIgnoreCase(typed, App.Status.REJECTED, PageRequest.of(0, 1))
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

        WishRequest saved = service.submit(linked == null ? null : linked.getId(), custom, desc, email,
                WishRequestService.parseFeatures(form.getFeatures()));
        // go straight to the page of the new wish, so the person sees it right away
        ra.addFlashAttribute("wishSent", true);
        return "redirect:" + WishRequestService.detailPath(saved);
    }

    // ── "All app wishes" ──────────────────────────────────────────────────────
    @GetMapping("/wishes")
    public String all(@RequestParam(defaultValue = "top") String sort, Model model) {
        sort = "new".equals(sort) ? "new" : "top";
        model.addAttribute("sort", sort);
        model.addAttribute("items", service.publicList(sort));
        return "wishes";
    }

    // ── Detail 1: wishes "like" one of our apps ───────────────────────────────
    @GetMapping("/wish/like/{slug}")
    public String like(@PathVariable String slug, Model model) {
        App app = publicApp(slug);
        if (app == null) return "redirect:/wish";
        detail(model, "Wish an app like " + app.getName(),
                null, List.of(), "Most popular wishes", service.statsFor(app.getId()),
                "/wish/like/" + app.getSlug() + "/plus-one", null,
                "like=" + enc(app.getSlug()), "/wish/like/" + app.getSlug());
        return "wish-like";
    }

    @PostMapping("/wish/like/{slug}/plus-one")
    public String plusOneApp(@PathVariable String slug,
                             @RequestParam(required = false, defaultValue = "") String email,
                             @RequestParam(required = false) String features,
                             @RequestParam(required = false) String website,
                             @CookieValue(name = VOTE_COOKIE, required = false) String voted,
                             HttpServletResponse resp, RedirectAttributes ra) {
        App app = publicApp(slug);
        if (app == null) return "redirect:/wish";
        return vote("a_" + app.getSlug(), "redirect:/wish/like/" + app.getSlug(), email, website, voted, resp, ra,
                () -> service.plusOne(app.getId(), email.trim(), WishRequestService.parseFeatures(features)));
    }

    // ── Detail 2: wishes "like" an app that is not in our catalog ─────────────
    @GetMapping("/wish/similar")
    public String similar(@RequestParam String name, Model model) {
        String key = WishRequestService.key(name);
        AppWishStats stats = service.statsForCustom(key);
        String display = service.customDisplayName(key);
        if (stats.getCount() == 0 || display == null) return "redirect:/wishes";
        detail(model, "Wish an app like " + display,
                "What people wrote", service.customDescriptions(key, 10), "Most popular wishes", stats,
                "/wish/similar/plus-one", display,
                "likeName=" + enc(display), "/wish/similar?name=" + enc(display));
        return "wish-like";
    }

    @PostMapping("/wish/similar/plus-one")
    public String plusOneCustom(@RequestParam String name,
                                @RequestParam(required = false, defaultValue = "") String email,
                                @RequestParam(required = false) String features,
                                @RequestParam(required = false) String website,
                                @CookieValue(name = VOTE_COOKIE, required = false) String voted,
                                HttpServletResponse resp, RedirectAttributes ra) {
        String key = WishRequestService.key(name);
        String display = service.customDisplayName(key);
        if (display == null) return "redirect:/wishes";
        return vote("c_" + Integer.toHexString(key.hashCode()), "redirect:/wish/similar?name=" + enc(display),
                email, website, voted, resp, ra,
                () -> service.plusOneCustom(display, email.trim(), WishRequestService.parseFeatures(features)));
    }

    // ── Detail 3: a plain wish (no "like") ────────────────────────────────────
    @GetMapping("/wish/about/{id}")
    public String about(@PathVariable Long id, Model model) {
        WishRequest w = service.findIdea(id).orElse(null);
        if (w == null) return "redirect:/wishes";
        detail(model, "Wished app",
                "The wish", List.of(w.getDescription()), "Selected features",
                new AppWishStats(service.ideaCount(id), new ArrayList<>(w.getFeatures())),
                "/wish/about/" + id + "/plus-one", null,
                "", "/wish/about/" + id);
        return "wish-like";
    }

    @PostMapping("/wish/about/{id}/plus-one")
    public String plusOneIdea(@PathVariable Long id,
                              @RequestParam(required = false, defaultValue = "") String email,
                              @RequestParam(required = false) String features,
                              @RequestParam(required = false) String website,
                              @CookieValue(name = VOTE_COOKIE, required = false) String voted,
                              HttpServletResponse resp, RedirectAttributes ra) {
        if (service.findIdea(id).isEmpty()) return "redirect:/wishes";
        return vote("i_" + id, "redirect:/wish/about/" + id, email, website, voted, resp, ra,
                () -> service.plusOneIdea(id, email.trim(), WishRequestService.parseFeatures(features)));
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    /** Fills the model of the shared detail template (wish-like.html). */
    private void detail(Model m, String heading, String descLabel, List<String> descriptions, String featuresLabel,
                        AppWishStats stats, String plusAction, String plusName, String newWishQuery, String path) {
        m.addAttribute("heading", heading);
        m.addAttribute("descLabel", descLabel);
        m.addAttribute("descriptions", descriptions);
        m.addAttribute("featuresLabel", featuresLabel);
        m.addAttribute("topFeatures", stats.getTopFeatures());
        m.addAttribute("wishCount", stats.getCount());
        m.addAttribute("plusAction", plusAction);
        m.addAttribute("plusName", plusName);
        m.addAttribute("newWishQuery", newWishQuery);
        // only wishes that exist (never empty apps), most wished first, capped
        m.addAttribute("others", service.others(path, OTHERS_LIMIT));
    }

    /** Shared +1 logic. Email is optional; anonymous votes are remembered per browser with a cookie. */
    private String vote(String voteKey, String back, String email, String website, String voted,
                        HttpServletResponse resp, RedirectAttributes ra, BooleanSupplier add) {
        if (website != null && !website.isBlank()) {            // honeypot
            ra.addFlashAttribute("plusOne", "added");
            return back;
        }
        email = email.trim();
        if (!email.isEmpty() && (!email.matches(EMAIL_RE) || email.length() > 255)) {
            ra.addFlashAttribute("plusOneError", "That email doesn't look right. Fix it or leave it empty.");
            return back;
        }
        LinkedHashSet<String> votedKeys = new LinkedHashSet<>();
        if (voted != null && voted.length() < 3000) votedKeys.addAll(Arrays.asList(voted.split("\\.")));
        if (email.isEmpty() && votedKeys.contains(voteKey)) {
            ra.addFlashAttribute("plusOne", "exists");
            return back;
        }

        boolean added = add.getAsBoolean();
        if (added && email.isEmpty()) {
            votedKeys.add(voteKey);
            List<String> keep = new ArrayList<>(votedKeys);
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

    private void populate(Model model) {
        // NOTE: neither the apps nor the features are inlined; the form calls /wish/apps and /wish/features
        model.addAttribute("suggested", service.suggestedFeatures(featureCatalog.top(10)));
    }

    /** The app for this slug, only if it is shown on Explore (not deleted, not rejected). */
    private App publicApp(String slug) {
        try {
            App a = appService.findBySlug(slug);
            return a.getStatus() != App.Status.REJECTED ? a : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

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