package com.dev.MonicaKimEnglish.dto.learning;

/** playedDelta는 실제 재생한 벽시계 시간(초), playbackSegments는 영상 위치 구간(초)입니다. */
public record HeartbeatRequest(String sessionKey, double currentTime, double duration, double playedDelta,
        boolean ended, java.util.List<PlaybackSegment> playbackSegments, Double playbackRate, Long sequence) {
    public HeartbeatRequest(String sessionKey, double currentTime, double duration, double playedDelta, boolean ended) {
        this(sessionKey, currentTime, duration, playedDelta, ended, null, 1.0, null);
    }
}
