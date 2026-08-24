package com.dev.MonicaKimEnglish.config;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserAdvice {
    @ModelAttribute("currentUser")
    public AppPrincipal currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof AppPrincipal p)) return null;
        return p;
    }
}
