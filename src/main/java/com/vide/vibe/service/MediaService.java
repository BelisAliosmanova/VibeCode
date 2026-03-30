package com.vide.vibe.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class MediaService {

    @Value("${app.upload.dir:static/images}")
    private String uploadDir;

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/svg+xml"
    );

    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * Upload a file and return its public URL path.
     * Subdirectory is used to organise files (e.g., "icons", "screenshots").
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

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);

        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file extension: " + extension);
        }

        // Generate unique filename
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        // Resolve target directory
        Path targetDir = Paths.get(uploadDir, subdirectory).toAbsolutePath();
        Files.createDirectories(targetDir);

        Path targetPath = targetDir.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Return public URL path
        return "/images/" + subdirectory + "/" + uniqueFilename;
    }

    /**
     * Delete a file by its public URL path.
     */
    public void delete(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) return;

        try {
            // Convert public URL to file path
            String relativePath = publicUrl.replace("/images/", "");
            Path filePath = Paths.get(uploadDir, relativePath).toAbsolutePath();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log but don't throw — file deletion is best-effort
            System.err.println("Failed to delete file: " + publicUrl + " — " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}