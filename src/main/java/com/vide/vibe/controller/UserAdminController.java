package com.vide.vibe.controller;

import com.vide.vibe.model.User;
import com.vide.vibe.security.AppPrincipal;
import com.vide.vibe.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;

    @GetMapping
    public String list(Model model, @AuthenticationPrincipal AppPrincipal principal) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", User.Role.values());
        model.addAttribute("statuses", User.Status.values());
        model.addAttribute("currentUserId", principal.getUser().getId());
        return "admin/users";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable UUID id,
                              @RequestParam User.Role role,
                              @AuthenticationPrincipal AppPrincipal principal,
                              org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            userService.updateRole(id, role, principal.getUser().getId());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable UUID id,
                                @RequestParam User.Status status,
                                @AuthenticationPrincipal AppPrincipal principal,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            userService.updateStatus(id, status, principal.getUser().getId());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable UUID id,
                              @AuthenticationPrincipal AppPrincipal principal,
                              org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(id, principal.getUser().getId());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}