package com.vide.vibe.controller;

import com.vide.vibe.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam String email, Model model) {
        passwordResetService.requestReset(email);
        // Same message either way — don't reveal whether the email exists.
        model.addAttribute("submitted", true);
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam String token, Model model) {
        boolean valid = passwordResetService.validateToken(token).isPresent();
        model.addAttribute("token", token);
        model.addAttribute("valid", valid);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPasswordSubmit(@RequestParam String token,
                                       @RequestParam String password,
                                       @RequestParam String confirmPassword,
                                       Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("valid", true);
            model.addAttribute("error", "Passwords do not match");
            return "auth/reset-password";
        }

        boolean success = passwordResetService.resetPassword(token, password);
        if (!success) {
            model.addAttribute("valid", false);
            return "auth/reset-password";
        }

        return "redirect:/auth/login?reset=1";
    }
}