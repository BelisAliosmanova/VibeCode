package com.vide.vibe.service;

import com.vide.vibe.model.*;
import com.vide.vibe.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles per-app email ownership verification.
 *
 * Key design decision: the claim flag lives on App.claimedAt, NOT on User.status.
 * This means:
 *   - Verifying app A never affects app B, even if they share the same owner email.
 *   - Each app must be claimed independently by whoever submitted it.
 *   - User.status is left untouched by this flow entirely.
 */
@Service
@RequiredArgsConstructor
public class ClaimService {

    private static final int TOKEN_HOURS = 24;

    private final AppClaimTokenRepository tokenRepository;
    private final AppRepository           appRepository;
    private final EmailService            emailService;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Automatically invoked when a new app is submitted.
     * Silently swallows errors so a broken mail config never blocks submission.
     */
    public void sendInitialClaimEmail(App app) {
        try {
            AppClaimToken token = buildToken(app, app.getOwner().getEmail());
            tokenRepository.save(token);
            emailService.sendClaimEmail(app.getOwner().getEmail(), app.getName(), token.getToken());
        } catch (Exception e) {
            System.err.println("[ClaimService] Initial claim email failed for app "
                    + app.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Re-sends a verification email on demand from the manage page.
     *
     * Validation rules:
     *  - The supplied email must match the app's owner email.
     *  - The app must not already be claimed.
     *  - A valid token created less than 5 minutes ago blocks re-sends (rate limit).
     */
    @Transactional
    public void requestClaimEmail(UUID appId, String email) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("App not found"));

        if (app.isClaimed()) {
            throw new RuntimeException("This app has already been verified.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        String ownerEmail      = app.getOwner().getEmail().toLowerCase();

        if (!ownerEmail.equals(normalizedEmail)) {
            throw new RuntimeException(
                    "That email does not match the address used to submit this app.");
        }

        // Rate-limit: block re-send within 5 minutes of a still-valid token
        Optional<AppClaimToken> latest =
                tokenRepository.findTopByAppIdOrderByCreatedAtDesc(appId);
        if (latest.isPresent()) {
            AppClaimToken last = latest.get();
            boolean tooSoon = last.isValid()
                    && last.getCreatedAt().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES));
            if (tooSoon) {
                throw new RuntimeException(
                        "A verification email was sent very recently. "
                                + "Please check your inbox or wait a few minutes before retrying.");
            }
        }

        AppClaimToken token = buildToken(app, normalizedEmail);
        tokenRepository.save(token);
        emailService.sendClaimEmail(email.trim(), app.getName(), token.getToken());
    }

    /**
     * Consumes the token from the email link and marks THIS app as claimed.
     * No other app is affected, even if owned by the same email address.
     *
     * @return the claimed App so the controller can redirect to its manage page
     */
    @Transactional
    public App verifyAndClaim(String rawToken) {
        AppClaimToken claimToken = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new RuntimeException(
                        "This verification link is invalid. "
                                + "It may have already been used or never existed."));

        if (claimToken.isUsed()) {
            throw new RuntimeException(
                    "This link has already been used. "
                            + "Your ownership of this app is already verified.");
        }
        if (claimToken.isExpired()) {
            throw new RuntimeException(
                    "This link has expired (links are valid for " + TOKEN_HOURS + " hours). "
                            + "Please request a new one from the manage page.");
        }

        // Consume the token
        claimToken.setUsedAt(Instant.now());
        tokenRepository.save(claimToken);

        // Mark only this specific app as claimed — no other app is touched
        App app = claimToken.getApp();
        app.setClaimedAt(Instant.now());
        appRepository.save(app);

        return app;
    }

    /**
     * Whether this specific app's ownership email has been verified.
     * Checks App.claimedAt, not User.status — fully per-app.
     */
    public boolean isOwnerUnverified(App app) {
        return !app.isClaimed();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private AppClaimToken buildToken(App app, String normalizedEmail) {
        return AppClaimToken.builder()
                .app(app)
                .email(normalizedEmail)
                .token(generateToken())
                .expiresAt(Instant.now().plus(TOKEN_HOURS, ChronoUnit.HOURS))
                .build();
    }

    private String generateToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}