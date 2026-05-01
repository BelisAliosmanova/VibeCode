package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A short-lived token sent to an app owner's email so they can prove
 * they control that address and "claim" the listing.
 *
 * Flow:
 *  1. App is submitted with an owner email → token row is created & email sent.
 *  2. Owner clicks the link → token is consumed, User.status → ACTIVE.
 *  3. If the token expires the owner can request a fresh one from the manage page.
 */
@Entity
@Table(
    name = "app_claim_tokens",
    indexes = {
        @Index(name = "idx_claim_token",  columnList = "token",  unique = true),
        @Index(name = "idx_claim_app_id", columnList = "app_id"),
        @Index(name = "idx_claim_email",  columnList = "email")
    }
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AppClaimToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_claim_token_app"))
    private App app;

    /** The email address this token was sent to. */
    @Column(name = "email", nullable = false, length = 320)
    private String email;

    /** URL-safe Base64 random token (64 chars). */
    @Column(name = "token", nullable = false, unique = true, length = 128)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Non-null once the owner has clicked the verification link. */
    @Column(name = "used_at")
    private Instant usedAt;

    // ── helpers ─────────────────────────────────────────────────────────────

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isUsed()    { return usedAt != null; }
    public boolean isValid()   { return !isExpired() && !isUsed(); }
}