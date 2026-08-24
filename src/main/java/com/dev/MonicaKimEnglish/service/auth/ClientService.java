package com.dev.MonicaKimEnglish.service.auth;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.MonicaKimEnglish.dto.auth.SignupForm;
import com.dev.MonicaKimEnglish.model.auth.Client;
import com.dev.MonicaKimEnglish.model.enums.ClientStatus;
import com.dev.MonicaKimEnglish.repository.auth.AdminAccountRepository;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.service.common.SmsSender;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clients;
    private final AdminAccountRepository admins;
    private final PasswordEncoder encoder;
    private final SmsSender sms;
    private final SecureRandom random = new SecureRandom();

    public boolean usernameAvailable(String username) {
        if (username == null || !username.matches("^[A-Za-z0-9]{5,50}$")) return false;
        return !clients.existsByUsername(username) && !admins.existsByUsername(username);
    }

    @Transactional
    public Client signup(SignupForm form) {
        String username = form.getUsername().trim();
        String phone = digits(form.getPhone());
        if (!usernameAvailable(username)) throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        if (!form.getPassword().equals(form.getPasswordConfirm())) throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
        if (clients.existsByPhone(phone)) throw new IllegalArgumentException("이미 가입된 휴대폰번호입니다.");

        Client c = new Client();
        c.setUsername(username);
        c.setPassword(encoder.encode(form.getPassword()));
        c.setName(form.getName().trim());
        c.setPhone(phone);
        c.setEmail(blankToNull(form.getEmail()));
        c.setBirthDate(form.getBirthDate());
        c.setPrivacyAgreed(true);
        c.setPrivacyAgreedAt(LocalDateTime.now());
        c.setStatus(ClientStatus.ACTIVE);
        return clients.save(c);
    }

    public void sendFoundUsername(String rawPhone) {
        String phone = digits(rawPhone);
        Client c = clients.findFirstByPhoneOrderByIdAsc(phone)
                .orElseThrow(() -> new IllegalArgumentException("일치하는 회원정보가 없습니다."));
        sms.send(phone, "[모니카김 영어] 회원님의 아이디는 " + c.getUsername() + " 입니다.");
    }

    @Transactional
    public void sendTemporaryPassword(String username, String rawPhone) {
        String phone = digits(rawPhone);
        Client c = clients.findByUsernameAndPhone(username == null ? "" : username.trim(), phone)
                .orElseThrow(() -> new IllegalArgumentException("아이디와 연락처가 일치하지 않습니다."));
        String temp = temporaryPassword();
        c.setPassword(encoder.encode(temp));
        clients.save(c);
        sms.send(phone, "[모니카김 영어] 임시 비밀번호는 " + temp + " 입니다. 로그인 후 반드시 변경해주세요.");
    }

    @Transactional
    public void updateProfile(Long clientId, String name, String phoneRaw, String email) {
        Client c = clients.findById(clientId).orElseThrow();
        String phone = digits(phoneRaw);
        clients.findFirstByPhoneOrderByIdAsc(phone).ifPresent(other -> {
            if (!other.getId().equals(clientId)) throw new IllegalArgumentException("이미 다른 회원이 사용 중인 휴대폰번호입니다.");
        });
        c.setName(name.trim());
        c.setPhone(phone);
        c.setEmail(blankToNull(email));
    }

    @Transactional
    public void changePassword(Long clientId, String current, String next, String confirm) {
        Client c = clients.findById(clientId).orElseThrow();
        if (!encoder.matches(current, c.getPassword())) throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        if (next == null || !next.matches("^(?=.*[A-Za-z])(?=.*[^A-Za-z0-9]).{8,}$")) throw new IllegalArgumentException("새 비밀번호는 영문과 특수문자를 포함해 8자 이상이어야 합니다.");
        if (!next.equals(confirm)) throw new IllegalArgumentException("새 비밀번호 확인이 일치하지 않습니다.");
        c.setPassword(encoder.encode(next));
    }

    public Client get(Long id) { return clients.findById(id).orElseThrow(); }
    public static String digits(String v) { return v == null ? "" : v.replaceAll("\\D", ""); }
    private String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private String temporaryPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        String specials = "!@#$%";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 9; i++) b.append(chars.charAt(random.nextInt(chars.length())));
        b.append(specials.charAt(random.nextInt(specials.length())));
        return b.toString();
    }
}
