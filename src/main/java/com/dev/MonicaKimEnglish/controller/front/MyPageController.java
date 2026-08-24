package com.dev.MonicaKimEnglish.controller.front;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.dev.MonicaKimEnglish.config.AppPrincipal;
import com.dev.MonicaKimEnglish.dto.lecture.LectureCardDto;
import com.dev.MonicaKimEnglish.model.enums.PricingType;
import com.dev.MonicaKimEnglish.model.learning.Enrollment;
import com.dev.MonicaKimEnglish.repository.learning.EnrollmentRepository;
import com.dev.MonicaKimEnglish.service.auth.ClientService;
import com.dev.MonicaKimEnglish.service.learning.WishlistService;
import com.dev.MonicaKimEnglish.service.lecture.LectureService;
import com.dev.MonicaKimEnglish.service.payment.PaymentService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/my")
public class MyPageController {
    private final EnrollmentRepository enrollments;
    private final WishlistService wishlistService;
    private final LectureService lectureService;
    private final PaymentService payments;
    private final ClientService clients;

    @GetMapping("/classroom")
    public String classroom(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="ALL") String access,
            @RequestParam(defaultValue="ALL") String progress, @RequestParam(defaultValue="ALL") String pricing,
            Authentication auth, Model model) {
        Long cid = cid(auth);
        Map<Long, Enrollment> latest = new LinkedHashMap<>();
        for (Enrollment e : enrollments.findAllForClient(cid)) latest.putIfAbsent(e.getLecture().getId(), e);
        List<LectureCardDto> rows = latest.values().stream().map(e -> lectureService.toCard(e.getLecture(), cid))
            .filter(r -> q.isBlank() || r.title().toLowerCase().contains(q.toLowerCase()))
            .filter(r -> access.equals("ALL") || (access.equals("AVAILABLE") == r.accessAvailable()))
            .filter(r -> progress.equals("ALL") || (progress.equals("COMPLETED") == r.completed()))
            .filter(r -> pricing.equals("ALL") || r.pricingType().name().equals(pricing)).toList();
        model.addAttribute("lectures", rows); model.addAttribute("q", q); model.addAttribute("access", access); model.addAttribute("progress", progress); model.addAttribute("pricing", pricing);
        return "front/my/classroom";
    }

    @GetMapping("/wishlist")
    public String wishlist(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="ALL") String pricing, Authentication auth, Model model) {
        Long cid = cid(auth);
        var rows = wishlistService.list(cid).stream().map(w -> lectureService.toCard(w.getLecture(), cid))
            .filter(r -> q.isBlank() || r.title().toLowerCase().contains(q.toLowerCase()))
            .filter(r -> pricing.equals("ALL") || r.pricingType().name().equals(pricing)).toList();
        model.addAttribute("lectures", rows); model.addAttribute("q", q); model.addAttribute("pricing", pricing);
        return "front/my/wishlist";
    }

    @GetMapping("/payments")
    public String paymentHistory(Authentication auth, Model model) {
        model.addAttribute("payments", payments.clientPayments(cid(auth)));
        return "front/my/payments";
    }

    @GetMapping("/profile")
    public String profile(Authentication auth, Model model) {
        model.addAttribute("client", clients.get(cid(auth)));
        return "front/my/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String name, @RequestParam String phone, @RequestParam(required=false) String email, Authentication auth, Model model) {
        try { clients.updateProfile(cid(auth), name, phone, email); return "redirect:/my/profile?saved"; }
        catch (IllegalArgumentException e) { model.addAttribute("client", clients.get(cid(auth))); model.addAttribute("profileError", e.getMessage()); return "front/my/profile"; }
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword, @RequestParam String confirmPassword, Authentication auth, Model model) {
        try { clients.changePassword(cid(auth), currentPassword, newPassword, confirmPassword); return "redirect:/my/profile?passwordChanged"; }
        catch (IllegalArgumentException e) { model.addAttribute("client", clients.get(cid(auth))); model.addAttribute("passwordError", e.getMessage()); return "front/my/profile"; }
    }

    private Long cid(Authentication a) { return ((AppPrincipal)a.getPrincipal()).getAccountId(); }
}
