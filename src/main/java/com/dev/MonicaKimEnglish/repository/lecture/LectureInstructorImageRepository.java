package com.dev.MonicaKimEnglish.repository.lecture;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dev.MonicaKimEnglish.model.lecture.LectureInstructorImage;

public interface LectureInstructorImageRepository extends JpaRepository<LectureInstructorImage, Long> {
    List<LectureInstructorImage> findByLectureIdOrderByDisplayOrderAsc(Long lectureId);
    Optional<LectureInstructorImage> findByIdAndLectureId(Long id, Long lectureId);
}
