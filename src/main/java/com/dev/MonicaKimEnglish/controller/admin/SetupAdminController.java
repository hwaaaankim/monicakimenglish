package com.dev.MonicaKimEnglish.controller.admin;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.MonicaKimEnglish.model.auth.AdminAccount;
import com.dev.MonicaKimEnglish.repository.auth.AdminAccountRepository;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;

import lombok.RequiredArgsConstructor;

/**
 * 최초 서버 구축 시에만 사용하는 관리자 bootstrap API입니다.
 *
 * 관리자 생성/비밀번호 복구가 끝나면
 * 1) app.bootstrap-admin.enabled=false
 * 2) 이 Controller 삭제
 * 두 작업을 반드시 진행해주세요.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/setup")
public class SetupAdminController {

    private final AdminAccountRepository admins;
    private final ClientRepository clients;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.enabled:false}")
    private boolean enabled;

    @Value("${app.bootstrap-admin.token:CHANGE_ME}")
    private String requiredToken;

    @PostMapping("/admin")
    public ResponseEntity<?> create(
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String token,
            @RequestParam(defaultValue = "admin") String username,
            @RequestParam(defaultValue = "12345") String password,
            @RequestParam(defaultValue = "최고관리자") String name) {

        ResponseEntity<?> validation = validateBootstrapRequest(token);
        if (validation != null) {
            return validation;
        }

        String normalizedUsername = normalizeRequired(username);
        String normalizedName = normalizeRequired(name);

        if (normalizedUsername.isBlank() || normalizedName.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "아이디, 비밀번호, 관리자명을 모두 입력해주세요."));
        }

        if (admins.count() > 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "관리자 계정이 이미 존재합니다. 비밀번호 복구가 필요하면 /setup/admin/reset-password 를 사용해주세요."
            ));
        }

        if (admins.existsByUsername(normalizedUsername) || clients.existsByUsername(normalizedUsername)) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미 사용 중인 아이디입니다."));
        }

        String encodedPassword = passwordEncoder.encode(password);
        if (!passwordEncoder.matches(password, encodedPassword)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "비밀번호 암호화 검증에 실패했습니다."));
        }

        AdminAccount admin = new AdminAccount();
        admin.setUsername(normalizedUsername);
        admin.setPassword(encodedPassword);
        admin.setName(normalizedName);
        admin.setActive(true);
        admins.saveAndFlush(admin);

        return ResponseEntity.ok(Map.of(
                "created", true,
                "username", admin.getUsername(),
                "passwordEncoded", true,
                "active", admin.isActive(),
                "warning", "로그인 확인 후 즉시 bootstrap API를 비활성화하고 SetupAdminController를 삭제해주세요."
        ));
    }

    /**
     * 이미 생성된 초기 관리자 계정의 비밀번호를 BCrypt로 다시 저장하는 복구 API입니다.
     * 개발 초기 세팅에서만 사용하세요.
     */
    @PostMapping("/admin/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String token,
            @RequestParam(defaultValue = "admin") String username,
            @RequestParam(defaultValue = "12345") String password,
            @RequestParam(defaultValue = "true") boolean activate) {

        ResponseEntity<?> validation = validateBootstrapRequest(token);
        if (validation != null) {
            return validation;
        }

        String normalizedUsername = normalizeRequired(username);
        if (normalizedUsername.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "아이디와 새 비밀번호를 입력해주세요."));
        }

        AdminAccount admin = admins.findByUsername(normalizedUsername)
                .orElse(null);

        if (admin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "해당 관리자 계정을 찾을 수 없습니다."));
        }

        String encodedPassword = passwordEncoder.encode(password);
        if (!passwordEncoder.matches(password, encodedPassword)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "비밀번호 암호화 검증에 실패했습니다."));
        }

        admin.setPassword(encodedPassword);
        if (activate) {
            admin.setActive(true);
        }
        admins.saveAndFlush(admin);

        return ResponseEntity.ok(Map.of(
                "reset", true,
                "username", admin.getUsername(),
                "passwordEncoded", true,
                "active", admin.isActive(),
                "warning", "로그인 확인 후 즉시 bootstrap API를 비활성화하고 SetupAdminController를 삭제해주세요."
        ));
    }

    private ResponseEntity<?> validateBootstrapRequest(String token) {
        if (!enabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "관리자 초기등록 API가 비활성화되어 있습니다."));
        }

        if (requiredToken == null || requiredToken.isBlank() || "CHANGE_ME".equals(requiredToken)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "MONICA_BOOTSTRAP_ADMIN_TOKEN을 먼저 안전한 난수로 설정해주세요."));
        }

        if (!requiredToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "초기등록 토큰이 일치하지 않습니다."));
        }

        return null;
    }

    private String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }
}
