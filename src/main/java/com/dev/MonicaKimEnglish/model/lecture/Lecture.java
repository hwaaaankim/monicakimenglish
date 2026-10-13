package com.dev.MonicaKimEnglish.model.lecture;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "instructor_name", length = 100)
    private String instructorName;

    @Lob
    @Column(name = "instructor_biography", columnDefinition = "TEXT")
    private String instructorBiography;

    // open-in-view=false에서도 상품/관리 화면이 안전하게 이력 목록을 렌더링합니다.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lecture_instructor_career", joinColumns = @JoinColumn(name = "lecture_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "career", nullable = false, length = 500)
    private List<String> instructorCareers = new ArrayList<>();

    @Column(name = "enrollment_starts_at")
    private LocalDateTime enrollmentStartsAt;

    @Column(name = "enrollment_ends_at")
    private LocalDateTime enrollmentEndsAt;

    @Column(name = "lecture_starts_at")
    private LocalDateTime lectureStartsAt;

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

    public boolean isEnrollmentOpen() {
        return isEnrollmentOpenAt(LocalDateTime.now());
    }

    public boolean isEnrollmentOpenAt(LocalDateTime now) {
        return status == LectureStatus.PUBLISHED
                && (enrollmentStartsAt == null || !now.isBefore(enrollmentStartsAt))
                && (enrollmentEndsAt == null || !now.isAfter(enrollmentEndsAt));
    }

    public String getEnrollmentStatusLabel() {
        LocalDateTime now = LocalDateTime.now();
        if (status != LectureStatus.PUBLISHED) return "모집하지 않음";
        if (enrollmentStartsAt != null && now.isBefore(enrollmentStartsAt)) return "모집 예정";
        if (enrollmentEndsAt != null && now.isAfter(enrollmentEndsAt)) return "모집 마감";
        return enrollmentStartsAt == null && enrollmentEndsAt == null ? "상시 모집" : "모집 중";
    }

    public String getViewingPeriodLabel() {
        return viewingDays == null ? "수강기간 무제한" : "수강 시작일부터 " + viewingDays + "일";
    }

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
