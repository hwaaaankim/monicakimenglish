package com.dev.MonicaKimEnglish.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import com.dev.MonicaKimEnglish.config.LoginFailureHandlers.AdminLoginFailureHandler;
import com.dev.MonicaKimEnglish.config.LoginFailureHandlers.ClientLoginFailureHandler;
import com.dev.MonicaKimEnglish.config.LoginSuccessHandlers.AdminLoginSuccessHandler;
import com.dev.MonicaKimEnglish.config.LoginSuccessHandlers.ClientLoginSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean("adminAuthenticationProvider")
    AuthenticationProvider adminAuthenticationProvider(
            AdminUserDetailsService adminUserDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean("clientAuthenticationProvider")
    AuthenticationProvider clientAuthenticationProvider(
            ClientUserDetailsService clientUserDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(clientUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * 관리자 전용 SecurityFilterChain.
     *
     * 관리자와 고객의 AuthenticationProvider 타입이 동일하기 때문에 반드시
     * @Qualifier로 관리자 Provider를 명시합니다.
     */
    @Bean
    @Order(1)
    SecurityFilterChain adminChain(
            HttpSecurity http,
            @Qualifier("adminAuthenticationProvider") AuthenticationProvider adminAuthenticationProvider,
            AdminLoginSuccessHandler successHandler,
            AdminLoginFailureHandler failureHandler) throws Exception {

        http
            .securityMatcher("/admin/**")
            .authenticationProvider(adminAuthenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/admin/login",
                    "/admin/login/process",
                    "/admin/css/**",
                    "/admin/lib/**",
                    "/admin/img/**",
                    "/admin/js/**"
                ).permitAll()
                .anyRequest().hasRole("ADMIN")
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login/process")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout")
                .permitAll()
            );

        return http.build();
    }

    /**
     * 고객/게스트용 SecurityFilterChain.
     */
    @Bean
    @Order(2)
    SecurityFilterChain clientChain(
            HttpSecurity http,
            @Qualifier("clientAuthenticationProvider") AuthenticationProvider clientAuthenticationProvider,
            ClientUserDetailsService clientUserDetailsService,
            ClientLoginSuccessHandler successHandler,
            ClientLoginFailureHandler failureHandler,
            @Value("${app.security.remember-me-key:change-this-in-production}") String rememberKey) throws Exception {

        http
            // 초기 관리자 생성 API는 bootstrap token을 별도로 검증하므로 개발 초기화 중에만 CSRF 제외합니다.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/setup/admin", "/setup/admin/reset-password"))
            .authenticationProvider(clientAuthenticationProvider)
            // 로그인 후 돌아갈 주소는 실제 HTML 화면 요청만 저장합니다.
            // 서비스워커/JS/영상/API 같은 백그라운드 요청이 로그인 성공 경로를 가로채지 못하게 합니다.
            .requestCache(cache -> cache.requestCache(clientNavigationRequestCache()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/index",
                    "/program",
                    "/member",
                    "/recruit",
                    "/contact",
                    "/client"
                ).permitAll()
                .requestMatchers(
                    "/front/**",
                    "/favicon.ico",
                    "/error",
                    "/auth/**",
                    "/setup/**"
                ).permitAll()
                // 강의 목록/상세/소개 이미지는 게스트도 열람할 수 있습니다.
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/lectures",
                    "/lectures/*",
                    "/lectures/*/preview",
                    "/media/lectures/**",
                    "/media/popups/**"
                ).permitAll()
                // 실제 수강/영상/자료는 고객 인증이 필요합니다.
                .requestMatchers(
                    "/my/**",
                    "/wishlist/**",
                    "/payments/**",
                    "/watch/**",
                    "/secure-media/**",
                    "/api/player/**"
                ).hasRole("CLIENT")
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/lectures/**"
                ).hasRole("CLIENT")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login/process")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key(rememberKey)
                // 관리자/고객 UserDetailsService가 동시에 있으므로 고객용을 반드시 명시합니다.
                .userDetailsService(clientUserDetailsService)
                .tokenValiditySeconds(60 * 60 * 24 * 30)
                .rememberMeParameter("remember-me")
            )
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/index?logout")
                .permitAll()
            );

        return http.build();
    }

    /**
     * 고객 로그인 전 SavedRequest는 실제 화면 이동 후보만 저장합니다.
     * 브라우저의 서비스워커 갱신, 정적 리소스, 미디어/API 요청이 먼저 401/302를 만들더라도
     * 로그인 완료 후 해당 리소스로 리다이렉트되는 문제를 방지합니다.
     */
    private static HttpSessionRequestCache clientNavigationRequestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(request -> {
            if (!"GET".equalsIgnoreCase(request.getMethod())) {
                return false;
            }

            String uri = request.getRequestURI();
            String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
            String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;

            boolean protectedNavigation = path.equals("/my")
                    || path.startsWith("/my/")
                    || path.equals("/wishlist")
                    || path.startsWith("/wishlist/")
                    || path.equals("/payments")
                    || path.startsWith("/payments/")
                    || path.equals("/watch")
                    || path.startsWith("/watch/");

            if (!protectedNavigation) {
                return false;
            }

            String accept = request.getHeader("Accept");
            return accept == null || accept.isBlank() || accept.contains("text/html");
        });
        return requestCache;
    }
}
