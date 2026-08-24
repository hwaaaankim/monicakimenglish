package com.dev.MonicaKimEnglish.controller.front;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.dev.MonicaKimEnglish.config.AppPrincipal;
import com.dev.MonicaKimEnglish.service.lecture.LectureService;
import com.dev.MonicaKimEnglish.service.payment.PaymentService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {
    private final LectureService lectures;
    private final PaymentService payments;

    @GetMapping("/checkout/{lectureId}")
    public String checkout(@PathVariable Long lectureId, Model model) {
        model.addAttribute("lecture", lectures.getPublic(lectureId));
        return "front/payment/checkout";
    }

    @PostMapping("/mock/approve/{lectureId}")
    public String mockApprove(@PathVariable Long lectureId, Authentication auth) {
        payments.mockApprove(((AppPrincipal)auth.getPrincipal()).getAccountId(), lectureId);
        return "redirect:/watch/" + lectureId + "?paid";
    }
}
