package com.dev.MonicaKimEnglish.repository.learning;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dev.MonicaKimEnglish.model.learning.LectureProgress;

public interface LectureProgressRepository extends JpaRepository<LectureProgress, Long> {
    Optional<LectureProgress> findByClientIdAndLectureId(Long clientId, Long lectureId);
}
