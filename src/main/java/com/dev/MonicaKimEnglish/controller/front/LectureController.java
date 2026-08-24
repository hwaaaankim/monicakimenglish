package com.dev.MonicaKimEnglish.controller.front;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.dev.MonicaKimEnglish.config.AppPrincipal;
import com.dev.MonicaKimEnglish.model.enums.PricingType;
import com.dev.MonicaKimEnglish.service.learning.LectureAccessService;
import com.dev.MonicaKimEnglish.service.learning.WishlistService;
import com.dev.MonicaKimEnglish.service.lecture.LectureService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class LectureController {
    private final LectureService lectures;
    private final LectureAccessService access;
    private final WishlistService wishlists;

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
        if (lecture.getStatus() != com.dev.MonicaKimEnglish.model.enums.LectureStatus.PUBLISHED && (clientId == null || !access.canWatch(clientId, id)))
            throw new IllegalArgumentException("현재 공개되지 않은 강의입니다.");
        model.addAttribute("lecture", lecture);
        model.addAttribute("card", lectures.toCard(lecture, clientId));
        model.addAttribute("images", lectures.introImages(id));
        model.addAttribute("attachments", lectures.attachmentList(id));
        model.addAttribute("tags", lectures.tagList(id));
        return "front/lecture/detail";
    }


    @GetMapping("/lectures/{id}/preview")
    @ResponseBody
    public Map<String, Object> preview(@PathVariable Long id) {
        var lecture = lectures.getPublic(id);
        return Map.of(
            "id", lecture.getId(),
            "title", lecture.getTitle(),
            "description", lecture.getDescription(),
            "pricing", lecture.getPricingType().name(),
            "price", lecture.getPrice(),
            "durationSeconds", lecture.getDurationSeconds(),
            "tags", lectures.tagList(id),
            "imageIds", lectures.introImages(id).stream().map(com.dev.MonicaKimEnglish.model.lecture.LectureIntroImage::getId).toList());
    }

    @PostMapping("/lectures/{id}/enroll-free")
    public String enrollFree(@PathVariable Long id, Authentication authentication) {
        Long clientId = requiredClientId(authentication);
        access.enrollFree(clientId, id);
        wishlists.removeIfExists(clientId, id);
        return "redirect:/watch/" + id;
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
