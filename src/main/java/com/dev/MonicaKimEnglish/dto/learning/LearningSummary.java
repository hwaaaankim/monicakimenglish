package com.dev.MonicaKimEnglish.dto.learning;

import java.time.LocalDateTime;
import java.util.List;

public record LearningSummary(long lastPositionSeconds, int progressPercent, boolean completed,
        long watchedSeconds, long durationSeconds, LocalDateTime lastWatchedAt,
        String lastThumbnailUrl, List<BookmarkDto> bookmarks) { }
