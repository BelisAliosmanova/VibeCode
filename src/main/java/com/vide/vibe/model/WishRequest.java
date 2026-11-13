package com.vide.vibe.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "wish_requests")
public class WishRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "An app like ..." — optional, the user may just describe their app. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id")
    private App app;

    /** Free-text "an app like ..." when the app is not in our catalog. */
    @Column(length = 120)
    private String customAppName;

    /** Stored as "" when the user only picked an app (keeps the old NOT NULL column valid). */
    @Column(nullable = false, length = 4000)
    private String description;

    /** Legacy column, no longer in the form. */
    @Column(length = 255)
    private String budget;

    @Column(nullable = false, length = 255)
    private String email;

    /** Desired features / tags (lowercase). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wish_request_features", joinColumns = @JoinColumn(name = "wish_request_id"))
    @Column(name = "feature", length = 40)
    private Set<String> features = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean handled = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public App getApp() { return app; }
    public void setApp(App app) { this.app = app; }
    public String getCustomAppName() { return customAppName; }
    public void setCustomAppName(String customAppName) { this.customAppName = customAppName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Set<String> getFeatures() { return features; }
    public void setFeatures(Set<String> features) { this.features = features; }
    public boolean isHandled() { return handled; }
    public void setHandled(boolean handled) { this.handled = handled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}