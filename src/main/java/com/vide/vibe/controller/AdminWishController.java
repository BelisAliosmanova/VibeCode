package com.vide.vibe.controller;

import com.vide.vibe.service.WishRequestService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Make sure /admin/** is restricted to ADMIN/MANAGER in your security config
 * (you already do this for /admin/home-sections/**).
 */
@Controller
@RequestMapping("/admin/wishes")
public class AdminWishController {

    private final WishRequestService service;

    public AdminWishController(WishRequestService service) {
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        var wishes = service.findAll();
        model.addAttribute("wishes", wishes);
        model.addAttribute("openCount", wishes.stream().filter(w -> !w.isHandled()).count());
        return "admin/wishes";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        service.toggleHandled(id);
        return "redirect:/admin/wishes";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/admin/wishes";
    }
}