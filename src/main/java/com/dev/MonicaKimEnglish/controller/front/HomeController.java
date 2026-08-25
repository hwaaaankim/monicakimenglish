package com.dev.MonicaKimEnglish.controller.front;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.dev.MonicaKimEnglish.service.popup.HomePopupService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {
    private final HomePopupService popupService;

    @GetMapping({ "/index", "/" })
    public String index(Model model) {
        model.addAttribute("homePopups", popupService.activeNow());
        return "front/index";
    }

    @GetMapping("/program")
    public String program() {
        return "front/program";
    }

    @GetMapping("/member")
    public String member() {
        return "front/member";
    }

    @GetMapping("/recruit")
    public String recruit() {
        return "front/recruit";
    }

    @GetMapping("/contact")
    public String contact() {
        return "front/contact";
    }

    @GetMapping("/client")
    public String client() {
        return "front/client";
    }
}
