package com.vide.vibe.service;

import com.vide.vibe.model.PasswordResetToken;
import com.vide.vibe.model.User;
import com.vide.vibe.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Always returns normally, regardless of whether the email exists — avoids leaking account existence. */
    public void requestReset(String email) {
        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) return;

        User user = userOpt.get();
        if (user.getAuthProvider() == User.AuthProvider.GOOGLE) {
            // Google-only account — no local password to reset.
            // Optionally send a "you signed up with Google" email here instead.
            return;
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        tokenRepository.save(resetToken);

        String resetLink = baseUrl + "/auth/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Reset your Vibe password");
        message.setText("""
                We received a request to reset your Vibe password.

                Click the link below to choose a new one. This link expires in 1 hour:
                %s

                If you didn't request this, you can safely ignore this email.
                """.formatted(resetLink));
        mailSender.send(message);
    }

    public Optional<PasswordResetToken> validateToken(String token) {
        return tokenRepository.findByToken(token)
                .filter(PasswordResetToken::isValid);
    }

    public boolean resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOpt = validateToken(token);
        if (tokenOpt.isEmpty()) return false;

        PasswordResetToken resetToken = tokenOpt.get();
        User user = resetToken.getUser();

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userService.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        return true;
    }
}