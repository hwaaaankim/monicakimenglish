package com.dev.MonicaKimEnglish.config;

import java.io.IOException;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.dev.MonicaKimEnglish.model.enums.ClientStatus;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 실패 사유를 화면에서 이해하기 쉬운 코드로 분리합니다.
 *
 * 보안상 "존재하지 않는 아이디"와 "비밀번호 불일치"는 credentials 하나로 묶어
 * 계정 존재 여부가 외부에 노출되지 않도록 합니다.
 */
public final class LoginFailureHandlers {

    private LoginFailureHandlers() {
    }

    @Slf4j
    @Component
    public static class AdminLoginFailureHandler implements AuthenticationFailureHandler {

        @Override
        public void onAuthenticationFailure(
                HttpServletRequest request,
                HttpServletResponse response,
                AuthenticationException exception) throws IOException, ServletException {

            String username = normalizeUsername(request.getParameter("username"));
            String errorCode = resolveCommonErrorCode(exception, "inactive");

            log.warn("Admin login failed. username={}, reason={}", username, exception.getClass().getSimpleName());
            response.sendRedirect(request.getContextPath() + "/admin/login?error=" + errorCode);
        }
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class ClientLoginFailureHandler implements AuthenticationFailureHandler {

        private final ClientRepository clients;

        @Override
        public void onAuthenticationFailure(
                HttpServletRequest request,
                HttpServletResponse response,
                AuthenticationException exception) throws IOException, ServletException {

            String username = normalizeUsername(request.getParameter("username"));
            String errorCode;

            if (exception instanceof DisabledException) {
                errorCode = clients.findByUsername(username)
                        .map(client -> {
                            if (client.getStatus() == ClientStatus.SUSPENDED) {
                                return "suspended";
                            }
                            if (client.getStatus() == ClientStatus.WITHDRAWN) {
                                return "withdrawn";
                            }
                            return "inactive";
                        })
                        .orElse("credentials");
            } else {
                errorCode = resolveCommonErrorCode(exception, "inactive");
            }

            log.warn("Client login failed. username={}, reason={}, code={}",
                    username, exception.getClass().getSimpleName(), errorCode);

            response.sendRedirect(request.getContextPath() + "/auth/login?error=" + errorCode);
        }
    }

    private static String resolveCommonErrorCode(AuthenticationException exception, String inactiveCode) {
        if (exception instanceof DisabledException
                || exception instanceof LockedException
                || exception instanceof AccountExpiredException
                || exception instanceof CredentialsExpiredException) {
            return inactiveCode;
        }
        if (exception instanceof AuthenticationServiceException) {
            return "system";
        }
        // BadCredentialsException과 UsernameNotFoundException은 같은 메시지로 처리합니다.
        return "credentials";
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }
}
