package com.dev.MonicaKimEnglish.service.common;

public interface SmsSender {
    void send(String phone, String message);
}
