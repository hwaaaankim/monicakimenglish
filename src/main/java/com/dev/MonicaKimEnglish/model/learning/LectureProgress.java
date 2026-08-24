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
@Table(name = "lecture_progress", uniqueConstraints = @UniqueConstraint(name = "uk_progress_client_lecture", columnNames = {"client_id", "lecture_id"}), indexes = @Index(name = "idx_progress_lecture", columnList = "lecture_id"))
public class LectureProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @Column(name = "watched_seconds", nullable = false)
    private long watchedSeconds;

    @Column(name = "last_position_seconds", nullable = false)
    private long lastPositionSeconds;

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;
}
