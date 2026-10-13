package com.dev.MonicaKimEnglish.repository.learning;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.dev.MonicaKimEnglish.model.learning.LectureProgress;

public interface LectureProgressRepository extends JpaRepository<LectureProgress, Long> {
    Optional<LectureProgress> findByClientIdAndLectureId(Long clientId, Long lectureId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LectureProgress p where p.client.id = :clientId and p.lecture.id = :lectureId")
    Optional<LectureProgress> findForUpdate(@Param("clientId") Long clientId, @Param("lectureId") Long lectureId);
}
