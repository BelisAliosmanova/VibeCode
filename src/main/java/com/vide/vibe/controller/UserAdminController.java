package com.vide.vibe.controller;

import com.vide.vibe.model.User;
import com.vide.vibe.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", User.Role.values());
        return "admin/users";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable UUID id, @RequestParam User.Role role) {
        userService.updateRole(id, role);
        return "redirect:/admin/users";
    }
}