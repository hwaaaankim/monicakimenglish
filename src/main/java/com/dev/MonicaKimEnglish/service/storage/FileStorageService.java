package com.dev.MonicaKimEnglish.service.storage;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileStorageService {
    private final StorageProperties props;

    public StoredFile store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("빈 파일은 저장할 수 없습니다.");
        String original = safeOriginalName(file.getOriginalFilename());
        String ext = extension(original);
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dir = root().resolve(folder).normalize();
        Path target = dir.resolve(storedName).normalize();
        assertInsideRoot(target);
        try {
            Files.createDirectories(dir);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(relative(target), original, file.getContentType(), Files.size(target));
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장에 실패했습니다: " + original, e);
        }
    }

    public Resource resource(String relativePath) {
        try {
            Path p = resolve(relativePath);
            Resource r = new UrlResource(p.toUri());
            if (!r.exists() || !r.isReadable()) throw new IllegalArgumentException("파일을 찾을 수 없습니다.");
            return r;
        } catch (Exception e) {
            throw new IllegalArgumentException("파일을 찾을 수 없습니다.", e);
        }
    }

    public Path resolve(String relativePath) {
        Path p = root().resolve(relativePath).normalize();
        assertInsideRoot(p);
        return p;
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try { Files.deleteIfExists(resolve(relativePath)); } catch (Exception ignored) { }
    }

    public String relative(Path absolute) {
        return root().relativize(absolute.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private Path root() {
        return Paths.get(props.getRoot()).toAbsolutePath().normalize();
    }

    private void assertInsideRoot(Path p) {
        if (!p.toAbsolutePath().normalize().startsWith(root())) throw new SecurityException("허용되지 않은 파일 경로입니다.");
    }

    private String safeOriginalName(String value) {
        String name = value == null ? "file" : Paths.get(value).getFileName().toString();
        return name.replaceAll("[\\r\\n]", "_");
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || name.length() - dot > 12) return "";
        String ext = name.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }

    public record StoredFile(String relativePath, String originalName, String contentType, long size) {}
}
