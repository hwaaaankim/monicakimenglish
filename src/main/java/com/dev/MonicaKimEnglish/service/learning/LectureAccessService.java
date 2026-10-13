package com.dev.MonicaKimEnglish.service.learning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dev.MonicaKimEnglish.model.enums.LectureStatus;
import com.dev.MonicaKimEnglish.model.enums.PricingType;
import com.dev.MonicaKimEnglish.model.learning.Enrollment;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.repository.learning.EnrollmentRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureRepository;
import com.dev.MonicaKimEnglish.service.lecture.LectureService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LectureAccessService {
    private final EnrollmentRepository enrollments;
    private final ClientRepository clients;
    private final LectureRepository lectures;
    private final LectureService lectureService;
    private final EnrollmentFundingPolicy fundingPolicy;

    /** 무료 강의도 명시적으로 신청하기 전에는 시청 권한을 부여하지 않습니다. */
    public boolean canWatch(Long clientId, Long lectureId) {
        if (clientId == null) return false;
        return enrollments.findUsable(clientId, lectureId, LocalDateTime.now()).stream()
                .filter(fundingPolicy::isEligible)
                .anyMatch(e -> e.getLecture().getStatus() != LectureStatus.DRAFT);
    }

    /** 미래 시작일의 수강권도 신청 완료 상태이므로 중복 신청/구매를 막습니다. */
    public Optional<Enrollment> findEnrollment(Long clientId, Long lectureId) {
        if (clientId == null) return Optional.empty();
        return enrollments.findActiveIncludingUpcoming(clientId, lectureId, LocalDateTime.now()).stream().filter(fundingPolicy::isEligible).findFirst();
    }

    public boolean hasEnrollment(Long clientId, Long lectureId) {
        return findEnrollment(clientId, lectureId).isPresent();
    }

    public boolean hasEnrollmentHistory(Long clientId, Long lectureId) {
        return clientId != null && !enrollments.findHistory(clientId, lectureId).isEmpty();
    }

    /** 시청권과 별개로 만료/환불된 본인 강의의 학습 기록은 계속 확인할 수 있습니다. */
    public boolean hasEligibleFunding(Enrollment enrollment) { return fundingPolicy.isEligible(enrollment); }

    public Enrollment requireOwnedEnrollment(Long clientId, Long lectureId) {
        if (clientId == null) throw new AccessDeniedException("로그인이 필요합니다.");
        return enrollments.findHistory(clientId, lectureId).stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("신청하거나 구매한 강의의 학습 기록만 확인할 수 있습니다."));
    }

    public Enrollment requireAccess(Long clientId, Long lectureId) {
        if (clientId == null) throw new AccessDeniedException("로그인이 필요합니다.");
        List<Enrollment> usable = enrollments.findUsable(clientId, lectureId, LocalDateTime.now()).stream().filter(fundingPolicy::isEligible).toList();
        if (usable.isEmpty()) {
            var upcoming = findEnrollment(clientId, lectureId);
            if (upcoming.isPresent() && upcoming.get().getStartedAt().isAfter(LocalDateTime.now())) {
                throw new AccessDeniedException("수강 신청이 완료되었습니다. 안내된 강의 시작일부터 시청할 수 있습니다.");
            }
            throw new AccessDeniedException("강의를 신청하거나 구매한 후 수강기간 내에 시청해주세요.");
        }
        var enrollment = usable.get(0);
        if (enrollment.getLecture().getStatus() == LectureStatus.DRAFT) throw new AccessDeniedException("현재 시청할 수 없는 강의입니다.");
        return enrollment;
    }

    @Transactional
    public Enrollment enrollFree(Long clientId, Long lectureId) {
        var client = clients.findByIdForUpdate(clientId).orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        var lecture = lectures.findById(lectureId).orElseThrow(() -> new IllegalArgumentException("강의를 찾을 수 없습니다."));
        var existing = findEnrollment(clientId, lectureId);
        if (existing.isPresent()) return existing.get();
        if (lecture.getStatus() != LectureStatus.PUBLISHED || lecture.getPricingType() != PricingType.FREE) {
            throw new IllegalArgumentException("무료 수강 가능한 강의가 아닙니다.");
        }
        lectureService.requireEnrollmentOpen(lecture);
        return enrollments.save(Enrollment.forLecture(client, lecture, null, LocalDateTime.now()));
    }

    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) { super(message); }
    }
}
