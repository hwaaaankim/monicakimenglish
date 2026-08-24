package com.dev.MonicaKimEnglish.dto.learning;

public record HeartbeatResponse(int progressPercent, boolean completed, long watchedSeconds, long lastPositionSeconds) {
}
