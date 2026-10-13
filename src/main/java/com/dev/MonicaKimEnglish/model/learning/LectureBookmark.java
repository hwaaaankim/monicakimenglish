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
@Table(name = "lecture_bookmark",
        uniqueConstraints = @UniqueConstraint(name = "uk_bookmark_client_lecture_position", columnNames = {"client_id", "lecture_id", "position_seconds"}),
        indexes = @Index(name = "idx_bookmark_client_lecture", columnList = "client_id,lecture_id"))
public class LectureBookmark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;
    @Column(name = "position_seconds", nullable = false)
    private long positionSeconds;
    @Column(nullable = false, length = 60)
    private String label;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
