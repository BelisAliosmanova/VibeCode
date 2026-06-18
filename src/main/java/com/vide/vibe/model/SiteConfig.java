package com.vide.vibe.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "site_config")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SiteConfig extends BaseEntity {

    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;
}