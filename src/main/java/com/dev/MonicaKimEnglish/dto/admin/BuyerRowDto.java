package com.dev.MonicaKimEnglish.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BuyerRowDto(Long clientId, String username, String name, String phone, BigDecimal paidAmount,
                          LocalDateTime paidAt, int progressPercent, long watchedSeconds, long viewCount) {
}
