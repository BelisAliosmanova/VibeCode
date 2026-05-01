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
 * Handles the full lifecycle of an app ownership claim:
 *
 *  sendInitialClaimEmail  — called right after an app is submitted
 *  requestClaimEmail      — called when an owner re-requests the verification link
 *  verifyAndClaim         — called when the owner clicks the link in the email
 *
 * The claim model is intentionally lightweight: we trust that the person who
 * receives the email at the submitted address is the real owner, so a single
 * token click is enough to mark them ACTIVE.
 */
@Service
@RequiredArgsConstructor
public class ClaimService {

    private static final int TOKEN_HOURS = 24;

    private final AppClaimTokenRepository tokenRepository;
    private final AppRepository           appRepository;
    private final UserRepository          userRepository;
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
            System.err.println("[ClaimService] Initial claim email failed for app " + app.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Re-sends a verification email.
     * The caller supplies the email they claim to own; it must match the app owner.
     *
     * Rate-limit: refuses to issue a new token if a valid (unexpired, unused)
     * token already exists and was created less than 5 minutes ago.
     *
     * @throws RuntimeException with a user-facing message on any validation failure
     */
    @Transactional
    public void requestClaimEmail(UUID appId, String email) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("App not found"));

        String normalizedEmail = email.trim().toLowerCase();
        String ownerEmail      = app.getOwner().getEmail().toLowerCase();

        if (!ownerEmail.equals(normalizedEmail)) {
            throw new RuntimeException("That email does not match the address used to submit this app.");
        }

        if (app.getOwner().getStatus() == User.Status.ACTIVE) {
            throw new RuntimeException("This app has already been verified.");
        }

        // Simple rate-limit: don't allow re-send within 5 minutes
        Optional<AppClaimToken> latest = tokenRepository.findTopByAppIdOrderByCreatedAtDesc(appId);
        if (latest.isPresent()) {
            AppClaimToken last = latest.get();
            boolean tooSoon = last.isValid()
                && last.getCreatedAt().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES));
            if (tooSoon) {
                throw new RuntimeException("A verification email was sent very recently. Please check your inbox or wait a few minutes before retrying.");
            }
        }

        AppClaimToken token = buildToken(app, normalizedEmail);
        tokenRepository.save(token);
        emailService.sendClaimEmail(email.trim(), app.getName(), token.getToken());
    }

    /**
     * Verifies the token from the email link and marks the owner as ACTIVE.
     *
     * @return the claimed App so the controller can redirect to the manage page
     * @throws RuntimeException with a user-facing message on any validation failure
     */
    @Transactional
    public App verifyAndClaim(String rawToken) {
        AppClaimToken claimToken = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new RuntimeException("This verification link is invalid. It may have already been used or never existed."));

        if (claimToken.isUsed()) {
            throw new RuntimeException("This link has already been used. Your ownership is already verified.");
        }
        if (claimToken.isExpired()) {
            throw new RuntimeException("This link has expired (links are valid for " + TOKEN_HOURS + " hours). Please request a new one from the manage page.");
        }

        // Consume the token
        claimToken.setUsedAt(Instant.now());
        tokenRepository.save(claimToken);

        // Promote owner to ACTIVE
        User owner = claimToken.getApp().getOwner();
        owner.setStatus(User.Status.ACTIVE);
        userRepository.save(owner);

        return claimToken.getApp();
    }

    /**
     * Returns true when the given app's owner has NOT yet verified their email.
     * Used by the manage page to decide whether to show the claim banner.
     */
    public boolean isOwnerUnverified(App app) {
        return app.getOwner().getStatus() == User.Status.PENDING;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
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