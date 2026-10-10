package com.vide.vibe.controller;

import com.vide.vibe.service.WishRequestService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WishController {

    private final WishRequestService service;

    public WishController(WishRequestService service) {
        this.service = service;
    }

    /** Form backing object (kept separate from the entity so users can't bind id/createdAt/handled). */
    public static class WishForm {
        private String description;
        private String budget;
        private String email;
        private String website; // honeypot: real users never fill this

        public String getDescription() { return description; }
        public void setDescription(String d) { this.description = d; }
        public String getBudget() { return budget; }
        public void setBudget(String b) { this.budget = b; }
        public String getEmail() { return email; }
        public void setEmail(String e) { this.email = e; }
        public String getWebsite() { return website; }
        public void setWebsite(String w) { this.website = w; }
    }

    @GetMapping("/wish")
    public String form(Model model) {
        if (!model.containsAttribute("wishForm")) {
            model.addAttribute("wishForm", new WishForm());
        }
        return "wish";
    }

    @PostMapping("/wish")
    public String submit(@ModelAttribute("wishForm") WishForm form,
                         Model model,
                         RedirectAttributes ra) {

        // Bots fill the hidden field: pretend success, store nothing
        if (form.getWebsite() != null && !form.getWebsite().isBlank()) {
            ra.addFlashAttribute("success", true);
            return "redirect:/wish";
        }

        String desc = form.getDescription() == null ? "" : form.getDescription().trim();
        String email = form.getEmail() == null ? "" : form.getEmail().trim();

        if (desc.length() < 10) {
            model.addAttribute("error", "Please describe the app in a bit more detail.");
            return "wish";
        }
        if (desc.length() > 4000 || (form.getBudget() != null && form.getBudget().length() > 255)) {
            model.addAttribute("error", "One of the fields is too long.");
            return "wish";
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || email.length() > 255) {
            model.addAttribute("error", "Please enter a valid email address.");
            return "wish";
        }

        service.submit(desc, form.getBudget(), email);
        ra.addFlashAttribute("success", true);
        return "redirect:/wish";
    }
}