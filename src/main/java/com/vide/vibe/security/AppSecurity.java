package com.vide.vibe.security;

import com.vide.vibe.model.App;
import com.vide.vibe.model.AppMedia;
import com.vide.vibe.model.User;
import com.vide.vibe.repository.AppMediaRepository;
import com.vide.vibe.service.AppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("appSecurity")
@RequiredArgsConstructor
public class AppSecurity {

    private final AppService appService;
    private final AppMediaRepository appMediaRepository;

    /** Owner, collaborator, MANAGER, or ADMIN may edit the given app. */
    public boolean canEdit(UUID appId, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return false;
        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.MANAGER) return true;

        App app = appService.findById(appId);
        if (app.getOwner().getId().equals(user.getId())) return true;
        return app.getCollaborators().stream().anyMatch(c -> c.getId().equals(user.getId()));
    }

    /** Same check, resolved from a media item instead of an appId path variable. */
    public boolean canEditMedia(UUID mediaId, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) return false;
        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.MANAGER) return true;

        AppMedia media = appMediaRepository.findById(mediaId)
                .orElseThrow(() -> new RuntimeException("Media not found: " + mediaId));
        App app = media.getApp();
        if (app.getOwner().getId().equals(user.getId())) return true;
        return app.getCollaborators().stream().anyMatch(c -> c.getId().equals(user.getId()));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (!(authentication.getPrincipal() instanceof AppPrincipal principal)) return null;
        return principal.getUser();
    }
}