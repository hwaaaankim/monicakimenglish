package com.dev.MonicaKimEnglish.repository.lecture;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dev.MonicaKimEnglish.model.lecture.LectureIntroImage;

public interface LectureIntroImageRepository extends JpaRepository<LectureIntroImage, Long> {
    List<LectureIntroImage> findByLectureIdOrderByDisplayOrderAsc(Long lectureId);
    Optional<LectureIntroImage> findFirstByLectureIdOrderByDisplayOrderAsc(Long lectureId);
    Optional<LectureIntroImage> findByIdAndLectureId(Long id, Long lectureId);
}
