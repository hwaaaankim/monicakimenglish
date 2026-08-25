package com.dev.MonicaKimEnglish.service.popup;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.MonicaKimEnglish.model.popup.HomePopup;
import com.dev.MonicaKimEnglish.repository.popup.HomePopupRepository;
import com.dev.MonicaKimEnglish.service.storage.FileStorageService;
import com.dev.MonicaKimEnglish.service.storage.TransactionFileCleanup;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HomePopupService {
    private static final int MIN_WIDTH = 400;
    private static final int MIN_HEIGHT = 600;
    private static final long MAX_IMAGE_BYTES = 15L * 1024L * 1024L;

    private final HomePopupRepository popups;
    private final FileStorageService storage;
    private final TransactionFileCleanup fileCleanup;

    @Transactional(readOnly = true)
    public List<HomePopup> activeNow() {
        return popups.findActive(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public HomePopup get(Long id) {
        return popups.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<HomePopup> listForAdmin(String q, String state) {
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String normalizedState = state == null ? "ALL" : state.trim().toUpperCase(Locale.ROOT);
        LocalDate today = LocalDate.now();

        return popups.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(popup -> matchesKeyword(popup, keyword))
                .filter(popup -> matchesState(popup, normalizedState, today))
                .toList();
    }

    @Transactional
    public HomePopup create(MultipartFile image, String targetUrl, LocalDate startDate, LocalDate endDate) {
        validatePeriod(startDate, endDate, LocalDate.now());
        validateImage(image, true);

        var stored = storage.store(image, "popups");
        fileCleanup.deleteOnRollback(stored.relativePath());

        HomePopup popup = new HomePopup();
        popup.setImagePath(stored.relativePath());
        popup.setImageOriginalName(stored.originalName());
        popup.setImageContentType(stored.contentType());
        popup.setTargetUrl(normalizeTargetUrl(targetUrl));
        popup.setStartDate(startDate);
        popup.setEndDate(endDate);
        return popups.save(popup);
    }

    @Transactional
    public HomePopup update(Long id, MultipartFile image, String targetUrl, LocalDate startDate, LocalDate endDate) {
        HomePopup popup = get(id);
        LocalDate effectiveRegistrationDate = popup.getCreatedAt() == null
                ? LocalDate.now()
                : popup.getCreatedAt().toLocalDate();

        validatePeriod(startDate, endDate, effectiveRegistrationDate);
        popup.setTargetUrl(normalizeTargetUrl(targetUrl));
        popup.setStartDate(startDate);
        popup.setEndDate(endDate);

        if (image != null && !image.isEmpty()) {
            validateImage(image, false);
            applyNewImage(popup, image, popup.getImagePath());
        }
        return popup;
    }

    @Transactional
    public void delete(Long id) {
        HomePopup popup = get(id);
        String oldPath = popup.getImagePath();
        popups.delete(popup);
        fileCleanup.deleteAfterCommit(oldPath);
    }

    private void applyNewImage(HomePopup popup, MultipartFile image, String oldPath) {
        var stored = storage.store(image, "popups");
        fileCleanup.deleteOnRollback(stored.relativePath());

        popup.setImagePath(stored.relativePath());
        popup.setImageOriginalName(stored.originalName());
        popup.setImageContentType(stored.contentType());

        if (oldPath != null && !oldPath.isBlank() && !oldPath.equals(stored.relativePath())) {
            fileCleanup.deleteAfterCommit(oldPath);
        }
    }

    private void validateImage(MultipartFile image, boolean required) {
        if (image == null || image.isEmpty()) {
            if (required) throw new IllegalArgumentException("팝업 이미지를 선택하고 편집해주세요.");
            return;
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("팝업 이미지는 15MB 이하만 등록할 수 있습니다.");
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("팝업 이미지는 이미지 파일만 등록할 수 있습니다.");
        }

        try (var input = image.getInputStream()) {
            BufferedImage decoded = ImageIO.read(input);
            if (decoded == null) {
                throw new IllegalArgumentException("이미지 파일을 확인할 수 없습니다.");
            }
            int width = decoded.getWidth();
            int height = decoded.getHeight();
            if (width < MIN_WIDTH || height < MIN_HEIGHT) {
                throw new IllegalArgumentException("팝업 이미지는 최소 400×600px 이상이어야 합니다.");
            }
            if ((long) width * 3L != (long) height * 2L) {
                throw new IllegalArgumentException("팝업 이미지는 2:3 비율이어야 합니다. 이미지 편집에서 저장 후 등록해주세요.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("팝업 이미지를 읽지 못했습니다.", e);
        }
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate, LocalDate registrationDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("게시 시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (startDate == null && endDate != null && registrationDate != null && endDate.isBefore(registrationDate)) {
            throw new IllegalArgumentException("종료일만 지정하는 경우 종료일은 등록일보다 빠를 수 없습니다.");
        }
    }

    private String normalizeTargetUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.trim();
        if (url.length() > 2000) {
            throw new IllegalArgumentException("연결 링크가 너무 깁니다.");
        }

        if (url.startsWith("/") && !url.startsWith("//")) {
            return url;
        }

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()) {
                return url;
            }
        } catch (IllegalArgumentException ignored) {
            // 아래 공통 오류 메시지로 처리합니다.
        }
        throw new IllegalArgumentException("연결 링크는 /로 시작하는 내부 주소 또는 http(s) 주소만 사용할 수 있습니다.");
    }

    private boolean matchesKeyword(HomePopup popup, String keyword) {
        if (keyword.isBlank()) return true;
        if (popup.getId() != null && String.valueOf(popup.getId()).contains(keyword)) return true;
        return popup.getTargetUrl() != null
                && popup.getTargetUrl().toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean matchesState(HomePopup popup, String state, LocalDate today) {
        return switch (state) {
            case "ACTIVE" -> popup.isActiveOn(today);
            case "SCHEDULED" -> popup.isScheduledOn(today);
            case "EXPIRED" -> popup.isExpiredOn(today);
            case "UNLIMITED" -> popup.isUnlimited();
            default -> true;
        };
    }
}
