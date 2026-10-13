package com.dev.MonicaKimEnglish.repository.learning;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import com.dev.MonicaKimEnglish.model.learning.LectureViewSession;

public interface LectureViewSessionRepository extends JpaRepository<LectureViewSession, Long> {
    Optional<LectureViewSession> findBySessionKeyAndClientIdAndLectureId(String sessionKey, Long clientId, Long lectureId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LectureViewSession s where s.sessionKey = :sessionKey and s.client.id = :clientId and s.lecture.id = :lectureId")
    Optional<LectureViewSession> findForUpdate(@Param("sessionKey") String sessionKey, @Param("clientId") Long clientId, @Param("lectureId") Long lectureId);
    @Query("select s from LectureViewSession s join fetch s.client where s.lecture.id = :lectureId order by s.startedAt desc")
    List<LectureViewSession> findRecentForLecture(@Param("lectureId") Long lectureId);
}
