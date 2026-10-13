package com.dev.MonicaKimEnglish.service.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.model.enums.PaymentStatus;
import com.dev.MonicaKimEnglish.model.enums.EnrollmentStatus;
import com.dev.MonicaKimEnglish.service.learning.EnrollmentFundingPolicy;
import com.dev.MonicaKimEnglish.model.enums.PricingType;
import com.dev.MonicaKimEnglish.model.learning.CoursePurchasePolicy;
import com.dev.MonicaKimEnglish.model.learning.Enrollment;
import com.dev.MonicaKimEnglish.model.lecture.Lecture;
import com.dev.MonicaKimEnglish.model.payment.Payment;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.repository.learning.EnrollmentRepository;
import com.dev.MonicaKimEnglish.repository.payment.PaymentRepository;
import com.dev.MonicaKimEnglish.service.learning.WishlistService;
import com.dev.MonicaKimEnglish.service.lecture.LectureService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final int ORDER_VALID_MINUTES = 30;
    private final PaymentRepository payments;
    private final EnrollmentRepository enrollments;
    private final ClientRepository clients;
    private final LectureService lectures;
    private final WishlistService wishlistService;
    private final PaymentProperties properties;
    private final TossPaymentClient toss;
    private final PlatformTransactionManager transactionManager;
    private final EnrollmentFundingPolicy fundingPolicy;

    public Lecture checkoutLecture(Long lectureId) {
        Lecture lecture = lectures.getPublic(lectureId);
        requirePaid(lecture);
        lectures.requireEnrollmentOpen(lecture);
        return lecture;
    }

    public boolean alreadyEnrolled(Long clientId, Long lectureId) {
        return enrollments.findActiveIncludingUpcoming(clientId, lectureId, LocalDateTime.now()).stream().anyMatch(fundingPolicy::isEligible);
    }

    @Transactional
    public Payment createOrder(Long clientId, Long lectureId, boolean termsAgreed) {
        if (!termsAgreed) throw new IllegalArgumentException("강의 제공 및 환불 규정에 동의해 주세요.");
        if (!properties.isMock()) properties.requireTossConfigured();
        Client client = lockClient(clientId);
        Lecture lecture = checkoutLecture(lectureId);
        if (alreadyEnrolled(clientId, lectureId)) throw new AlreadyEnrolledException(lectureId);
        LocalDateTime now = LocalDateTime.now();
        requireNoOtherAuthenticatedOrder(clientId, lectureId, null);
        Payment previous = payments.findFirstByClientIdAndLectureIdAndProviderAndStatusOrderByRequestedAtDesc(
                clientId, lectureId, properties.provider(), PaymentStatus.PENDING).orElse(null);
        if (previous != null && previous.getPaymentKey() != null) throw new PendingPaymentException(previous.getOrderNo());
        if (previous != null && previous.getRequestedAt().plusMinutes(ORDER_VALID_MINUTES).isAfter(now)
                && previous.getAmount().compareTo(lecture.getPrice()) == 0
                && Objects.equals(previous.getViewingDaysSnapshot(), lecture.getViewingDays())
                && Objects.equals(previous.getLectureStartSnapshot(), lecture.getLectureStartsAt())
                && Objects.equals(previous.getLectureTitleSnapshot(), lecture.getTitle())
                && CoursePurchasePolicy.VERSION.equals(previous.getRefundPolicyVersion())) return previous;
        Payment payment = new Payment();
        payment.setOrderNo("MK-" + UUID.randomUUID());
        payment.setClient(client);
        payment.setLecture(lecture);
        payment.setLectureTitleSnapshot(lecture.getTitle());
        payment.setAmount(lecture.getPrice());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(properties.provider());
        payment.setRequestedAt(now);
        payment.setTermsAgreedAt(now);
        payment.setViewingDaysSnapshot(lecture.getViewingDays());
        payment.setLectureStartSnapshot(lecture.getLectureStartsAt());
        payment.setRefundPolicyVersion(CoursePurchasePolicy.VERSION);
        payment.setRefundPolicySnapshot(CoursePurchasePolicy.TEXT);
        payment.setTotalSessionsSnapshot(1);
        return payments.save(payment);
    }

    @Transactional
    public Payment mockApproveOrder(Long clientId, String orderId) {
        if (!properties.isMock()) throw new IllegalArgumentException("샘플 결제는 현재 사용할 수 없습니다.");
        Client client = lockClient(clientId);
        Payment payment = ownedOrder(clientId, orderId);
        if (!"MOCK".equals(payment.getProvider())) throw new IllegalArgumentException("샘플 주문이 아닙니다.");
        if (payment.getStatus() == PaymentStatus.PAID) return payment;
        requirePending(payment, false);
        List<Enrollment> active = enrollments.findActiveIncludingUpcoming(clientId, payment.getLecture().getId(), LocalDateTime.now()).stream().filter(fundingPolicy::isEligible).toList();
        if (!active.isEmpty()) {
            Payment existing = active.get(0).getPayment();
            if (existing != null && existing.getStatus() == PaymentStatus.PAID && "MOCK".equals(existing.getProvider())) return existing;
            throw new AlreadyEnrolledException(payment.getLecture().getId());
        }
        lectures.requireEnrollmentOpen(payment.getLecture());
        return complete(payment, client, "mock_" + UUID.randomUUID(), LocalDateTime.now());
    }

    public Payment confirmToss(Long clientId, String orderId, String paymentKey, BigDecimal callbackAmount) {
        properties.requireTossConfigured();
        // Persist the authenticated key before contacting the provider. A timeout must not permit a new charge.
        Payment bound = new TransactionTemplate(transactionManager).execute(transaction -> {
            lockClient(clientId);
            Payment payment = ownedOrder(clientId, orderId);
            requireTossMatch(payment, paymentKey, callbackAmount);
            if (isSettled(payment)) return payment;
            requirePending(payment, true);
            if (alreadyEnrolled(clientId, payment.getLecture().getId())) throw new AlreadyEnrolledException(payment.getLecture().getId());
            requireNoOtherAuthenticatedOrder(clientId, payment.getLecture().getId(), payment.getOrderNo());
            if (payment.getPaymentKey() != null && !paymentKey.equals(payment.getPaymentKey()))
                throw new IllegalArgumentException("이미 인증된 주문입니다. 같은 주문의 결제 상태를 먼저 확인해 주세요.");
            payment.setPaymentKey(paymentKey);
            payments.saveAndFlush(payment);
            return payment;
        });
        if (isSettled(bound)) return bound;
        TossPaymentClient.Approval approved;
        try { approved = toss.confirm(paymentKey, bound.getOrderNo(), bound.getAmount()); }
        catch (TossPaymentClient.TerminalPaymentException failure) {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                lockClient(clientId);
                Payment current = ownedOrder(clientId, orderId);
                if (current.getStatus() == PaymentStatus.PENDING && paymentKey.equals(current.getPaymentKey())) {
                    current.setStatus(PaymentStatus.FAILED);
                    current.setProviderCheckedAt(LocalDateTime.now());
                    payments.save(current);
                }
            });
            throw failure;
        }
        if (approved == null || !"DONE".equals(approved.status()) || !bound.getOrderNo().equals(approved.orderId())
                || !paymentKey.equals(approved.paymentKey()) || approved.totalAmount() == null
                || approved.totalAmount().compareTo(bound.getAmount()) != 0 || !"KRW".equals(approved.currency())) {
            throw new IllegalArgumentException("결제 승인 결과가 주문 정보와 일치하지 않습니다. 고객센터에 문의해 주세요.");
        }
        LocalDateTime approvedAt = approved.approvalTime();
        return new TransactionTemplate(transactionManager).execute(transaction -> {
            Client client = lockClient(clientId);
            Payment current = ownedOrder(clientId, orderId);
            requireTossMatch(current, paymentKey, callbackAmount);
            if (isSettled(current)) return current;
            requirePending(current, true);
            if (alreadyEnrolled(clientId, current.getLecture().getId())) throw new AlreadyEnrolledException(current.getLecture().getId());
            current.setProviderCheckedAt(LocalDateTime.now());
            return complete(current, client, approved.paymentKey(), approvedAt);
        });
    }

    private void requireTossMatch(Payment payment, String paymentKey, BigDecimal amount) {
        if (!properties.provider().equals(payment.getProvider())) throw new IllegalArgumentException("현재 결제 환경의 주문이 아닙니다.");
        if (paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200)
            throw new IllegalArgumentException("결제 인증 정보가 올바르지 않습니다.");
        if (amount == null || amount.compareTo(payment.getAmount()) != 0)
            throw new IllegalArgumentException("주문 금액과 결제 금액이 일치하지 않습니다.");
        if (payment.getPaymentKey() != null && !paymentKey.equals(payment.getPaymentKey()))
            throw new IllegalArgumentException("결제 인증 정보가 일치하지 않습니다.");
    }

    private void requireNoOtherAuthenticatedOrder(Long clientId, Long lectureId, String ownOrderId) {
        if (properties.isMock()) return;
        payments.findFirstByClientIdAndLectureIdAndProviderAndStatusAndPaymentKeyIsNotNullOrderByRequestedAtDesc(
                clientId, lectureId, properties.provider(), PaymentStatus.PENDING).ifPresent(other -> {
            if (!other.getOrderNo().equals(ownOrderId)) throw new PendingPaymentException(other.getOrderNo());
        });
    }

    /** The caller supplies a server-to-server query result, never a webhook body. */
    @Transactional
    public void reconcileTossState(Long clientId, String orderId, TossPaymentClient.State state) {
        Client client = lockClient(clientId);
        Payment payment = ownedOrder(clientId, orderId);
        requireTossMatch(payment, state.paymentKey(), state.totalAmount());
        if (!payment.getOrderNo().equals(state.orderId()) || !"KRW".equals(state.currency())
                || state.balanceAmount() == null || state.balanceAmount().signum() < 0
                || state.balanceAmount().compareTo(payment.getAmount()) > 0 || payment.getPaymentKey() == null) {
            throw new IllegalArgumentException("결제 조회 응답이 저장된 주문과 일치하지 않습니다.");
        }
        BigDecimal refunded = state.totalAmount().subtract(state.balanceAmount());
        // Older/out-of-order events cannot undo a refund or restore a revoked enrollment.
        if (payment.getStatus() == PaymentStatus.REFUNDED || refunded.compareTo(payment.getRefundedAmount()) < 0) return;
        payment.setProviderCheckedAt(LocalDateTime.now());
        if ("CANCELED".equals(state.status()) || ("PARTIAL_CANCELED".equals(state.status()) && state.balanceAmount().signum() == 0)) {
            if (state.balanceAmount().signum() != 0) throw new IllegalArgumentException("전액 취소 금액을 확인할 수 없습니다.");
            payment.setRefundedAmount(payment.getAmount());
            payment.setStatus(PaymentStatus.REFUNDED);
            enrollments.findFirstByPaymentId(payment.getId()).ifPresent(enrollment -> {
                enrollment.setStatus(EnrollmentStatus.CANCELED); enrollments.save(enrollment);
            });
        } else if ("PARTIAL_CANCELED".equals(state.status())) {
            if (refunded.signum() <= 0) throw new IllegalArgumentException("부분 취소 금액을 확인할 수 없습니다.");
            boolean needsEnrollment = enrollments.findFirstByPaymentId(payment.getId()).isEmpty();
            LocalDateTime approvedAt = null;
            if (needsEnrollment) {
                if (payment.getStatus() == PaymentStatus.PENDING) requirePending(payment, true);
                if (state.approvedAt() == null) throw new IllegalArgumentException("결제 승인 시각을 확인할 수 없습니다.");
                approvedAt = new TossPaymentClient.Approval(state.paymentKey(), state.orderId(), "DONE",
                        state.totalAmount(), state.currency(), state.approvedAt()).approvalTime();
            }
            payment.setRefundedAmount(refunded);
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            if (needsEnrollment) {
                payment.setApprovedAt(approvedAt);
                enrollments.save(Enrollment.forLecture(client, payment.getLecture(), payment, approvedAt));
            }
            // Partial refunds retain the existing entitlement. No price-policy inference is made here.
        } else if ("DONE".equals(state.status()) && payment.getStatus() == PaymentStatus.PENDING) {
            requirePending(payment, true);
            if (refunded.signum() != 0 || state.approvedAt() == null) throw new IllegalArgumentException("결제 승인 정보를 확인할 수 없습니다.");
            LocalDateTime approvedAt = new TossPaymentClient.Approval(state.paymentKey(), state.orderId(), state.status(),
                    state.totalAmount(), state.currency(), state.approvedAt()).approvalTime();
            complete(payment, client, state.paymentKey(), approvedAt);
        } else if (("EXPIRED".equals(state.status()) || "ABORTED".equals(state.status())) && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
        }
        payments.save(payment);
    }

    private static boolean isSettled(Payment payment) {
        return payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED;
    }

    @Transactional(readOnly = true)
    public Payment ownedOrder(Long clientId, String orderId) {
        if (orderId == null || !orderId.matches("MK-(?:[a-fA-F0-9-]{36}|[0-9]{14}-[a-fA-F0-9]{8})")) throw new IllegalArgumentException("주문번호가 올바르지 않습니다.");
        return payments.findOwnedOrder(orderId, clientId).orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Payment completedOrder(Long clientId, String orderId) {
        Payment payment = ownedOrder(clientId, orderId);
        if (!isSettled(payment)) throw new IllegalArgumentException("결제가 완료된 주문이 아닙니다.");
        if (properties.getMode() == PaymentProperties.Mode.TOSS && !properties.isTestKey() && !"TOSS".equals(payment.getProvider()))
            throw new IllegalArgumentException("이 주문은 샘플 또는 테스트 결제입니다. 실제 수강 신청은 강의 상세에서 진행해 주세요.");
        return payment;
    }

    private Payment complete(Payment payment, Client client, String paymentKey, LocalDateTime approvedAt) {
        payment.setPaymentKey(paymentKey);
        payment.setStatus(PaymentStatus.PAID);
        payment.setApprovedAt(approvedAt);
        payments.save(payment);
        if (enrollments.findFirstByPaymentId(payment.getId()).isEmpty()) {
            enrollments.save(Enrollment.forLecture(client, payment.getLecture(), payment, approvedAt));
        }
        wishlistService.removeIfExists(client.getId(), payment.getLecture().getId());
        return payment;
    }

    private Client lockClient(Long clientId) {
        return clients.findByIdForUpdate(clientId).orElseThrow(() -> new IllegalArgumentException("회원 정보를 확인할 수 없습니다."));
    }

    private void requirePending(Payment payment, boolean allowProviderRecovery) {
        if (payment.getStatus() != PaymentStatus.PENDING) throw new IllegalArgumentException("승인 가능한 주문이 아닙니다.");
        if (!allowProviderRecovery && payment.getRequestedAt().plusMinutes(ORDER_VALID_MINUTES).isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("결제 요청 시간이 만료되었습니다. 강의 상세에서 다시 구매해 주세요.");
        if (payment.getTermsAgreedAt() == null) throw new IllegalArgumentException("구매 약관 동의가 확인되지 않습니다.");
    }

    private void requirePaid(Lecture lecture) {
        if (lecture.getPricingType() != PricingType.PAID || lecture.getPrice() == null || lecture.getPrice().signum() <= 0
                || lecture.getPrice().stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("결제 가능한 유료 강의가 아닙니다.");
    }

    public List<Payment> clientPayments(Long clientId) { return payments.findForClient(clientId); }

    public static class PendingPaymentException extends IllegalArgumentException {
        private final String orderId;
        public PendingPaymentException(String orderId) { super("진행 중인 주문의 결제 상태를 먼저 확인해 주세요. 새 결제는 진행되지 않습니다."); this.orderId = orderId; }
        public String getOrderId() { return orderId; }
    }

    public static class AlreadyEnrolledException extends IllegalArgumentException {
        private final Long lectureId;
        public AlreadyEnrolledException(Long lectureId) { super("이미 신청한 강의입니다."); this.lectureId = lectureId; }
        public Long getLectureId() { return lectureId; }
    }
}
