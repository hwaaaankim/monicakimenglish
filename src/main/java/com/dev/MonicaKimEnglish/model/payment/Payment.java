package com.dev.MonicaKimEnglish.model.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.model.enums.PaymentStatus;
import com.dev.MonicaKimEnglish.model.lecture.Lecture;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_client", columnList = "client_id,approved_at"),
        @Index(name = "idx_payment_lecture", columnList = "lecture_id,status"),
        @Index(name = "idx_payment_approved", columnList = "approved_at")
})
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 80)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @Column(name = "lecture_title_snapshot", nullable = false, length = 200)
    private String lectureTitleSnapshot;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Column(name = "refunded_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "provider_checked_at")
    private LocalDateTime providerCheckedAt;

    @Transient
    public BigDecimal getNetAmount() { return amount.subtract(refundedAmount == null ? BigDecimal.ZERO : refundedAmount); }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

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

    @Column(name = "terms_agreed_at")
    private LocalDateTime termsAgreedAt;

    @Version
    private Long version;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @PrePersist
    void prePersist() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
    }
}
