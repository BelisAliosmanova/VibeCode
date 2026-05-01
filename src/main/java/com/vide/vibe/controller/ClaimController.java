package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.service.ClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Two responsibilities:
 *
 *  POST /apps/{appId}/claim/request  — trigger (or re-trigger) the verification email
 *  GET  /claim/verify/{token}         — land here from the email link, consume the token
 */
@Controller
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    // ─── Re-send / first-time request ─────────────────────────────────────────
    // Called via AJAX from the manage page — returns JSON.

    @PostMapping("/apps/{appId}/claim/request")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestClaim(
            @PathVariable UUID appId,
            @RequestParam String email) {
        try {
            claimService.requestClaimEmail(appId, email);
            return ResponseEntity.ok(Map.of(
                    "ok",      true,
                    "message", "Verification email sent! Check your inbox."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    // ─── Email link landing page ───────────────────────────────────────────────
    // The link in the email points here.  On success we redirect to the manage page.

    @GetMapping("/claim/verify/{token}")
    public String verifyToken(@PathVariable String token, Model model) {
        try {
            App app = claimService.verifyAndClaim(token);
            // Redirect to manage page with a success flash param
            return "redirect:/apps/" + app.getId() + "/manage?claimed=1";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "claim/result";
        }
    }
}