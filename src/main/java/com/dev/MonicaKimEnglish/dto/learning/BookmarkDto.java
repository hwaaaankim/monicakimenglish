package com.dev.MonicaKimEnglish.dto.learning;

import java.time.LocalDateTime;

public record BookmarkDto(Long id, long positionSeconds, String label, String thumbnailUrl,
        LocalDateTime createdAt) { }
