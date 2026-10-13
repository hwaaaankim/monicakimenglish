package com.dev.MonicaKimEnglish.service.learning;

import org.springframework.stereotype.Component;
import com.dev.MonicaKimEnglish.model.enums.PaymentStatus;
import com.dev.MonicaKimEnglish.model.learning.Enrollment;
import com.dev.MonicaKimEnglish.service.payment.PaymentProperties;
import lombok.RequiredArgsConstructor;

/** 테스트 결제 수강권이 운영 결제로 전환한 뒤에도 유료 영상을 열어주지 않도록 분리합니다. */
@Component
@RequiredArgsConstructor
public class EnrollmentFundingPolicy {
    private final PaymentProperties properties;

    public boolean isEligible(Enrollment enrollment) {
        var payment = enrollment.getPayment();
        // 무료 신청 및 관리자가 부여한 결제 없는 수강권은 그대로 보존합니다.
        if (payment == null) return true;
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) return false;
        // TOSS 모드인데 테스트 키가 아닌 경우, 잘못된 설정에서도 테스트 수강권을 허용하지 않습니다.
        boolean live = properties.getMode() == PaymentProperties.Mode.TOSS && !properties.isTestKey();
        if (live) return "TOSS".equals(payment.getProvider());
        return "TOSS".equals(payment.getProvider()) || "TOSS_TEST".equals(payment.getProvider()) || "MOCK".equals(payment.getProvider());
    }
}
