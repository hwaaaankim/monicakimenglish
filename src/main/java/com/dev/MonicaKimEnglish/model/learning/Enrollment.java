package com.dev.MonicaKimEnglish.model.learning;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.model.enums.EnrollmentStatus;
import com.dev.MonicaKimEnglish.model.lecture.Lecture;
import com.dev.MonicaKimEnglish.model.payment.Payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "enrollment", indexes = {
        @Index(name = "idx_enrollment_access", columnList = "client_id,lecture_id,status,expires_at"),
        @Index(name = "idx_enrollment_client", columnList = "client_id,created_at")
})
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "lecture_title_snapshot", length = 200)
    private String lectureTitleSnapshot;

    @Column(name = "amount_snapshot", precision = 14, scale = 0)
    private BigDecimal amountSnapshot;

    @Column(name = "viewing_days_snapshot")
    private Integer viewingDaysSnapshot;

    @Column(name = "lecture_start_snapshot")
    private LocalDateTime lectureStartSnapshot;

    @Column(name = "refund_policy_version", length = 50)
    private String refundPolicyVersion;

    @Lob
    @Column(name = "refund_policy_snapshot", columnDefinition = "TEXT")
    private String refundPolicySnapshot;

    @Column(name = "total_sessions_snapshot", nullable = false)
    private int totalSessionsSnapshot = 1;

    /** A later lecture edit must not change the terms already purchased. */
    public static Enrollment forLecture(Client client, Lecture lecture, Payment payment, LocalDateTime now) {
        Enrollment enrollment = new Enrollment();
        enrollment.setClient(client);
        enrollment.setLecture(lecture);
        enrollment.setPayment(payment);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setLectureTitleSnapshot(payment == null ? lecture.getTitle() : payment.getLectureTitleSnapshot());
        enrollment.setAmountSnapshot(payment == null ? BigDecimal.ZERO : payment.getAmount());
        enrollment.setViewingDaysSnapshot(payment == null ? lecture.getViewingDays() : payment.getViewingDaysSnapshot());
        LocalDateTime scheduled = payment == null ? lecture.getLectureStartsAt() : payment.getLectureStartSnapshot();
        enrollment.setLectureStartSnapshot(scheduled);
        enrollment.setStartedAt(scheduled != null && scheduled.isAfter(now) ? scheduled : now);
        if (enrollment.getViewingDaysSnapshot() != null) {
            enrollment.setExpiresAt(enrollment.getStartedAt().plusDays(enrollment.getViewingDaysSnapshot()));
        }
        enrollment.setRefundPolicyVersion(payment == null ? CoursePurchasePolicy.VERSION : payment.getRefundPolicyVersion());
        enrollment.setRefundPolicySnapshot(payment == null ? CoursePurchasePolicy.TEXT : payment.getRefundPolicySnapshot());
        enrollment.setTotalSessionsSnapshot(payment == null ? 1 : payment.getTotalSessionsSnapshot());
        enrollment.setCreatedAt(now);
        return enrollment;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (startedAt == null) startedAt = now;
        if (createdAt == null) createdAt = now;
        if (status == null) status = EnrollmentStatus.ACTIVE;
    }
}
