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

@Service
@RequiredArgsConstructor
public class ClaimService {

    private static final int TOKEN_HOURS = 24;

    private final AppClaimTokenRepository tokenRepository;
    private final AppRepository           appRepository;
    private final EmailService            emailService;

    public void sendInitialClaimEmail(App app) {
        // temporarily removed try/catch to expose the real error in logs
        AppClaimToken token = buildToken(app, app.getOwner().getEmail());
        tokenRepository.save(token);
        emailService.sendClaimEmail(app.getOwner().getEmail(), app.getName(), token.getToken());
    }

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

        claimToken.setUsedAt(Instant.now());
        tokenRepository.save(claimToken);

        App app = claimToken.getApp();
        app.setClaimedAt(Instant.now());
        appRepository.save(app);

        return app;
    }

    public boolean isOwnerUnverified(App app) {
        return !app.isClaimed();
    }

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