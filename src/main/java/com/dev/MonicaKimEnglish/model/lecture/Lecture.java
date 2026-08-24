package com.dev.MonicaKimEnglish.model.lecture;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.dev.MonicaKimEnglish.model.enums.LectureStatus;
import com.dev.MonicaKimEnglish.model.enums.PricingType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lecture", indexes = {
        @Index(name = "idx_lecture_status", columnList = "status"),
        @Index(name = "idx_lecture_pricing", columnList = "pricing_type"),
        @Index(name = "idx_lecture_created", columnList = "created_at")
})
public class Lecture {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type", nullable = false, length = 20)
    private PricingType pricingType = PricingType.FREE;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal price = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LectureStatus status = LectureStatus.DRAFT;

    @Column(name = "viewing_days")
    private Integer viewingDays;

    @Column(name = "video_path", length = 500)
    private String videoPath;

    @Column(name = "video_original_name", length = 255)
    private String videoOriginalName;

    @Column(name = "video_mime_type", length = 120)
    private String videoMimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Version
    private Long version;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (price == null) price = BigDecimal.ZERO;
        if (status == null) status = LectureStatus.DRAFT;
        if (pricingType == null) pricingType = PricingType.FREE;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
