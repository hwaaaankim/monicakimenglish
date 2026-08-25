package com.dev.MonicaKimEnglish.controller.admin;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dev.MonicaKimEnglish.model.popup.HomePopup;
import com.dev.MonicaKimEnglish.service.popup.HomePopupService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PopupAdminController {
    private final HomePopupService popupService;

    @GetMapping("/admin/popups")
    public String list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "ALL") String state,
            @RequestParam(required = false) Long editId,
            Model model) {

        HomePopup editPopup = editId == null ? null : popupService.get(editId);
        model.addAttribute("popups", popupService.listForAdmin(q, state));
        model.addAttribute("q", q);
        model.addAttribute("state", state);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("editPopup", editPopup);
        model.addAttribute("formAction", editPopup == null ? "/admin/popups" : "/admin/popups/" + editPopup.getId());
        return "admin/popup/index";
    }

    @PostMapping("/admin/popups")
    public String create(
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String targetUrl,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            RedirectAttributes redirectAttributes) {
        try {
            popupService.create(image, targetUrl, startDate, endDate);
            redirectAttributes.addFlashAttribute("successMessage", "팝업을 등록했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/popups#popupForm";
    }

    @PostMapping("/admin/popups/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) String targetUrl,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            RedirectAttributes redirectAttributes) {
        try {
            popupService.update(id, image, targetUrl, startDate, endDate);
            redirectAttributes.addFlashAttribute("successMessage", "팝업을 수정했습니다.");
            return "redirect:/admin/popups";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/popups?editId=" + id + "#popupForm";
        }
    }

    @PostMapping("/admin/popups/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            popupService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "팝업을 삭제했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/popups";
    }
}
