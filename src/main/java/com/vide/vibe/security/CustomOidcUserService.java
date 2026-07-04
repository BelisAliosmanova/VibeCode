package com.vide.vibe.security;

import com.vide.vibe.model.User;
import com.vide.vibe.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserService userService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Google account did not return an email address");
        }

        User user = userService.findByEmail(email)
                .orElseGet(() -> userService.create(
                        User.builder()
                                .email(email)
                                .passwordHash(null)
                                .role(User.Role.USER)
                                .status(User.Status.ACTIVE)
                                .authProvider(User.AuthProvider.GOOGLE)
                                .build()
                ));

        // Note: if a LOCAL account with this email already exists, this silently
        // signs them into that same account via Google. That's usually what you
        // want (account linking), but if you'd rather block it to avoid a
        // confused-deputy/account-takeover edge case where an attacker registers
        // a Google account with a victim's email, check
        // user.getAuthProvider() == User.AuthProvider.LOCAL here and reject instead.

        if (user.getStatus() == User.Status.BANNED) {
            throw new IllegalStateException("This account has been banned");
        }

        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        return new AppOidcUserPrincipal(user, authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}