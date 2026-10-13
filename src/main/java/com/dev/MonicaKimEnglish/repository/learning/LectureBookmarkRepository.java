package com.dev.MonicaKimEnglish.repository.learning;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dev.MonicaKimEnglish.model.learning.LectureBookmark;

public interface LectureBookmarkRepository extends JpaRepository<LectureBookmark, Long> {
    List<LectureBookmark> findByClientIdAndLectureIdOrderByPositionSecondsAsc(Long clientId, Long lectureId);
    Optional<LectureBookmark> findByIdAndClientIdAndLectureId(Long id, Long clientId, Long lectureId);
}
