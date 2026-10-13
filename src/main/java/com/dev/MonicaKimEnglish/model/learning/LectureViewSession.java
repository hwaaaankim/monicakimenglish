package com.dev.MonicaKimEnglish.model.learning;

import java.time.LocalDateTime;

import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.model.lecture.Lecture;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lecture_view_session", indexes = {
        @Index(name = "idx_view_session_lecture", columnList = "lecture_id,started_at"),
        @Index(name = "idx_view_session_client", columnList = "client_id,started_at")
})
public class LectureViewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_key", nullable = false, unique = true, length = 50)
    private String sessionKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "watched_seconds", nullable = false)
    private long watchedSeconds;

    @Column(name = "last_position_seconds", nullable = false)
    private long lastPositionSeconds;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (lastHeartbeatAt == null) lastHeartbeatAt = startedAt;
    }
}
