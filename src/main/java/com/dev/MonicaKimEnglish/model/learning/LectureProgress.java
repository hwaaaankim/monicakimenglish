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

    /** 실제 재생 구간의 합집합입니다. 밀리초 단위 start:end 목록을 보관합니다. */
    @Lob
    @Column(name = "watched_ranges", columnDefinition = "LONGTEXT")
    private String watchedRanges;

    /** 과거 버전에 구간 정보 없이 저장된 학습량은 보존하되 중복 가산하지 않습니다. */
    @Column(name = "legacy_watched_seconds", nullable = false)
    private long legacyWatchedSeconds;

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
