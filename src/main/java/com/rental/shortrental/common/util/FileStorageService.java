package com.rental.shortrental.common.util;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.Set;

@Service
public class FileStorageService {

    private final Path root;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".pdf", ".docx"
    );

    public FileStorageService(@Value("${app.storage.dir:./storage}") String dir) {
        this.root = Paths.get(dir).normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create storage dir: " + root, e);
        }
    }

    public String store(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) return null;
        String ext = extractExt(file.getOriginalFilename());
        validateExtension(ext);
        String name = prefix + "_" + UUID.randomUUID() + ext;
        try {
            Files.copy(file.getInputStream(), resolveSafe(name));
            return name;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + name, e);
        }
    }

    public String storeBytes(byte[] data, String prefix, String ext) {
        if (data == null || data.length == 0) return null;
        String normalizedExt = normalizeExt(ext);
        validateExtension(normalizedExt);
        String name = prefix + "_" + UUID.randomUUID() + normalizedExt.toLowerCase();
        try {
            Files.write(resolveSafe(name), data);
            return name;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + name, e);
        }
    }

    public byte[] load(String name) {
        try {
            return Files.readAllBytes(resolveSafe(name));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load file: " + name, e);
        }
    }

    public void delete(String name) {
        if (name == null) return;
        try {
            Files.deleteIfExists(resolveSafe(name));
        } catch (IOException ignored) {
        }
    }

    private static String extractExt(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i).toLowerCase();
    }

    private static String normalizeExt(String ext) {
        if (ext == null || ext.isBlank()) return "";
        String normalized = ext.startsWith(".") ? ext : "." + ext;
        return normalized.toLowerCase();
    }

    private void validateExtension(String ext) {
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported file extension");
        }
    }

    private Path resolveSafe(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Invalid storage file name");
        }
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return target;
    }
}
