package com.dev.MonicaKimEnglish.model.lecture;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lecture_tag", uniqueConstraints = @UniqueConstraint(name = "uk_lecture_tag", columnNames = {"lecture_id", "name"}), indexes = @Index(name = "idx_lecture_tag_name", columnList = "name"))
public class LectureTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @Column(nullable = false, length = 80)
    private String name;
}
