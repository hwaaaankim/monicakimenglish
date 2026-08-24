package com.dev.MonicaKimEnglish.dto.learning;

public record HeartbeatRequest(String sessionKey, double currentTime, double duration, double playedDelta, boolean ended) {
}
