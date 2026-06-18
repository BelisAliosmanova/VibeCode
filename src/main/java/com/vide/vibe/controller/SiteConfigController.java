package com.vide.vibe.controller;

import com.vide.vibe.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/site-config")
@RequiredArgsConstructor
public class SiteConfigController {

    private static final String SECTION_ORDER_KEY = "home_section_order";

    private final SiteConfigService siteConfigService;

    @PostMapping("/section-order")
    public ResponseEntity<Map<String, Object>> saveSectionOrder(
            @RequestBody Map<String, String> body) {
        try {
            String order = body.get("order");
            if (order == null || order.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing order value"));
            }
            siteConfigService.set(SECTION_ORDER_KEY, order);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }
}