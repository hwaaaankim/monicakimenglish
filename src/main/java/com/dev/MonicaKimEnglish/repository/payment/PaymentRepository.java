package com.dev.MonicaKimEnglish.repository.payment;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dev.MonicaKimEnglish.model.enums.PaymentStatus;
import com.dev.MonicaKimEnglish.model.payment.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("select p from Payment p join fetch p.lecture where p.client.id = :clientId order by p.requestedAt desc")
    List<Payment> findForClient(@Param("clientId") Long clientId);
    @Query("select p from Payment p join fetch p.client where p.lecture.id = :lectureId and p.status = :status order by p.approvedAt desc")
    List<Payment> findForLecture(@Param("lectureId") Long lectureId, @Param("status") PaymentStatus status);
    long countByLectureIdAndStatus(Long lectureId, PaymentStatus status);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = com.dev.MonicaKimEnglish.model.enums.PaymentStatus.PAID and p.approvedAt between :from and :to")
    java.math.BigDecimal sumPaidAmount(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
