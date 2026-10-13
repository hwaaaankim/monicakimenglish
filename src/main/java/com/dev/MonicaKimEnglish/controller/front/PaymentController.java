package com.dev.MonicaKimEnglish.controller.front;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.dev.MonicaKimEnglish.config.AppPrincipal;
import com.dev.MonicaKimEnglish.model.learning.CoursePurchasePolicy;
import com.dev.MonicaKimEnglish.model.learning.Enrollment;
import com.dev.MonicaKimEnglish.model.payment.Payment;
import com.dev.MonicaKimEnglish.repository.learning.EnrollmentRepository;
import com.dev.MonicaKimEnglish.service.payment.PaymentProperties;
import com.dev.MonicaKimEnglish.service.payment.PaymentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService payments;
    private final PaymentProperties properties;
    private final EnrollmentRepository enrollments;

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Referrer-Policy", "same-origin");
    }

    @GetMapping("/checkout/{lectureId}")
    public String checkout(@PathVariable Long lectureId, Authentication auth, Model model) {
        if (payments.alreadyEnrolled(clientId(auth), lectureId)) return "redirect:/lectures/" + lectureId;
        model.addAttribute("lecture", payments.checkoutLecture(lectureId));
        model.addAttribute("paymentMode", properties.getMode().name());
        model.addAttribute("tossTest", !properties.isMock() && properties.isTestKey());
        model.addAttribute("refundPolicy", CoursePurchasePolicy.TEXT);
        model.addAttribute("refundVersion", CoursePurchasePolicy.VERSION);
        return "front/payment/checkout";
    }

    @PostMapping("/orders")
    @ResponseBody
    public ResponseEntity<?> createOrder(@Valid @RequestBody OrderRequest request, Authentication auth) {
        try {
        Payment payment = payments.createOrder(clientId(auth), request.lectureId(), request.termsAgreed());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", payment.getOrderNo());
        data.put("orderName", payment.getLectureTitleSnapshot().substring(0, Math.min(100, payment.getLectureTitleSnapshot().length())));
        data.put("amount", payment.getAmount());
        data.put("mode", properties.getMode().name());
        data.put("customerKey", "MK_" + payment.getOrderNo().substring(3));
        if (!properties.isMock()) {
            data.put("clientKey", properties.getClientKey());
            data.put("successUrl", properties.callbackUrl("/payments/toss/success"));
            data.put("failUrl", properties.callbackUrl("/payments/toss/fail"));
        }
        return ResponseEntity.ok(data);
        } catch (IllegalArgumentException exception) { return apiError(exception); }
    }

    @PostMapping("/mock/approve")
    @ResponseBody
    public ResponseEntity<?> mockApprove(@Valid @RequestBody MockRequest request, Authentication auth) {
        try { return ResponseEntity.ok(completion(payments.mockApproveOrder(clientId(auth), request.orderId()))); }
        catch (IllegalArgumentException exception) { return apiError(exception); }
    }

    @GetMapping("/toss/success")
    public String tossSuccess(@RequestParam String orderId, @RequestParam String paymentKey,
                              @RequestParam BigDecimal amount, Authentication auth, Model model) {
        properties.requireTossConfigured();
        Payment payment = payments.ownedOrder(clientId(auth), orderId);
        model.addAttribute("payment", payment);
        model.addAttribute("paymentKey", paymentKey);
        model.addAttribute("callbackAmount", amount);
        return "front/payment/confirm";
    }

    @PostMapping("/toss/confirm")
    @ResponseBody
    public ResponseEntity<?> confirm(@Valid @RequestBody ConfirmRequest request, Authentication auth) {
        try { return ResponseEntity.ok(completion(payments.confirmToss(clientId(auth), request.orderId(), request.paymentKey(), request.amount()))); }
        catch (IllegalArgumentException exception) { return apiError(exception); }
    }

    @GetMapping("/completed/{orderId}")
    public String completed(@PathVariable String orderId, Authentication auth, Model model) {
        Payment payment = payments.completedOrder(clientId(auth), orderId);
        Enrollment enrollment = enrollments.findFirstByPaymentId(payment.getId())
                .orElseThrow(() -> new IllegalArgumentException("수강 등록 정보를 확인할 수 없습니다. 고객센터에 문의해 주세요."));
        model.addAttribute("payment", payment);
        model.addAttribute("enrollment", enrollment);
        model.addAttribute("startsLater", enrollment.getStartedAt().isAfter(LocalDateTime.now()));
        return "front/payment/success";
    }

    @GetMapping("/recover/{orderId}")
    public String recover(@PathVariable String orderId, Authentication auth, Model model) {
        properties.requireTossConfigured();
        Payment payment = payments.ownedOrder(clientId(auth), orderId);
        if (payment.getPaymentKey() == null) throw new IllegalArgumentException("결제 인증 정보가 없습니다. 고객센터에 문의해 주세요.");
        model.addAttribute("payment", payment);
        model.addAttribute("paymentKey", payment.getPaymentKey());
        model.addAttribute("callbackAmount", payment.getAmount());
        return "front/payment/confirm";
    }

    @GetMapping("/toss/fail")
    public String fail(@RequestParam(required = false) String orderId, Model model, Authentication auth) {
        if (orderId != null) model.addAttribute("payment", payments.ownedOrder(clientId(auth), orderId));
        // A browser redirect is not authoritative payment state and must not change a paid order.
        model.addAttribute("message", "결제를 완료하지 못했거나 결제창을 닫았습니다. 결제내역을 확인한 뒤 다시 시도해 주세요.");
        return "front/payment/fail";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String paymentError(IllegalArgumentException exception, HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("message", exception.getMessage());
        return "front/payment/fail";
    }

    private static ResponseEntity<?> apiError(IllegalArgumentException exception) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", exception.getMessage());
        if (exception instanceof PaymentService.AlreadyEnrolledException owned) {
            body.put("redirectUrl", "/lectures/" + owned.getLectureId());
        }
        if (exception instanceof PaymentService.PendingPaymentException pending) {
            body.put("redirectUrl", "/payments/recover/" + pending.getOrderId());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private static Long clientId(Authentication auth) { return ((AppPrincipal) auth.getPrincipal()).getAccountId(); }
    private static Map<String, String> completion(Payment payment) {
        return Map.of("redirectUrl", "/payments/completed/" + payment.getOrderNo());
    }
    public record OrderRequest(@NotNull @Positive Long lectureId, boolean termsAgreed) { }
    public record MockRequest(@NotBlank String orderId) { }
    public record ConfirmRequest(@NotBlank String orderId, @NotBlank String paymentKey, @NotNull @Positive BigDecimal amount) { }
}
