package com.dev.MonicaKimEnglish.service.payment;

import org.springframework.stereotype.Service;
import com.dev.MonicaKimEnglish.model.enums.PaymentStatus;
import com.dev.MonicaKimEnglish.repository.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {
    private final PaymentProperties properties;
    private final PaymentRepository payments;
    private final TossPaymentClient toss;
    private final PaymentService paymentService;

    /** A webhook only triggers a query. Its status, amount and order fields are never trusted. */
    public void synchronize(String paymentKey) {
        if (properties.isMock() || paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200) return;
        var stored = payments.findStoredPaymentKey(paymentKey).orElse(null);
        if (stored == null || !properties.provider().equals(stored.getProvider()) || stored.getStatus() == PaymentStatus.REFUNDED) return;
        TossPaymentClient.State authoritative = toss.queryStoredPayment(paymentKey);
        if (authoritative == null) throw new IllegalArgumentException("결제 상태 조회에 실패했습니다.");
        paymentService.reconcileTossState(stored.getClient().getId(), stored.getOrderNo(), authoritative);
    }
}
