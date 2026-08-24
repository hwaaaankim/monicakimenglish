package com.dev.MonicaKimEnglish.dto.lecture;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.dev.MonicaKimEnglish.model.enums.PricingType;

public record LectureCardDto(
        Long id,
        String title,
        String description,
        PricingType pricingType,
        BigDecimal price,
        LocalDateTime uploadedAt,
        long durationSeconds,
        long fileSizeBytes,
        List<String> tags,
        Long thumbnailImageId,
        boolean owned,
        boolean wished,
        int progressPercent,
        boolean completed,
        LocalDateTime expiresAt,
        boolean accessAvailable) {
}
