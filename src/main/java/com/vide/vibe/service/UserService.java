package com.vide.vibe.service;

import com.vide.vibe.model.User;
import com.vide.vibe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    @Transactional
    public User create(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * Looks up a user by exact email match; if none exists, creates a
     * bare-bones account (no password, LOCAL provider, PENDING status) so
     * it can be assigned as an app owner right away. The user can claim
     * the account later via normal signup/password-reset flow.
     */
    @Transactional
    public User findOrCreateByEmail(String rawEmail) {
        if (rawEmail == null) {
            throw new IllegalArgumentException("Email cannot be blank");
        }
        String email = rawEmail.trim();
        if (email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Not a valid email address: " + email);
        }

        return findByEmail(email).orElseGet(() -> {
            User user = User.builder()
                    .email(email)
                    .passwordHash("")
                    .role(User.Role.USER)
                    .status(User.Status.PENDING)
                    .authProvider(User.AuthProvider.LOCAL)
                    .build();
            return userRepository.save(user);
        });
    }

    // ── Admin user management ───────────────────────────────────────────

    public List<User> findAll() {
        return userRepository.findAllByDeletedAtIsNullOrderByEmailAsc();
    }

    @Transactional
    public User updateRole(UUID targetId, User.Role newRole, UUID actingAdminId) {
        if (targetId.equals(actingAdminId) && newRole != User.Role.ADMIN) {
            throw new IllegalStateException("You can't remove your own admin role.");
        }
        User user = findById(targetId);
        user.setRole(newRole);
        return userRepository.save(user);
    }

    @Transactional
    public User updateStatus(UUID targetId, User.Status newStatus, UUID actingAdminId) {
        if (targetId.equals(actingAdminId) && newStatus == User.Status.BANNED) {
            throw new IllegalStateException("You can't ban your own account.");
        }
        User user = findById(targetId);
        user.setStatus(newStatus);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID targetId, UUID actingAdminId) {
        if (targetId.equals(actingAdminId)) {
            throw new IllegalStateException("You can't delete your own account.");
        }
        User user = findById(targetId);
        // Free the email up for reuse, same reasoning as the slug-mangling fix in
        // CategoryService — the email column has a physical unique index that
        // doesn't know about deletedAt, so a soft-deleted row would otherwise
        // block re-registration with that email forever.
        user.setEmail(mangleEmail(user.getEmail(), user.getId()));
        user.softDelete();
        userRepository.save(user);
    }

    private String mangleEmail(String email, UUID id) {
        String suffix = "+deleted-" + id;
        int maxLen = 320; // matches @Column(length = 320) on User.email
        if (email.length() + suffix.length() <= maxLen) {
            return email + suffix;
        }
        int allowed = Math.max(0, maxLen - suffix.length());
        return email.substring(0, allowed) + suffix;
    }
}