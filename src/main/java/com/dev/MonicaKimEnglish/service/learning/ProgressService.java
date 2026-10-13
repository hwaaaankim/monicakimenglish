package com.dev.MonicaKimEnglish.service.learning;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dev.MonicaKimEnglish.dto.learning.HeartbeatRequest;
import com.dev.MonicaKimEnglish.dto.learning.HeartbeatResponse;
import com.dev.MonicaKimEnglish.dto.learning.LearningSummary;
import com.dev.MonicaKimEnglish.dto.learning.PlaybackSegment;
import com.dev.MonicaKimEnglish.model.learning.LectureProgress;
import com.dev.MonicaKimEnglish.model.learning.LectureViewSession;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.repository.learning.LectureProgressRepository;
import com.dev.MonicaKimEnglish.repository.learning.LectureViewSessionRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureRepository;
import com.dev.MonicaKimEnglish.service.storage.FfmpegService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProgressService {
    private final LectureAccessService access;
    private final ClientRepository clients;
    private final LectureRepository lectures;
    private final LectureProgressRepository progresses;
    private final LectureViewSessionRepository sessions;
    private final BookmarkService bookmarks;
    private final FfmpegService ffmpeg;

    @Transactional
    public PlayerSessionStart startSession(Long clientId, Long lectureId, String userAgent) {
        access.requireAccess(clientId, lectureId);
        // 처음 여는 두 탭이 동일 progress 행을 중복 생성하지 않도록 회원을 먼저 잠급니다.
        var client = clients.findByIdForUpdate(clientId).orElseThrow();
        var lecture = lectures.findById(lectureId).orElseThrow();
        LectureProgress p = progresses.findForUpdate(clientId, lectureId).orElseGet(() -> {
            LectureProgress fresh = new LectureProgress();
            fresh.setClient(client);
            fresh.setLecture(lecture);
            fresh.setDurationSeconds(lecture.getDurationSeconds());
            fresh.setWatchedRanges("");
            return fresh;
        });
        if (p.getDurationSeconds() <= 0) p.setDurationSeconds(serverDuration(lecture.getDurationSeconds(), lecture.getVideoPath()));
        p.setViewCount(p.getViewCount() + 1);
        progresses.saveAndFlush(p);

        LectureViewSession session = new LectureViewSession();
        session.setSessionKey(UUID.randomUUID().toString());
        session.setClient(client);
        session.setLecture(lecture);
        session.setStartedAt(LocalDateTime.now());
        session.setLastHeartbeatAt(session.getStartedAt());
        session.setLastPositionSeconds(p.getLastPositionSeconds());
        session.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(500, userAgent.length())));
        sessions.save(session);
        return new PlayerSessionStart(session.getSessionKey(), p.getLastPositionSeconds(), p.getProgressPercent(), p.isCompleted());
    }

    @Transactional(readOnly = true)
    public LearningSummary learningSummary(Long clientId, Long lectureId) {
        var enrollment = access.requireOwnedEnrollment(clientId, lectureId);
        var progress = progresses.findByClientIdAndLectureId(clientId, lectureId).orElse(null);
        long position = progress == null ? 0 : progress.getLastPositionSeconds();
        return new LearningSummary(position, progress == null ? 0 : progress.getProgressPercent(),
                progress != null && progress.isCompleted(), progress == null ? 0 : progress.getWatchedSeconds(),
                progress == null ? enrollment.getLecture().getDurationSeconds() : progress.getDurationSeconds(),
                progress == null ? null : progress.getLastWatchedAt(),
                BookmarkService.frameUrl(lectureId, position), bookmarks.list(clientId, lectureId));
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long clientId, Long lectureId, HeartbeatRequest req) {
        access.requireAccess(clientId, lectureId);
        validateRequest(req);
        LectureViewSession session = sessions.findForUpdate(req.sessionKey(), clientId, lectureId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 재생 세션입니다."));
        LectureProgress p = progresses.findForUpdate(clientId, lectureId)
                .orElseThrow(() -> new IllegalArgumentException("학습 기록을 찾을 수 없습니다."));
        // 네트워크 실패로 동일 요청을 재전송해도 시청 구간과 위치를 다시 덮어쓰지 않습니다.
        if (req.sequence() != null && req.sequence() <= session.getLastSequence()) return response(p);

        long duration = serverDuration(session.getLecture().getDurationSeconds(), session.getLecture().getVideoPath());
        if (duration <= 0) throw new IllegalArgumentException("영상 길이를 확인할 수 없습니다. 관리자에게 영상 파일 확인을 요청해주세요.");
        LocalDateTime now = LocalDateTime.now();
        double wall = Math.max(0, Duration.between(session.getLastHeartbeatAt(), now).toMillis() / 1000.0);
        double rate = req.playbackRate() == null ? 1.0 : req.playbackRate();
        // 서버 경과 시간과 지원 배속으로 재생 가능한 분량만 인정합니다.
        // 매 요청마다 시간 여유를 더하면 빠른 연속 요청으로 그 여유가 누적될 수 있습니다.
        // 실제 서버 경과 시간 및 클라이언트가 보고한 재생 시간 중 작은 값만 인정합니다.
        double budget = Math.min(20.0, Math.min(wall, req.playedDelta())) * rate;
        long position = Math.min(duration, Math.max(0, Math.round(req.currentTime())));
        if (p.getWatchedRanges() == null) p.setLegacyWatchedSeconds(Math.max(p.getLegacyWatchedSeconds(), p.getWatchedSeconds()));
        WatchIntervals ranges = WatchIntervals.parse(p.getWatchedRanges());
        List<PlaybackSegment> segments = req.playbackSegments();
        if (segments == null) {
            // 구버전 플레이어는 직전 위치와 실제 재생량이 일치할 때만 연속 구간을 인정합니다.
            double distance = req.currentTime() - session.getLastPositionSeconds();
            double delta = Math.max(0, Math.min(req.playedDelta(), budget));
            segments = distance > 0 && Math.abs(distance - req.playedDelta()) <= 1.5
                    ? List.of(new PlaybackSegment(Math.max(0, req.currentTime() - delta), req.currentTime())) : List.of();
        }
        double accepted = 0;
        for (PlaybackSegment segment : segments) {
            if (segment.endSeconds() > duration + 1) throw new IllegalArgumentException("재생 구간이 영상 길이를 벗어났습니다.");
            double start = Math.min(duration, segment.startSeconds());
            double end = Math.min(duration, segment.endSeconds());
            double remaining = Math.max(0, budget - accepted);
            if (remaining <= 0) break;
            end = Math.min(end, start + remaining);
            if (end > start) {
                ranges.add(start, end, duration);
                accepted += end - start;
            }
        }
        session.setWatchedSeconds(session.getWatchedSeconds() + (long) Math.floor(accepted));
        session.setLastPositionSeconds(position);
        session.setLastHeartbeatAt(now);
        if (req.sequence() != null) session.setLastSequence(req.sequence());
        session.setEndedAt(req.ended() ? now : null);

        // 기존 버전은 어떤 구간을 보았는지 알 수 없으므로 기존 분량을 유지하는 보수적 기준을 씁니다.
        // 신규 기록은 실제 구간의 합집합으로 계산하여 같은 장면을 반복해도 중복 차감하지 않습니다.
        long watched = Math.min(duration, Math.max(p.getLegacyWatchedSeconds(), ranges.watchedMilliseconds() / 1000));
        p.setWatchedRanges(ranges.encode());
        p.setWatchedSeconds(watched);
        p.setLastPositionSeconds(position);
        p.setDurationSeconds(duration);
        boolean completed = p.isCompleted() || (req.ended() && position >= Math.max(1, duration - 5) && watched >= Math.floor(duration * 0.90));
        p.setCompleted(completed);
        p.setProgressPercent(completed ? 100 : (int) Math.min(99, Math.floor(watched * 100.0 / duration)));
        if (accepted > 0) p.setLastWatchedAt(now);
        progresses.save(p);
        return response(p);
    }

    private long serverDuration(long stored, String videoPath) {
        return stored > 0 ? stored : ffmpeg.readDurationSeconds(videoPath);
    }

    private static void validateRequest(HeartbeatRequest req) {
        if (req == null || req.sessionKey() == null || req.sessionKey().isBlank() || req.sessionKey().length() > 50) {
            throw new IllegalArgumentException("재생 세션이 없습니다.");
        }
        if (!Double.isFinite(req.currentTime()) || req.currentTime() < 0 || !Double.isFinite(req.playedDelta()) || req.playedDelta() < 0
                || !Double.isFinite(req.duration()) || req.duration() < 0) {
            throw new IllegalArgumentException("재생 시간 형식이 올바르지 않습니다.");
        }
        if (req.sequence() != null && req.sequence() <= 0) throw new IllegalArgumentException("재생 요청 순서가 올바르지 않습니다.");
        if (req.playbackRate() != null && (!Double.isFinite(req.playbackRate()) || req.playbackRate() < 0.5 || req.playbackRate() > 2)) {
            throw new IllegalArgumentException("재생 배속은 0.5배부터 2배까지만 지원합니다.");
        }
        if (req.playbackSegments() != null) {
            if (req.playbackSegments().size() > 64) throw new IllegalArgumentException("재생 구간 요청이 너무 많습니다.");
            for (PlaybackSegment segment : req.playbackSegments()) {
                if (segment == null || !Double.isFinite(segment.startSeconds()) || !Double.isFinite(segment.endSeconds())
                        || segment.startSeconds() < 0 || segment.endSeconds() <= segment.startSeconds()) {
                    throw new IllegalArgumentException("재생 구간 형식이 올바르지 않습니다.");
                }
            }
        }
    }

    private static HeartbeatResponse response(LectureProgress p) {
        return new HeartbeatResponse(p.getProgressPercent(), p.isCompleted(), p.getWatchedSeconds(), p.getLastPositionSeconds());
    }

    public record PlayerSessionStart(String sessionKey, long lastPositionSeconds, int progressPercent, boolean completed) { }
}
