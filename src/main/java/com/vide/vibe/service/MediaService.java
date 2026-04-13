package com.vide.vibe.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class MediaService {

    private final Cloudinary cloudinary;

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/svg+xml"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    public MediaService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}")    String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {

        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true
        ));
    }

    /**
     * Upload a file to Cloudinary and return its public URL.
     * The subdirectory is used as the Cloudinary folder (e.g. "icons", "screenshots").
     */
    public String upload(MultipartFile file, String subdirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds 5MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder",        "vibe/" + subdirectory,
                        "resource_type", "image"
                )
        );

        return (String) result.get("secure_url");
    }

    /**
     * Delete a file from Cloudinary by its URL.
     * Extracts the public_id from the URL.
     */
    public void delete(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) return;
        try {
            String publicId = extractPublicId(publicUrl);
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            System.err.println("Failed to delete from Cloudinary: " + publicUrl + " — " + e.getMessage());
        }
    }

    /**
     * Extracts the Cloudinary public_id from a secure URL.
     * e.g. https://res.cloudinary.com/demo/image/upload/v123/vibe/icons/abc.png
     *   -> vibe/icons/abc
     */
    private String extractPublicId(String url) {
        // Find "/upload/" and take everything after the version segment
        int uploadIdx = url.indexOf("/upload/");
        if (uploadIdx == -1) return url;
        String afterUpload = url.substring(uploadIdx + 8); // skip "/upload/"

        // Skip version segment if present (v1234567890/)
        if (afterUpload.startsWith("v") && afterUpload.contains("/")) {
            int slashIdx = afterUpload.indexOf("/");
            afterUpload = afterUpload.substring(slashIdx + 1);
        }

        // Remove file extension
        int dotIdx = afterUpload.lastIndexOf(".");
        if (dotIdx != -1) {
            afterUpload = afterUpload.substring(0, dotIdx);
        }

        return afterUpload;
    }
}