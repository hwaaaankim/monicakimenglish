package com.dev.MonicaKimEnglish.model.lecture;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lecture_instructor_image", indexes = @Index(name = "idx_lecture_instructor_order", columnList = "lecture_id,display_order"))
public class LectureInstructorImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
