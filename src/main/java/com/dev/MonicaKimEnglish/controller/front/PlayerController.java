package com.dev.MonicaKimEnglish.controller.front;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.dev.MonicaKimEnglish.config.AppPrincipal;
import com.dev.MonicaKimEnglish.dto.learning.HeartbeatRequest;
import com.dev.MonicaKimEnglish.service.learning.LectureAccessService;
import com.dev.MonicaKimEnglish.service.learning.LectureEntryService;
import com.dev.MonicaKimEnglish.service.learning.ProgressService;
import com.dev.MonicaKimEnglish.service.learning.VideoPlaybackTicketService;
import com.dev.MonicaKimEnglish.service.lecture.LectureService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PlayerController {
    private final LectureAccessService access;
    private final LectureService lectures;
    private final ProgressService progress;
    private final VideoPlaybackTicketService playbackTickets;
    private final LectureEntryService lectureEntries;

    @GetMapping("/watch/{lectureId}")
    public String watch(
            @PathVariable Long lectureId,
            Authentication auth,
            Model model,
            @RequestParam(name = "entry", required = false) String entryToken,
            @RequestParam(name = "t", required = false) String position,
            HttpServletRequest request,
            HttpServletResponse response) {

        AppPrincipal principal = principal(auth);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0, private");
        if (!lectureEntries.consume(request.getSession(false), entryToken, principal.getAccountId(), lectureId)) {
            return lectureEntries.detailRedirect(lectureId, position);
        }
        var enrollment = access.requireAccess(principal.getAccountId(), lectureId);
        var introImages = lectures.introImages(lectureId);

        model.addAttribute("lecture", enrollment.getLecture());
        model.addAttribute("enrollment", enrollment);
        model.addAttribute("accessExpiresAt", enrollment.getExpiresAt());
        model.addAttribute("accessStartedAt", enrollment.getStartedAt());
        model.addAttribute("learningSummary", progress.learningSummary(principal.getAccountId(), lectureId));
        model.addAttribute("attachments", lectures.attachmentList(lectureId));
        model.addAttribute("posterImageId", introImages.isEmpty() ? null : introImages.get(0).getId());
        model.addAttribute("playerWatermark", principal.getUsername());

        // 수강 페이지 자체도 브라우저/프록시에 남지 않게 하고 iframe 캡처·임베딩을 막습니다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0, private");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Permissions-Policy", "display-capture=(), picture-in-picture=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");

        return "front/lecture/watch";
    }

    @PostMapping("/api/player/{lectureId}/session")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> session(
            @PathVariable Long lectureId,
            Authentication auth,
            HttpServletRequest request) {

        try {
            Long clientId = cid(auth);
            var started = progress.startSession(clientId, lectureId, request.getHeader("User-Agent"));
            String playbackUrl = issuePlaybackUrl(request.getSession(), clientId, lectureId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sessionKey", started.sessionKey());
            body.put("playbackUrl", playbackUrl);
            body.put("lastPositionSeconds", started.lastPositionSeconds());
            body.put("progressPercent", started.progressPercent());
            body.put("completed", started.completed());
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache").body(body);
        } catch (LectureAccessService.AccessDeniedException | IllegalArgumentException error) {
            return apiFailure(error);
        }
    }

    /**
     * 장시간 일시정지 등으로 재생 티켓이 만료된 경우 현재 로그인/수강권을 다시 검사한 뒤
     * 새 스트리밍 URL만 재발급합니다. 학습 세션/조회수는 새로 만들지 않습니다.
     */
    @PostMapping("/api/player/{lectureId}/ticket")
    @ResponseBody
    public ResponseEntity<?> ticket(
            @PathVariable Long lectureId,
            Authentication auth,
            HttpServletRequest request) {

        try {
            Long clientId = cid(auth);
            access.requireAccess(clientId, lectureId);
            String playbackUrl = issuePlaybackUrl(request.getSession(), clientId, lectureId);
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache").body(Map.of("playbackUrl", playbackUrl));
        } catch (LectureAccessService.AccessDeniedException | IllegalArgumentException error) {
            return apiFailure(error);
        }
    }

    @PostMapping("/api/player/{lectureId}/heartbeat")
    @ResponseBody
    public ResponseEntity<?> heartbeat(
            @PathVariable Long lectureId,
            @RequestBody HeartbeatRequest body,
            Authentication auth) {
        try {
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(progress.heartbeat(cid(auth), lectureId, body));
        } catch (LectureAccessService.AccessDeniedException | IllegalArgumentException error) {
            return apiFailure(error);
        }
    }

    private ResponseEntity<Map<String, Object>> apiFailure(RuntimeException error) {
        int status = error instanceof LectureAccessService.AccessDeniedException ? 403 : 400;
        return ResponseEntity.status(status).cacheControl(org.springframework.http.CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").body(Map.of("message", error.getMessage()));
    }

    private String issuePlaybackUrl(HttpSession session, Long clientId, Long lectureId) {
        if (session == null) {
            throw new IllegalStateException("로그인 세션을 확인할 수 없습니다.");
        }
        String token = playbackTickets.issue(session, clientId, lectureId);
        return "/secure-media/lectures/" + lectureId + "/video/" + token;
    }

    private Long cid(Authentication auth) {
        return principal(auth).getAccountId();
    }

    private AppPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AppPrincipal principal) || !"CLIENT".equals(principal.getRole())) {
            throw new LectureAccessService.AccessDeniedException("로그인이 필요합니다.");
        }
        return principal;
    }
}
