package com.vide.vibe.service;

import com.vide.vibe.model.SiteConfig;
import com.vide.vibe.repository.SiteConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SiteConfigService {

    private final SiteConfigRepository siteConfigRepository;

    public Optional<String> get(String key) {
        return siteConfigRepository.findByConfigKey(key)
                .map(SiteConfig::getConfigValue);
    }

    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        SiteConfig config = siteConfigRepository.findByConfigKey(key)
                .orElseGet(() -> SiteConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        siteConfigRepository.save(config);
    }
}