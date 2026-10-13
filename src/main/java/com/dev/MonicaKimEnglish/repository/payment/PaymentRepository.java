package com.dev.MonicaKimEnglish.repository.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dev.MonicaKimEnglish.model.enums.PaymentStatus;
import com.dev.MonicaKimEnglish.model.payment.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("select p from Payment p join fetch p.lecture join fetch p.client where p.orderNo = :orderNo and p.client.id = :clientId")
    Optional<Payment> findOwnedOrder(@Param("orderNo") String orderNo, @Param("clientId") Long clientId);

    @Query("select p from Payment p join fetch p.client join fetch p.lecture where p.paymentKey = :paymentKey")
    Optional<Payment> findStoredPaymentKey(@Param("paymentKey") String paymentKey);

    @Query("select p from Payment p join fetch p.client where p.lecture.id = :lectureId and p.status in (com.dev.MonicaKimEnglish.model.enums.PaymentStatus.PAID, com.dev.MonicaKimEnglish.model.enums.PaymentStatus.PARTIALLY_REFUNDED) order by p.approvedAt desc")
    List<Payment> findActiveBuyersForLecture(@Param("lectureId") Long lectureId);

    long countByLectureIdAndStatusIn(Long lectureId, List<PaymentStatus> statuses);

    Optional<Payment> findFirstByClientIdAndLectureIdAndProviderAndStatusOrderByRequestedAtDesc(
            Long clientId, Long lectureId, String provider, PaymentStatus status);

    Optional<Payment> findFirstByClientIdAndLectureIdAndProviderAndStatusAndPaymentKeyIsNotNullOrderByRequestedAtDesc(
            Long clientId, Long lectureId, String provider, PaymentStatus status);

    @Query("select p from Payment p join fetch p.lecture where p.client.id = :clientId order by p.requestedAt desc")
    List<Payment> findForClient(@Param("clientId") Long clientId);
    @Query("select p from Payment p join fetch p.client where p.lecture.id = :lectureId and p.status = :status order by p.approvedAt desc")
    List<Payment> findForLecture(@Param("lectureId") Long lectureId, @Param("status") PaymentStatus status);
    long countByLectureIdAndStatus(Long lectureId, PaymentStatus status);

    @Query("select coalesce(sum(p.amount - coalesce(p.refundedAmount, 0)), 0) from Payment p where p.provider = 'TOSS' and p.status in (com.dev.MonicaKimEnglish.model.enums.PaymentStatus.PAID, com.dev.MonicaKimEnglish.model.enums.PaymentStatus.PARTIALLY_REFUNDED) and p.approvedAt between :from and :to")
    java.math.BigDecimal sumPaidAmount(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
