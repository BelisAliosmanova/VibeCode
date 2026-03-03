package com.vide.vibe.model;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_role",  columnList = "role"),
        @Index(name = "idx_users_status", columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends SoftDeletableEntity {

    public enum Role {
        ADMIN, USER
    }

    public enum Status {
        ACTIVE, PENDING, BANNED
    }

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    // ── Relationships ──────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "owner", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<App> ownedApps = new LinkedHashSet<>();

    @ManyToMany(mappedBy = "collaborators", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<App> collaboratingApps = new LinkedHashSet<>();
}