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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "payment_key", length = 120)
    private String paymentKey;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @PrePersist
    void prePersist() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
    }
}
