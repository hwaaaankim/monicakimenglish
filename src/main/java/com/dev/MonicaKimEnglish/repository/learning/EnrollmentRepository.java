package com.dev.MonicaKimEnglish.repository.learning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dev.MonicaKimEnglish.model.learning.Enrollment;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    @Query("select e from Enrollment e join fetch e.lecture left join fetch e.payment where e.client.id = :clientId order by e.createdAt desc")
    List<Enrollment> findAllForClient(@Param("clientId") Long clientId);

    @Query("select e from Enrollment e join fetch e.lecture left join fetch e.payment where e.client.id = :clientId and e.lecture.id = :lectureId and e.status = com.dev.MonicaKimEnglish.model.enums.EnrollmentStatus.ACTIVE and e.startedAt <= :now and (e.expiresAt is null or e.expiresAt >= :now) order by e.createdAt desc")
    List<Enrollment> findUsable(@Param("clientId") Long clientId, @Param("lectureId") Long lectureId, @Param("now") LocalDateTime now);

    @Query("select e from Enrollment e join fetch e.lecture left join fetch e.payment where e.client.id = :clientId and e.lecture.id = :lectureId and e.status = com.dev.MonicaKimEnglish.model.enums.EnrollmentStatus.ACTIVE and (e.expiresAt is null or e.expiresAt >= :now) order by e.createdAt desc")
    List<Enrollment> findActiveIncludingUpcoming(@Param("clientId") Long clientId, @Param("lectureId") Long lectureId, @Param("now") LocalDateTime now);

    @Query("select e from Enrollment e join fetch e.lecture left join fetch e.payment where e.client.id = :clientId and e.lecture.id = :lectureId order by e.createdAt desc")
    List<Enrollment> findHistory(@Param("clientId") Long clientId, @Param("lectureId") Long lectureId);

    Optional<Enrollment> findFirstByPaymentId(Long paymentId);
}
