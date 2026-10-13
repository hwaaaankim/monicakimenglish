package com.dev.MonicaKimEnglish.service.learning;

import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;

/** 강의 설명 화면에서 발급한 1회용 입장권으로만 재생 화면을 열도록 합니다. */
@Service
public class LectureEntryService {
    private static final String ATTRIBUTE = LectureEntryService.class.getName() + ".ENTRIES";
    private static final long TTL_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final int MAX_ENTRIES = 64;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public LectureEntryService() {
        this(Clock.systemUTC());
    }

    LectureEntryService(Clock clock) {
        this.clock = clock;
    }

    public String issue(HttpSession session, Long clientId, Long lectureId) {
        if (session == null || clientId == null || lectureId == null) {
            throw new IllegalArgumentException("강의 입장 정보를 확인할 수 없습니다.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        synchronized (session) {
            Map<String, Entry> entries = entries(session);
            cleanup(entries);
            while (entries.size() >= MAX_ENTRIES) {
                entries.remove(entries.keySet().iterator().next());
            }
            // 같은 강의를 여러 탭에서 열어도 각 화면의 입장권을 유지합니다.
            entries.put(token, new Entry(clientId, lectureId, clock.millis()));
            session.setAttribute(ATTRIBUTE, entries);
        }
        return token;
    }

    public boolean consume(HttpSession session, String token, Long clientId, Long lectureId) {
        if (session == null || token == null || token.length() != 43 || clientId == null || lectureId == null) {
            return false;
        }
        synchronized (session) {
            Map<String, Entry> entries = entries(session);
            cleanup(entries);
            Entry entry = entries.get(token);
            boolean valid = entry != null && clientId.equals(entry.clientId()) && lectureId.equals(entry.lectureId());
            if (valid) entries.remove(token);
            session.setAttribute(ATTRIBUTE, entries);
            return valid;
        }
    }

    /** 과거 시청 URL의 이어보기 위치는 보존하되 임의 쿼리나 입장권은 전달하지 않습니다. */
    public String detailRedirect(Long lectureId, String position) {
        String destination = "redirect:/lectures/" + lectureId;
        if (position == null || position.isBlank()) return destination;
        try {
            double seconds = Double.parseDouble(position);
            if (Double.isFinite(seconds) && seconds >= 0 && seconds <= Integer.MAX_VALUE) {
                return destination + "?t=" + (long) seconds;
            }
        } catch (NumberFormatException ignored) {
            // 잘못된 재생 위치가 강의 설명 화면 진입까지 막지 않게 합니다.
        }
        return destination;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Entry> entries(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        return value instanceof Map<?, ?> ? (Map<String, Entry>) value : new LinkedHashMap<>();
    }

    private void cleanup(Map<String, Entry> entries) {
        long now = clock.millis();
        entries.values().removeIf(entry -> now < entry.issuedAt() || now - entry.issuedAt() >= TTL_MILLIS);
    }

    private record Entry(Long clientId, Long lectureId, long issuedAt) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
