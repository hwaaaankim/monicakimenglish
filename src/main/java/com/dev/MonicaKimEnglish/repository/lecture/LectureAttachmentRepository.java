package com.dev.MonicaKimEnglish.repository.lecture;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dev.MonicaKimEnglish.model.lecture.LectureAttachment;

public interface LectureAttachmentRepository extends JpaRepository<LectureAttachment, Long> {
    List<LectureAttachment> findByLectureIdOrderByIdAsc(Long lectureId);
    Optional<LectureAttachment> findByIdAndLectureId(Long id, Long lectureId);
}
