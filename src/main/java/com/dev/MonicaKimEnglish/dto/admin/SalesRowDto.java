package com.dev.MonicaKimEnglish.dto.admin;

import java.math.BigDecimal;

public record SalesRowDto(Long lectureId, String title, long salesCount, BigDecimal revenue, long viewCount, long playSeconds) {
}
