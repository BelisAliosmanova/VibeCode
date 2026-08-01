package com.vide.vibe.controller;

import com.vide.vibe.model.RankingConfig;
import com.vide.vibe.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/ranking")
@RequiredArgsConstructor
public class AdminRankingController {

    private final RankingService rankingService;

    @GetMapping
    public String edit(Model model) {
        model.addAttribute("config", rankingService.getConfig());
        return "admin/ranking";
    }

    @PostMapping
    public String update(@ModelAttribute RankingConfig config, RedirectAttributes redirectAttributes) {
        rankingService.updateConfig(config);
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/admin/ranking";
    }
}