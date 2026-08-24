package com.dev.MonicaKimEnglish.config;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.dev.MonicaKimEnglish.model.auth.AdminAccount;
import com.dev.MonicaKimEnglish.repository.auth.AdminAccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository adminRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? "" : username.trim();

        AdminAccount admin = adminRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("관리자 계정이 없습니다."));

        return new AppPrincipal(
                admin.getId(),
                admin.getUsername(),
                admin.getPassword(),
                admin.getName(),
                "ADMIN",
                admin.isActive()
        );
    }
}
