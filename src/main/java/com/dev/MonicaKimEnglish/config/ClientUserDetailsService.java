package com.dev.MonicaKimEnglish.config;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.model.enums.ClientStatus;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientUserDetailsService implements UserDetailsService {

    private final ClientRepository clientRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? "" : username.trim();

        Client client = clientRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("회원정보가 없습니다."));

        return new AppPrincipal(
                client.getId(),
                client.getUsername(),
                client.getPassword(),
                client.getName(),
                "CLIENT",
                client.getStatus() == ClientStatus.ACTIVE
        );
    }
}
