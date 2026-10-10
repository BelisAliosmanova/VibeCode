package com.vide.vibe.model;

import jakarta.persistence.*; // use javax.persistence.* if you are on Spring Boot 2
import java.time.LocalDateTime;

@Entity
@Table(name = "wish_requests")
public class WishRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(length = 255)
    private String budget;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false)
    private boolean handled = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isHandled() { return handled; }
    public void setHandled(boolean handled) { this.handled = handled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}