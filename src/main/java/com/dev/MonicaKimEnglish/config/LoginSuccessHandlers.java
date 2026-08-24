package com.dev.MonicaKimEnglish.config;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import com.dev.MonicaKimEnglish.model.auth.AdminAccount;
import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.repository.auth.AdminAccountRepository;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

public final class LoginSuccessHandlers {
    private LoginSuccessHandlers() {}

    @Component
    @RequiredArgsConstructor
    public static class ClientLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
        private final ClientRepository clients;
        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
            AppPrincipal p = (AppPrincipal) authentication.getPrincipal();
            Client c = clients.findById(p.getAccountId()).orElse(null);
            if (c != null) { c.setLastLoginAt(LocalDateTime.now()); clients.save(c); }

            // 이전 세션에 정적 리소스/서비스워커 요청이 SavedRequest로 남아 있어도
            // 로그인 직후 그 주소로 이동하지 않도록 안전한 화면 경로만 유지합니다.
            HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
            SavedRequest savedRequest = requestCache.getRequest(request, response);
            if (savedRequest != null && !isSafeClientNavigation(savedRequest.getRedirectUrl())) {
                requestCache.removeRequest(request, response);
            }

            setDefaultTargetUrl("/lectures");
            super.onAuthenticationSuccess(request, response, authentication);
        }

        private boolean isSafeClientNavigation(String redirectUrl) {
            if (redirectUrl == null || redirectUrl.isBlank()) {
                return false;
            }
            try {
                String path = URI.create(redirectUrl).getPath();
                if (path == null) {
                    return false;
                }
                return path.equals("/my")
                        || path.startsWith("/my/")
                        || path.equals("/wishlist")
                        || path.startsWith("/wishlist/")
                        || path.equals("/payments")
                        || path.startsWith("/payments/")
                        || path.equals("/watch")
                        || path.startsWith("/watch/");
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class AdminLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
        private final AdminAccountRepository admins;
        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
            AppPrincipal p = (AppPrincipal) authentication.getPrincipal();
            AdminAccount a = admins.findById(p.getAccountId()).orElse(null);
            if (a != null) { a.setLastLoginAt(LocalDateTime.now()); admins.save(a); }
            setDefaultTargetUrl("/admin/dashboard");
            setAlwaysUseDefaultTargetUrl(true);
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
