package com.dev.MonicaKimEnglish.controller.front;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.dev.MonicaKimEnglish.dto.auth.SignupForm;
import com.dev.MonicaKimEnglish.service.auth.ClientService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final ClientService clients;

    @GetMapping("/login") public String login() { return "front/auth/login"; }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new SignupForm());
        return "front/auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult binding, Model model) {
        if (binding.hasErrors()) return "front/auth/signup";
        try {
            clients.signup(form);
            return "redirect:/auth/login?joined";
        } catch (IllegalArgumentException e) {
            model.addAttribute("formError", e.getMessage());
            return "front/auth/signup";
        }
    }

    @GetMapping("/check-username")
    @ResponseBody
    public Map<String, Object> checkUsername(@RequestParam String username) {
        boolean available = clients.usernameAvailable(username);
        return Map.of("available", available, "message", available ? "사용 가능한 아이디입니다." : "사용할 수 없는 아이디입니다.");
    }

    @PostMapping("/find-id")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> findId(@RequestParam String phone) {
        try { clients.sendFoundUsername(phone); return ResponseEntity.ok(Map.of("ok", true, "message", "가입된 연락처로 아이디를 발송했습니다.")); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage())); }
    }

    @PostMapping("/reset-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestParam String username, @RequestParam String phone) {
        try { clients.sendTemporaryPassword(username, phone); return ResponseEntity.ok(Map.of("ok", true, "message", "가입된 연락처로 임시 비밀번호를 발송했습니다.")); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage())); }
    }
}
