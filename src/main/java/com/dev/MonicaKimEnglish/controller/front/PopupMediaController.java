package com.dev.MonicaKimEnglish.controller.front;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.dev.MonicaKimEnglish.repository.popup.HomePopupRepository;
import com.dev.MonicaKimEnglish.service.storage.FileStorageService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PopupMediaController {
    private static final Logger log = LoggerFactory.getLogger(PopupMediaController.class);

    private final HomePopupRepository popups;
    private final FileStorageService storage;

    @GetMapping("/media/popups/{id}")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        var popup = popups.findById(id).orElse(null);
        if (popup == null || popup.getImagePath() == null || popup.getImagePath().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path path = storage.resolve(popup.getImagePath());
            if (!Files.isRegularFile(path)) {
                log.debug("Popup image file is missing. popupId={}, path={}", id, popup.getImagePath());
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = resolveMediaType(popup.getImageContentType(), path);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .contentType(mediaType)
                    .body(storage.resource(popup.getImagePath()));
        } catch (IllegalArgumentException | SecurityException e) {
            log.debug("Popup image cannot be served. popupId={}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    private MediaType resolveMediaType(String storedType, Path path) {
        try {
            if (storedType != null && storedType.toLowerCase().startsWith("image/")) {
                return MediaType.parseMediaType(storedType);
            }
            String detected = Files.probeContentType(path);
            if (detected != null && detected.toLowerCase().startsWith("image/")) {
                return MediaType.parseMediaType(detected);
            }
        } catch (Exception ignored) {
            // 이미지 응답의 안전한 기본값을 사용합니다.
        }
        return MediaType.IMAGE_JPEG;
    }
}
