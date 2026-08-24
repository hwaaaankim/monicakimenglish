package com.dev.MonicaKimEnglish.service.learning;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.model.enums.*;
import com.dev.MonicaKimEnglish.model.learning.Enrollment;
import com.dev.MonicaKimEnglish.model.lecture.Lecture;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.repository.learning.EnrollmentRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LectureAccessService {
    private final EnrollmentRepository enrollments;
    private final ClientRepository clients;
    private final LectureRepository lectures;

    public boolean canWatch(Long clientId, Long lectureId) {
        return !enrollments.findUsable(clientId, lectureId, LocalDateTime.now()).isEmpty();
    }

    public Enrollment requireAccess(Long clientId, Long lectureId) {
        List<Enrollment> usable = enrollments.findUsable(clientId, lectureId, LocalDateTime.now());
        if (usable.isEmpty()) throw new AccessDeniedException("구매 또는 수강기간을 확인해주세요.");
        Lecture lecture = usable.get(0).getLecture();
        if (lecture.getStatus() == LectureStatus.DRAFT) throw new AccessDeniedException("현재 시청할 수 없는 강의입니다.");
        return usable.get(0);
    }

    @Transactional
    public Enrollment enrollFree(Long clientId, Long lectureId) {
        Lecture l = lectures.findById(lectureId).orElseThrow();
        if (l.getStatus() != LectureStatus.PUBLISHED || l.getPricingType() != PricingType.FREE) throw new IllegalArgumentException("무료 수강 가능한 영상이 아닙니다.");
        List<Enrollment> current = enrollments.findUsable(clientId, lectureId, LocalDateTime.now());
        if (!current.isEmpty()) return current.get(0);
        Client c = clients.findById(clientId).orElseThrow();
        Enrollment e = new Enrollment();
        e.setClient(c); e.setLecture(l); e.setStatus(EnrollmentStatus.ACTIVE); e.setStartedAt(LocalDateTime.now());
        if (l.getViewingDays() != null) e.setExpiresAt(LocalDateTime.now().plusDays(l.getViewingDays()));
        return enrollments.save(e);
    }

    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) { super(message); }
    }
}
