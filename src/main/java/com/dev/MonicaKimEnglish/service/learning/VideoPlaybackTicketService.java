package com.dev.MonicaKimEnglish.service.learning;

import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;

/**
 * HTML5 video 전용 재생 티켓을 HttpSession에 보관합니다.
 *
 * 공개/고정 URL만으로 영상 파일을 내려받지 못하게 하고,
 * 로그인 세션 + 수강권 + 현재 브라우저 세션에서 발급한 임시 티켓을 모두 만족해야
 * 실제 byte-range 스트리밍이 가능하도록 하는 보조 계층입니다.
 *
 * 주의: 브라우저에서 실제로 재생되는 영상은 결국 클라이언트에 바이트가 전달되므로
 * DRM 없이 100% 추출을 막을 수는 없습니다. 이 서비스는 URL 공유/직접 접근/단순 다운로드의
 * 난이도를 높이는 용도입니다.
 */
@Service
public class VideoPlaybackTicketService {

    private static final String SESSION_ATTRIBUTE =
            VideoPlaybackTicketService.class.getName() + ".PLAYBACK_GRANTS";

    private static final long ABSOLUTE_TTL_MILLIS = Duration.ofHours(6).toMillis();
    private static final long IDLE_TTL_MILLIS = Duration.ofMinutes(20).toMillis();
    private static final int MAX_GRANTS_PER_SESSION = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(HttpSession session, Long clientId, Long lectureId) {
        if (session == null || clientId == null || lectureId == null) {
            throw new IllegalArgumentException("재생 티켓을 발급할 수 없습니다.");
        }

        long now = System.currentTimeMillis();
        String token = randomToken();

        synchronized (session) {
            Map<String, PlaybackGrant> grants = grants(session);
            cleanupExpired(grants, now);

            // 같은 브라우저 세션에서 같은 강의의 이전 URL은 즉시 폐기합니다.
            grants.entrySet().removeIf(entry -> {
                PlaybackGrant grant = entry.getValue();
                return clientId.equals(grant.clientId) && lectureId.equals(grant.lectureId);
            });

            while (grants.size() >= MAX_GRANTS_PER_SESSION) {
                String oldestKey = grants.entrySet().stream()
                        .min((a, b) -> Long.compare(a.getValue().lastSeenAt, b.getValue().lastSeenAt))
                        .map(Map.Entry::getKey)
                        .orElse(null);
                if (oldestKey == null) {
                    break;
                }
                grants.remove(oldestKey);
            }

            grants.put(token, new PlaybackGrant(clientId, lectureId, now, now));
            session.setAttribute(SESSION_ATTRIBUTE, grants);
        }

        return token;
    }

    public boolean validateAndTouch(HttpSession session, String token, Long clientId, Long lectureId) {
        if (session == null || token == null || token.isBlank() || clientId == null || lectureId == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        synchronized (session) {
            Map<String, PlaybackGrant> grants = grants(session);
            cleanupExpired(grants, now);

            PlaybackGrant grant = grants.get(token);
            if (grant == null) {
                return false;
            }
            if (!clientId.equals(grant.clientId) || !lectureId.equals(grant.lectureId)) {
                grants.remove(token);
                return false;
            }
            if (isExpired(grant, now)) {
                grants.remove(token);
                return false;
            }

            grant.lastSeenAt = now;
            session.setAttribute(SESSION_ATTRIBUTE, grants);
            return true;
        }
    }

    public void revoke(HttpSession session, String token) {
        if (session == null || token == null || token.isBlank()) {
            return;
        }
        synchronized (session) {
            Map<String, PlaybackGrant> grants = grants(session);
            grants.remove(token);
            session.setAttribute(SESSION_ATTRIBUTE, grants);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, PlaybackGrant> grants(HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, PlaybackGrant>) raw;
        }
        return new HashMap<>();
    }

    private void cleanupExpired(Map<String, PlaybackGrant> grants, long now) {
        Iterator<Map.Entry<String, PlaybackGrant>> iterator = grants.entrySet().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue(), now)) {
                iterator.remove();
            }
        }
    }

    private boolean isExpired(PlaybackGrant grant, long now) {
        return now - grant.issuedAt > ABSOLUTE_TTL_MILLIS
                || now - grant.lastSeenAt > IDLE_TTL_MILLIS;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class PlaybackGrant implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Long clientId;
        private final Long lectureId;
        private final long issuedAt;
        private long lastSeenAt;

        private PlaybackGrant(Long clientId, Long lectureId, long issuedAt, long lastSeenAt) {
            this.clientId = clientId;
            this.lectureId = lectureId;
            this.issuedAt = issuedAt;
            this.lastSeenAt = lastSeenAt;
        }
    }
}
