package com.dev.MonicaKimEnglish.model.popup;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "home_popup", indexes = {
        @Index(name = "idx_home_popup_period", columnList = "start_date,end_date"),
        @Index(name = "idx_home_popup_created", columnList = "created_at")
})
public class HomePopup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_path", nullable = false, length = 500)
    private String imagePath;

    @Column(name = "image_original_name", length = 255)
    private String imageOriginalName;

    @Column(name = "image_content_type", length = 100)
    private String imageContentType;

    @Column(name = "target_url", length = 2000)
    private String targetUrl;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActiveOn(LocalDate date) {
        if (date == null) return false;
        if (startDate != null && date.isBefore(startDate)) return false;
        return endDate == null || !date.isAfter(endDate);
    }

    public boolean isScheduledOn(LocalDate date) {
        return date != null && startDate != null && date.isBefore(startDate);
    }

    public boolean isExpiredOn(LocalDate date) {
        return date != null && endDate != null && date.isAfter(endDate);
    }

    public boolean isUnlimited() {
        return startDate == null && endDate == null;
    }
}
