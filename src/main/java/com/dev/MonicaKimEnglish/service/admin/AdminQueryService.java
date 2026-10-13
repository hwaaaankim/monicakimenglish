package com.dev.MonicaKimEnglish.service.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.stereotype.Service;

import com.dev.MonicaKimEnglish.dto.admin.BuyerRowDto;
import com.dev.MonicaKimEnglish.dto.admin.SalesRowDto;
import com.dev.MonicaKimEnglish.model.enums.PaymentStatus;
import com.dev.MonicaKimEnglish.repository.learning.LectureProgressRepository;
import com.dev.MonicaKimEnglish.repository.payment.PaymentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminQueryService {
    private final PaymentRepository payments;
    private final LectureProgressRepository progresses;
    @PersistenceContext private EntityManager em;

    public List<SalesRowDto> salesRows(LocalDateTime from, LocalDateTime to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
            SELECT l.id, l.title,
                   COUNT(DISTINCT p.id) AS sales_count,
                   COALESCE(SUM(p.amount - COALESCE(p.refunded_amount, 0)), 0) AS revenue,
                   COALESCE((SELECT COUNT(v.id) FROM lecture_view_session v WHERE v.lecture_id=l.id AND v.started_at BETWEEN :from AND :to),0) AS view_count,
                   COALESCE((SELECT SUM(v.watched_seconds) FROM lecture_view_session v WHERE v.lecture_id=l.id AND v.started_at BETWEEN :from AND :to),0) AS play_seconds
              FROM lecture l
              LEFT JOIN payment p ON p.lecture_id=l.id AND p.status IN ('PAID','PARTIALLY_REFUNDED') AND p.provider='TOSS' AND p.approved_at BETWEEN :from AND :to
             GROUP BY l.id, l.title
             ORDER BY revenue DESC, sales_count DESC, view_count DESC
            """).setParameter("from", from).setParameter("to", to).getResultList();
        return rows.stream().map(r -> new SalesRowDto(((Number)r[0]).longValue(), String.valueOf(r[1]), ((Number)r[2]).longValue(),
                r[3] == null ? BigDecimal.ZERO : new BigDecimal(r[3].toString()), ((Number)r[4]).longValue(), ((Number)r[5]).longValue())).toList();
    }

    public List<BuyerRowDto> buyers(Long lectureId) {
        return payments.findActiveBuyersForLecture(lectureId).stream().map(p -> {
            var progress = progresses.findByClientIdAndLectureId(p.getClient().getId(), lectureId).orElse(null);
            return new BuyerRowDto(p.getClient().getId(), p.getClient().getUsername(), p.getClient().getName(), p.getClient().getPhone(), p.getNetAmount(), p.getApprovedAt(),
                    progress == null ? 0 : progress.getProgressPercent(), progress == null ? 0 : progress.getWatchedSeconds(), progress == null ? 0 : progress.getViewCount());
        }).toList();
    }
}
