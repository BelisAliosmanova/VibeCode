package com.vide.vibe.controller;

import com.vide.vibe.model.App;
import com.vide.vibe.model.AppMedia;
import com.vide.vibe.repository.AppMediaRepository;
import com.vide.vibe.service.AppService;
import com.vide.vibe.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;
    private final AppService appService;
    private final AppMediaRepository appMediaRepository;

    /**
     * Upload an icon for an app.
     * Returns JSON: { "url": "/images/icons/uuid.png" }
     */
    @PostMapping("/upload/icon/{appId}")
    @ResponseBody
    public ResponseEntity<?> uploadIcon(
            @PathVariable UUID appId,
            @RequestParam("file") MultipartFile file) {
        try {
            App app = appService.findById(appId);

            // Delete old icon if present
            if (app.getIconUrl() != null) {
                mediaService.delete(app.getIconUrl());
            }

            String url = mediaService.upload(file, "icons");
            appService.updateIconUrl(appId, url);

            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Upload a screenshot / media for an app.
     * Returns JSON: { "url": "...", "mediaId": "..." }
     */
    @PostMapping("/upload/screenshot/{appId}")
    @ResponseBody
    public ResponseEntity<?> uploadScreenshot(
            @PathVariable UUID appId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "position", defaultValue = "0") int position) {
        try {
            App app = appService.findById(appId);

            String url = mediaService.upload(file, "screenshots");

            AppMedia media = AppMedia.builder()
                    .app(app)
                    .type(AppMedia.MediaType.SCREENSHOT)
                    .url(url)
                    .position(position)
                    .build();

            AppMedia saved = appMediaRepository.save(media);

            return ResponseEntity.ok(Map.of(
                    "url", url,
                    "mediaId", saved.getId().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a media item.
     */
    @DeleteMapping("/{mediaId}")
    @ResponseBody
    public ResponseEntity<?> deleteMedia(@PathVariable UUID mediaId) {
        try {
            AppMedia media = appMediaRepository.findById(mediaId)
                    .orElseThrow(() -> new RuntimeException("Media not found"));
            mediaService.delete(media.getUrl());
            appMediaRepository.delete(media);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Generic file upload (not tied to app) — useful for standalone usage.
     * Returns JSON: { "url": "..." }
     */
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "misc") String type) {
        try {
            String url = mediaService.upload(file, type);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}