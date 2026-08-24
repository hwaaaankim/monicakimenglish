package com.dev.MonicaKimEnglish.service.learning;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.MonicaKimEnglish.dto.learning.HeartbeatRequest;
import com.dev.MonicaKimEnglish.dto.learning.HeartbeatResponse;
import com.dev.MonicaKimEnglish.model.learning.*;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.repository.learning.*;
import com.dev.MonicaKimEnglish.repository.lecture.LectureRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProgressService {
    private final LectureAccessService access;
    private final ClientRepository clients;
    private final LectureRepository lectures;
    private final LectureProgressRepository progresses;
    private final LectureViewSessionRepository sessions;

    @Transactional
    public PlayerSessionStart startSession(Long clientId, Long lectureId, String userAgent) {
        access.requireAccess(clientId, lectureId);
        var client = clients.findById(clientId).orElseThrow();
        var lecture = lectures.findById(lectureId).orElseThrow();
        LectureViewSession session = new LectureViewSession();
        session.setSessionKey(UUID.randomUUID().toString());
        session.setClient(client);
        session.setLecture(lecture);
        session.setStartedAt(LocalDateTime.now());
        session.setLastHeartbeatAt(LocalDateTime.now());
        session.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(500, userAgent.length())));
        sessions.save(session);

        LectureProgress p = progresses.findByClientIdAndLectureId(clientId, lectureId).orElseGet(() -> {
            LectureProgress np = new LectureProgress();
            np.setClient(client);
            np.setLecture(lecture);
            np.setDurationSeconds(lecture.getDurationSeconds());
            return np;
        });
        p.setViewCount(p.getViewCount() + 1);
        p.setLastWatchedAt(LocalDateTime.now());
        progresses.save(p);

        return new PlayerSessionStart(
                session.getSessionKey(),
                p.getLastPositionSeconds(),
                p.getProgressPercent(),
                p.isCompleted());
    }

    @Transactional
    public HeartbeatResponse heartbeat(Long clientId, Long lectureId, HeartbeatRequest req) {
        access.requireAccess(clientId, lectureId);
        if (req == null || req.sessionKey() == null) throw new IllegalArgumentException("재생 세션이 없습니다.");
        LectureViewSession s = sessions.findBySessionKeyAndClientIdAndLectureId(req.sessionKey(), clientId, lectureId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 재생 세션입니다."));
        LocalDateTime now = LocalDateTime.now();
        double wall = Math.max(0, Duration.between(s.getLastHeartbeatAt(), now).toMillis() / 1000.0);
        long acceptedDelta = Math.round(Math.max(0, Math.min(req.playedDelta(), Math.min(20.0, wall + 2.0))));
        long position = Math.max(0, Math.round(req.currentTime()));
        long duration = Math.max(1, Math.round(req.duration() > 0 ? req.duration() : s.getLecture().getDurationSeconds()));

        s.setWatchedSeconds(s.getWatchedSeconds() + acceptedDelta);
        s.setLastPositionSeconds(position);
        s.setLastHeartbeatAt(now);
        if (req.ended()) s.setEndedAt(now);

        LectureProgress p = progresses.findByClientIdAndLectureId(clientId, lectureId).orElseThrow();
        long watched = Math.min(duration, p.getWatchedSeconds() + acceptedDelta);
        p.setWatchedSeconds(watched);
        p.setLastPositionSeconds(Math.min(duration, position));
        p.setDurationSeconds(duration);
        boolean completed = req.ended() && position >= Math.max(1, duration - 5) && watched >= Math.floor(duration * 0.90);
        if (p.isCompleted()) completed = true;
        p.setCompleted(completed);
        int percent = completed ? 100 : (int) Math.min(99, Math.floor((watched * 100.0) / duration));
        p.setProgressPercent(percent);
        p.setLastWatchedAt(now);
        progresses.save(p);
        return new HeartbeatResponse(percent, completed, watched, p.getLastPositionSeconds());
    }
    public record PlayerSessionStart(
            String sessionKey,
            long lastPositionSeconds,
            int progressPercent,
            boolean completed) {
    }

}
