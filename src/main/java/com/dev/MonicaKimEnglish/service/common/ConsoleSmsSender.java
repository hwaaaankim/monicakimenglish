package com.dev.MonicaKimEnglish.service.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsoleSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);
    @Override
    public void send(String phone, String message) {
        log.info("[MOCK-SMS] to={} message={}", mask(phone), message);
    }
    private String mask(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
