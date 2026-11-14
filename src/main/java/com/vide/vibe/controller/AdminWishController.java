package com.vide.vibe.controller;

import com.vide.vibe.model.WishRequest;
import com.vide.vibe.service.WishRequestService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Admin screen for wishes. REPLACES your current controller for /admin/wishes
 * (same URLs: GET /admin/wishes, POST /admin/wishes/{id}/toggle, POST /admin/wishes/{id}/delete).
 * Keep it behind the same security rule as your other /admin/** pages.
 */
@Controller
@RequestMapping("/admin/wishes")
public class AdminWishController {

    private static final int PAGE_SIZE = 25;
    private static final Set<String> STATUSES = Set.of("all", "open", "handled");
    private static final Set<String> TYPES = Set.of("all", "app", "custom", "idea", "votes");

    private final WishRequestService service;

    public AdminWishController(WishRequestService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "all") String status,
                       @RequestParam(defaultValue = "all") String type,
                       @RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        status = STATUSES.contains(status) ? status : "all";
        type = TYPES.contains(type) ? type : "all";
        q = clean(q);

        Page<WishRequest> p = service.adminSearch(status, type, q, Math.max(page, 0), PAGE_SIZE);
        if (p.getTotalPages() > 0 && p.getNumber() >= p.getTotalPages()) {          // e.g. last row of the last page was deleted
            p = service.adminSearch(status, type, q, p.getTotalPages() - 1, PAGE_SIZE);
        }

        long total = service.countAll();
        long handled = service.countHandled(true);

        model.addAttribute("wishes", p.getContent());
        model.addAttribute("page", p.getNumber());
        model.addAttribute("totalPages", p.getTotalPages());
        model.addAttribute("found", p.getTotalElements());
        model.addAttribute("status", status);
        model.addAttribute("type", type);
        model.addAttribute("q", q);
        model.addAttribute("totalCount", total);
        model.addAttribute("handledCount", handled);
        model.addAttribute("openCount", total - handled);
        // what people ask for the most (apps and typed apps), to click through to their wishes
        model.addAttribute("topWishes", service.publicList("top").stream().filter(i -> !"idea".equals(i.getKind())).limit(8).toList());
        return "admin/wishes";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id,
                         @RequestParam(defaultValue = "all") String status,
                         @RequestParam(defaultValue = "all") String type,
                         @RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "0") int page,
                         RedirectAttributes ra) {
        service.toggleHandled(id);
        return back(status, type, q, page, ra);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(defaultValue = "all") String status,
                         @RequestParam(defaultValue = "all") String type,
                         @RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "0") int page,
                         RedirectAttributes ra) {
        service.delete(id);
        return back(status, type, q, page, ra);
    }

    /** CSV of the current filter (max 5000 rows), e.g. to contact everyone who wished for something. */
    @GetMapping("/export.csv")
    public void export(@RequestParam(defaultValue = "all") String status,
                       @RequestParam(defaultValue = "all") String type,
                       @RequestParam(defaultValue = "") String q,
                       HttpServletResponse resp) throws IOException {
        status = STATUSES.contains(status) ? status : "all";
        type = TYPES.contains(type) ? type : "all";
        List<WishRequest> rows = service.adminSearch(status, type, clean(q), 0, 5000).getContent();

        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"wishes.csv\"");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(resp.getOutputStream(), StandardCharsets.UTF_8));
        out.print('\uFEFF');   // so Excel reads UTF-8
        out.print("id,created,type,status,like_app,description,features,email\r\n");
        for (WishRequest w : rows) {
            String likeApp = w.getApp() != null ? w.getApp().getName() : w.getCustomAppName();
            out.print(String.join(",",
                    String.valueOf(w.getId()),
                    csv(w.getCreatedAt().format(fmt)),
                    csv(w.getKind()),
                    csv(w.isHandled() ? "handled" : "open"),
                    csv(likeApp),
                    csv(w.getDescription()),
                    csv(String.join("; ", w.getFeatures())),
                    csv(w.getEmail())));
            out.print("\r\n");
        }
        out.flush();
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private static String back(String status, String type, String q, int page, RedirectAttributes ra) {
        ra.addAttribute("status", STATUSES.contains(status) ? status : "all");
        ra.addAttribute("type", TYPES.contains(type) ? type : "all");
        ra.addAttribute("q", clean(q));
        ra.addAttribute("page", Math.max(page, 0));
        return "redirect:/admin/wishes";
    }

    private static String clean(String q) {
        q = q == null ? "" : q.trim();
        return q.length() > 100 ? q.substring(0, 100) : q;
    }

    /** CSV cell; also defuses spreadsheet formulas (=, +, -, @) in user-written text. */
    private static String csv(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}