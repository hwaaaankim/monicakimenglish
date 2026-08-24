package com.dev.MonicaKimEnglish.model.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.dev.MonicaKimEnglish.model.enums.ClientStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "client", indexes = {
        @Index(name = "idx_client_name", columnList = "name"),
        @Index(name = "idx_client_phone", columnList = "phone"),
        @Index(name = "idx_client_status", columnList = "status")
})
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 120)
    private String password;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "privacy_agreed", nullable = false)
    private boolean privacyAgreed;

    @Column(name = "privacy_agreed_at")
    private LocalDateTime privacyAgreedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (privacyAgreed && privacyAgreedAt == null) privacyAgreedAt = now;
        if (status == null) status = ClientStatus.ACTIVE;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
