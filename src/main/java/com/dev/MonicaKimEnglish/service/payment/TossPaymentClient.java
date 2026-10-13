package com.dev.MonicaKimEnglish.service.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TossPaymentClient {
    private final PaymentProperties properties;
    private final RestClient client;
    private final RestClient queryClient;

    public TossPaymentClient(PaymentProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        this.client = RestClient.builder().baseUrl("https://api.tosspayments.com")
                .requestFactory(factory).build();
        SimpleClientHttpRequestFactory queryFactory = new SimpleClientHttpRequestFactory();
        queryFactory.setConnectTimeout(2_000);
        queryFactory.setReadTimeout(5_000);
        this.queryClient = RestClient.builder().baseUrl("https://api.tosspayments.com")
                .requestFactory(queryFactory).build();
    }

    public Approval confirm(String paymentKey, String orderId, BigDecimal amount) {
        properties.requireTossConfigured();
        try {
            return client.post().uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .header("Idempotency-Key", "confirm-" + orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve().body(Approval.class);
        } catch (RestClientResponseException ex) {
            Approval recovered = recover(paymentKey);
            if (recovered != null && "DONE".equals(recovered.status())) return recovered;
            if (matches(recovered, paymentKey, orderId, amount) && ("EXPIRED".equals(recovered.status()) || "ABORTED".equals(recovered.status())))
                throw new TerminalPaymentException();
            throw new IllegalArgumentException("결제를 승인하지 못했습니다. 결제내역을 확인한 뒤 다시 시도해 주세요.");
        } catch (RestClientException ex) {
            Approval recovered = recover(paymentKey);
            if (recovered != null && "DONE".equals(recovered.status())) return recovered;
            if (matches(recovered, paymentKey, orderId, amount) && ("EXPIRED".equals(recovered.status()) || "ABORTED".equals(recovered.status())))
                throw new TerminalPaymentException();
            throw new IllegalArgumentException("결제 결과를 확인 중입니다. 같은 주문으로 다시 확인해 주세요. 중복 결제하지 마세요.");
        }
    }

    private Approval recover(String paymentKey) {
        try {
            return client.get().uri("/v1/payments/{key}", paymentKey)
                    .header(HttpHeaders.AUTHORIZATION, authorization()).retrieve().body(Approval.class);
        } catch (RestClientException ignored) { return null; }
    }

    public State queryStoredPayment(String paymentKey) {
        properties.requireTossConfigured();
        return queryClient.get().uri("/v1/payments/{key}", paymentKey)
                .header(HttpHeaders.AUTHORIZATION, authorization()).retrieve().body(State.class);
    }

    public static class TerminalPaymentException extends IllegalArgumentException {
        public TerminalPaymentException() { super("결제 인증이 만료되었거나 결제가 승인되지 않았습니다. 강의 상세에서 다시 구매해 주세요."); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record State(String paymentKey, String orderId, String status, BigDecimal totalAmount,
                        BigDecimal balanceAmount, String currency, String approvedAt) { }

    private String authorization() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (properties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));
    }

    private static boolean matches(Approval result, String key, String orderId, BigDecimal amount) {
        return result != null && key.equals(result.paymentKey()) && orderId.equals(result.orderId())
                && result.totalAmount() != null && amount.compareTo(result.totalAmount()) == 0 && "KRW".equals(result.currency());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Approval(String paymentKey, String orderId, String status, BigDecimal totalAmount,
                           String currency, String approvedAt) {
        public LocalDateTime approvalTime() {
            if (approvedAt == null) throw new IllegalArgumentException("결제 승인 시각을 확인할 수 없습니다.");
            try { return OffsetDateTime.parse(approvedAt).atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime(); }
            catch (RuntimeException e) { throw new IllegalArgumentException("결제 승인 응답을 확인할 수 없습니다."); }
        }
    }
}
