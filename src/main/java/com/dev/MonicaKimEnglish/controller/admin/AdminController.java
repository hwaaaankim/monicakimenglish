package com.dev.MonicaKimEnglish.controller.admin;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dev.MonicaKimEnglish.model.auth.AdminAccount;
import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.model.enums.*;
import com.dev.MonicaKimEnglish.repository.auth.AdminAccountRepository;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.repository.learning.LectureViewSessionRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureRepository;
import com.dev.MonicaKimEnglish.repository.payment.PaymentRepository;
import com.dev.MonicaKimEnglish.service.admin.AdminQueryService;
import com.dev.MonicaKimEnglish.service.common.QrCodeService;
import com.dev.MonicaKimEnglish.service.lecture.LectureService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminController {
    private final ClientRepository clients;
    private final AdminAccountRepository admins;
    private final PasswordEncoder encoder;
    private final LectureRepository lectureRepo;
    private final LectureService lectures;
    private final PaymentRepository payments;
    private final LectureViewSessionRepository viewSessions;
    private final AdminQueryService adminQueries;
    private final QrCodeService qr;

    @GetMapping("/admin/login") public String login() { return "admin/login"; }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        LocalDateTime to = LocalDateTime.now(), from = to.minusDays(30);
        model.addAttribute("memberCount", clients.count());
        model.addAttribute("lectureCount", lectureRepo.count());
        model.addAttribute("revenue30", payments.sumPaidAmount(from, to));
        model.addAttribute("salesRows", adminQueries.salesRows(from, to).stream().limit(5).toList());
        return "admin/dashboard";
    }

    @GetMapping("/admin/members")
    public String members(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="ALL") String status, Model model) {
        Specification<Client> spec = (root, query, cb) -> cb.conjunction();
        if (!q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("username")), like), cb.like(cb.lower(root.get("name")), like), cb.like(root.get("phone"), "%" + q.replaceAll("\\D", "") + "%")));
        }
        if (!status.equals("ALL")) try { ClientStatus s = ClientStatus.valueOf(status); spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), s)); } catch(Exception ignored) {}
        model.addAttribute("members", clients.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("q", q); model.addAttribute("status", status);
        return "admin/member/list";
    }

    @GetMapping("/admin/members/{id}")
    public String memberDetail(@PathVariable Long id, Model model) { model.addAttribute("member", clients.findById(id).orElseThrow()); return "admin/member/detail"; }

    @PostMapping("/admin/members/{id}")
    public String memberUpdate(@PathVariable Long id, @RequestParam String name, @RequestParam String phone,
            @RequestParam(required=false) String email, @RequestParam ClientStatus status) {
        Client c = clients.findById(id).orElseThrow();
        String normalizedPhone = phone.replaceAll("\\D", "");
        clients.findFirstByPhoneOrderByIdAsc(normalizedPhone).ifPresent(other -> { if (!other.getId().equals(id)) throw new IllegalArgumentException("이미 다른 회원이 사용 중인 연락처입니다."); });
        c.setName(name.trim()); c.setPhone(normalizedPhone); c.setEmail(email == null || email.isBlank() ? null : email.trim()); c.setStatus(status); clients.save(c);
        return "redirect:/admin/members/" + id + "?saved";
    }

    @GetMapping("/admin/lectures")
    public String lectureList(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="ALL") String status,
            @RequestParam(defaultValue="ALL") String pricing, Model model) {
        Specification<com.dev.MonicaKimEnglish.model.lecture.Lecture> spec = (root, query, cb) -> cb.conjunction();
        if (!q.isBlank()) spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), "%" + q.toLowerCase() + "%"));
        if (!status.equals("ALL")) try { LectureStatus s = LectureStatus.valueOf(status); spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), s)); } catch(Exception ignored) {}
        if (!pricing.equals("ALL")) try { PricingType p = PricingType.valueOf(pricing); spec = spec.and((root, query, cb) -> cb.equal(root.get("pricingType"), p)); } catch(Exception ignored) {}
        model.addAttribute("lectures", lectureRepo.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("q", q); model.addAttribute("status", status); model.addAttribute("pricing", pricing);
        return "admin/lecture/list";
    }

    @GetMapping("/admin/lectures/new") public String lectureNew() { return "admin/lecture/form"; }

    @PostMapping("/admin/lectures")
    public String lectureCreate(@RequestParam String title, @RequestParam String description, @RequestParam PricingType pricingType,
            @RequestParam(required=false) BigDecimal price, @RequestParam(required=false) Integer viewingDays, @RequestParam LectureStatus status,
            @RequestParam(required=false, defaultValue="") String tags, @RequestParam MultipartFile video,
            @RequestParam(required=false) MultipartFile[] introImages,
            @RequestParam(required=false, defaultValue="") String thumbnailSelection,
            @RequestParam(required=false) MultipartFile[] attachments,
            RedirectAttributes redirectAttributes) {
        try {
            var lecture = lectures.create(title, description, pricingType, price, viewingDays, status, tags,
                    video, introImages, thumbnailSelection, attachments);
            return "redirect:/admin/lectures/" + lecture.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("formError", e.getMessage());
            return "redirect:/admin/lectures/new";
        }
    }

    @GetMapping("/admin/lectures/{id}")
    public String lectureDetail(@PathVariable Long id, Model model) {
        var l = lectures.get(id); model.addAttribute("lecture", l); model.addAttribute("images", lectures.introImages(id)); model.addAttribute("attachments", lectures.attachmentList(id)); model.addAttribute("tags", lectures.tagList(id));
        model.addAttribute("buyers", adminQueries.buyers(id)); model.addAttribute("sessions", viewSessions.findRecentForLecture(id).stream().limit(200).toList()); model.addAttribute("purchaseCount", lectures.purchaseCount(id));
        return "admin/lecture/detail";
    }

    @GetMapping("/admin/lectures/{id}/edit")
    public String lectureEdit(@PathVariable Long id, Model model) {
        model.addAttribute("lecture", lectures.get(id)); model.addAttribute("images", lectures.introImages(id)); model.addAttribute("attachments", lectures.attachmentList(id)); model.addAttribute("tagText", String.join(", ", lectures.tagList(id)));
        return "admin/lecture/form";
    }

    @PostMapping("/admin/lectures/{id}")
    public String lectureUpdate(@PathVariable Long id, @RequestParam String title, @RequestParam String description, @RequestParam PricingType pricingType,
            @RequestParam(required=false) BigDecimal price, @RequestParam(required=false) Integer viewingDays, @RequestParam LectureStatus status,
            @RequestParam(required=false, defaultValue="") String tags, @RequestParam(required=false) MultipartFile video,
            @RequestParam(required=false) MultipartFile[] introImages,
            @RequestParam(required=false, defaultValue="") String thumbnailSelection,
            @RequestParam(required=false) MultipartFile[] attachments,
            @RequestParam(required=false) List<Long> deleteImageIds, @RequestParam(required=false) List<Long> deleteAttachmentIds,
            RedirectAttributes redirectAttributes) {
        try {
            lectures.update(id, title, description, pricingType, price, viewingDays, status, tags,
                    video, introImages, thumbnailSelection, attachments, deleteImageIds, deleteAttachmentIds);
            return "redirect:/admin/lectures/" + id + "?saved";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("formError", e.getMessage());
            return "redirect:/admin/lectures/" + id + "/edit";
        }
    }

    @PostMapping("/admin/lectures/{id}/archive")
    public String archive(@PathVariable Long id) { lectures.archive(id); return "redirect:/admin/lectures/" + id + "?archived"; }

    @GetMapping(value="/admin/lectures/{id}/qr.png", produces=MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] qr(@PathVariable Long id, HttpServletRequest req) {
        lectures.get(id);
        String base = req.getScheme() + "://" + req.getServerName() + ((req.getServerPort()==80 || req.getServerPort()==443) ? "" : ":" + req.getServerPort());
        return qr.png(base + "/lectures/" + id, 360);
    }

    @GetMapping("/admin/sales")
    public String sales(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue="30") int preset, Model model) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(Math.max(1, preset) - 1L) : from;
        if (start.isAfter(end)) { LocalDate tmp = start; start = end; end = tmp; }
        LocalDateTime f = start.atStartOfDay(), t = end.plusDays(1).atStartOfDay().minusNanos(1);
        var rows = adminQueries.salesRows(f, t);
        model.addAttribute("rows", rows); model.addAttribute("from", start); model.addAttribute("to", end);
        model.addAttribute("totalRevenue", rows.stream().map(com.dev.MonicaKimEnglish.dto.admin.SalesRowDto::revenue).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("totalSales", rows.stream().mapToLong(com.dev.MonicaKimEnglish.dto.admin.SalesRowDto::salesCount).sum());
        model.addAttribute("totalViews", rows.stream().mapToLong(com.dev.MonicaKimEnglish.dto.admin.SalesRowDto::viewCount).sum());
        return "admin/sales/index";
    }
}
