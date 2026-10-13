package com.dev.MonicaKimEnglish.controller.front;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.dev.MonicaKimEnglish.service.payment.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PaymentWebhookController {
    private final PaymentWebhookService webhooks;

    @PostMapping("/payments/toss/webhook")
    public ResponseEntity<Map<String, Boolean>> webhook(@RequestBody Event event) {
        if (event != null && "PAYMENT_STATUS_CHANGED".equals(event.eventType()) && event.data() != null) {
            try { webhooks.synchronize(event.data().paymentKey()); }
            catch (RuntimeException unavailable) {
                // A non-200 response asks Toss to retry; no keys, customer details or provider errors are echoed.
                return ResponseEntity.status(503).body(Map.of("received", false));
            }
        }
        return ResponseEntity.ok(Map.of("received", true));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String eventType, Data data) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String paymentKey) { }
}
