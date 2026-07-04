package com.vide.vibe.security;

import com.vide.vibe.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;

/** Wraps our own User entity so AppSecurity can treat Google logins like any other principal. */
public class AppOidcUserPrincipal extends DefaultOidcUser implements AppPrincipal {

    private final User user;

    public AppOidcUserPrincipal(User user, Collection<? extends GrantedAuthority> authorities,
                                 OidcIdToken idToken, OidcUserInfo userInfo) {
        super(authorities, idToken, userInfo, "email");
        this.user = user;
    }

    @Override
    public User getUser() {
        return user;
    }
}