package com.dev.MonicaKimEnglish.service.payment;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.model.enums.*;
import com.dev.MonicaKimEnglish.model.learning.Enrollment;
import com.dev.MonicaKimEnglish.model.lecture.Lecture;
import com.dev.MonicaKimEnglish.model.payment.Payment;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.repository.learning.EnrollmentRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureRepository;
import com.dev.MonicaKimEnglish.repository.payment.PaymentRepository;
import com.dev.MonicaKimEnglish.service.learning.WishlistService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository payments;
    private final EnrollmentRepository enrollments;
    private final ClientRepository clients;
    private final LectureRepository lectures;
    private final WishlistService wishlistService;

    @Transactional
    public Payment mockApprove(Long clientId, Long lectureId) {
        if (!enrollments.findUsable(clientId, lectureId, LocalDateTime.now()).isEmpty()) throw new IllegalArgumentException("이미 시청 가능한 강의입니다.");
        Client client = clients.findById(clientId).orElseThrow();
        Lecture lecture = lectures.findById(lectureId).orElseThrow();
        if (lecture.getStatus() != LectureStatus.PUBLISHED || lecture.getPricingType() != PricingType.PAID) throw new IllegalArgumentException("결제 가능한 유료 강의가 아닙니다.");

        LocalDateTime now = LocalDateTime.now();
        Payment p = new Payment();
        p.setOrderNo("MK-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 8));
        p.setClient(client); p.setLecture(lecture); p.setLectureTitleSnapshot(lecture.getTitle()); p.setAmount(lecture.getPrice());
        p.setStatus(PaymentStatus.PAID); p.setProvider("MOCK"); p.setPaymentKey("mock_" + UUID.randomUUID()); p.setRequestedAt(now); p.setApprovedAt(now);
        payments.save(p);

        Enrollment e = new Enrollment(); e.setClient(client); e.setLecture(lecture); e.setPayment(p); e.setStatus(EnrollmentStatus.ACTIVE); e.setStartedAt(now);
        if (lecture.getViewingDays() != null) e.setExpiresAt(now.plusDays(lecture.getViewingDays()));
        enrollments.save(e);
        wishlistService.removeIfExists(clientId, lectureId);
        return p;
    }

    public List<Payment> clientPayments(Long clientId) { return payments.findForClient(clientId); }
}
