package com.dev.MonicaKimEnglish.controller.front;

import java.util.Map;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.dev.MonicaKimEnglish.config.AppPrincipal;
import com.dev.MonicaKimEnglish.model.enums.PricingType;
import com.dev.MonicaKimEnglish.service.learning.LectureAccessService;
import com.dev.MonicaKimEnglish.service.learning.WishlistService;
import com.dev.MonicaKimEnglish.service.learning.ProgressService;
import com.dev.MonicaKimEnglish.service.lecture.LectureService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class LectureController {
    private final LectureService lectures;
    private final LectureAccessService access;
    private final WishlistService wishlists;
    private final ProgressService progress;

    @GetMapping("/lectures")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String tag,
                       @RequestParam(defaultValue = "ALL") String pricing,
                       @RequestParam(defaultValue = "ALL") String ownership,
                       Authentication authentication, Model model) {
        Long clientId = clientId(authentication);
        model.addAttribute("lectures", lectures.searchPublic(q, tag, pricing, ownership, clientId));
        model.addAttribute("tags", lectures.publicTagNames());
        model.addAttribute("q", q); model.addAttribute("tag", tag); model.addAttribute("pricing", pricing); model.addAttribute("ownership", ownership);
        return "front/lecture/list";
    }

    @GetMapping("/lectures/{id}")
    public String detail(@PathVariable Long id, Authentication authentication, Model model) {
        var lecture = lectures.get(id);
        Long clientId = clientId(authentication);
        var card = lectures.toCard(lecture, clientId);
        if (lecture.getStatus() != com.dev.MonicaKimEnglish.model.enums.LectureStatus.PUBLISHED
                && (lecture.getStatus() != com.dev.MonicaKimEnglish.model.enums.LectureStatus.ARCHIVED || !card.owned()))
            throw new IllegalArgumentException("현재 공개되지 않은 강의입니다.");
        model.addAttribute("lecture", lecture);
        model.addAttribute("card", card);
        model.addAttribute("images", lectures.introImages(id));
        model.addAttribute("instructorImages", lectures.instructorImages(id));
        var enrollment = access.findEnrollment(clientId, id).orElse(null);
        var historyEnrollment = clientId != null && card.owned() ? access.requireOwnedEnrollment(clientId, id) : null;
        model.addAttribute("enrollment", enrollment);
        model.addAttribute("historyEnrollment", historyEnrollment);
        model.addAttribute("hasCurrentEnrollment", enrollment != null);
        model.addAttribute("accessPending", enrollment != null && enrollment.getStartedAt().isAfter(LocalDateTime.now()));
        boolean canceled = historyEnrollment != null && historyEnrollment.getStatus() == com.dev.MonicaKimEnglish.model.enums.EnrollmentStatus.CANCELED;
        model.addAttribute("accessCanceled", canceled);
        model.addAttribute("accessExpired", !canceled && !card.accessAvailable() && card.expiresAt() != null && card.expiresAt().isBefore(LocalDateTime.now()));
        if (clientId != null && card.owned()) {
            model.addAttribute("learningSummary", progress.learningSummary(clientId, id));
        }
        model.addAttribute("attachments", lectures.attachmentList(id));
        model.addAttribute("tags", lectures.tagList(id));
        return "front/lecture/detail";
    }


    @GetMapping("/lectures/{id}/preview")
    @ResponseBody
    public Map<String, Object> preview(@PathVariable Long id) {
        var lecture = lectures.getPublic(id);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("id", lecture.getId());
        preview.put("title", lecture.getTitle());
        preview.put("description", lecture.getDescription());
        preview.put("pricing", lecture.getPricingType().name());
        preview.put("price", lecture.getPrice());
        preview.put("durationSeconds", lecture.getDurationSeconds());
        preview.put("tags", lectures.tagList(id));
        preview.put("imageIds", lectures.introImages(id).stream().map(com.dev.MonicaKimEnglish.model.lecture.LectureIntroImage::getId).toList());
        preview.put("instructorName", lecture.getInstructorName());
        preview.put("instructorBiography", lecture.getInstructorBiography());
        preview.put("instructorCareers", lecture.getInstructorCareers());
        preview.put("instructorImageIds", lectures.instructorImages(id).stream().map(com.dev.MonicaKimEnglish.model.lecture.LectureInstructorImage::getId).toList());
        preview.put("viewingDays", lecture.getViewingDays());
        preview.put("viewingPeriodLabel", lecture.getViewingPeriodLabel());
        preview.put("enrollmentOpen", lecture.isEnrollmentOpen());
        preview.put("enrollmentStatusLabel", lecture.getEnrollmentStatusLabel());
        preview.put("enrollmentStartsAt", lecture.getEnrollmentStartsAt());
        preview.put("enrollmentEndsAt", lecture.getEnrollmentEndsAt());
        preview.put("lectureStartsAt", lecture.getLectureStartsAt());
        return preview;
    }

    @PostMapping("/lectures/{id}/enroll-free")
    public String enrollFree(@PathVariable Long id, Authentication authentication) {
        Long clientId = requiredClientId(authentication);
        access.enrollFree(clientId, id);
        wishlists.removeIfExists(clientId, id);
        return "redirect:/lectures/" + id + "?enrolled";
    }

    @PostMapping("/wishlist/{id}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleWish(@PathVariable Long id, Authentication authentication) {
        try { boolean wished = wishlists.toggle(requiredClientId(authentication), id); return ResponseEntity.ok(Map.of("ok", true, "wished", wished)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage())); }
    }

    private Long clientId(Authentication a) {
        return a != null && a.getPrincipal() instanceof AppPrincipal p && "CLIENT".equals(p.getRole()) ? p.getAccountId() : null;
    }
    private Long requiredClientId(Authentication a) {
        Long id = clientId(a); if (id == null) throw new IllegalStateException("로그인이 필요합니다."); return id;
    }
}
