package com.dev.MonicaKimEnglish.controller.front;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dev.MonicaKimEnglish.config.AppPrincipal;
import com.dev.MonicaKimEnglish.repository.lecture.LectureAttachmentRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureIntroImageRepository;
import com.dev.MonicaKimEnglish.service.learning.LectureAccessService;
import com.dev.MonicaKimEnglish.service.learning.VideoPlaybackTicketService;
import com.dev.MonicaKimEnglish.service.lecture.LectureService;
import com.dev.MonicaKimEnglish.service.storage.FileStorageService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MediaController {
    private static final Logger log = LoggerFactory.getLogger(MediaController.class);
    private static final int STREAM_BUFFER_SIZE = 256 * 1024;

    private final LectureService lectures;
    private final LectureIntroImageRepository images;
    private final LectureAttachmentRepository attachments;
    private final LectureAccessService access;
    private final FileStorageService storage;
    private final VideoPlaybackTicketService playbackTickets;

    @GetMapping("/media/lectures/{lectureId}/images/{imageId}")
    public ResponseEntity<Resource> publicImage(
            @PathVariable Long lectureId,
            @PathVariable Long imageId,
            Authentication auth) {

        var lecture = lectures.get(lectureId);
        boolean admin = auth != null
                && auth.getPrincipal() instanceof AppPrincipal p
                && "ADMIN".equals(p.getRole());
        boolean clientAccess = auth != null
                && auth.getPrincipal() instanceof AppPrincipal p
                && "CLIENT".equals(p.getRole())
                && access.canWatch(p.getAccountId(), lectureId);

        if (lecture.getStatus() != com.dev.MonicaKimEnglish.model.enums.LectureStatus.PUBLISHED
                && !admin
                && !clientAccess) {
            throw new IllegalArgumentException("현재 공개되지 않은 이미지입니다.");
        }

        var img = images.findByIdAndLectureId(imageId, lectureId).orElseThrow();
        Resource resource = storage.resource(img.getFilePath());
        MediaType type = detect(img.getFilePath(), MediaType.IMAGE_JPEG);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)))
                .contentType(type)
                .body(resource);
    }

    @GetMapping("/secure-media/lectures/{lectureId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> attachment(
            @PathVariable Long lectureId,
            @PathVariable Long attachmentId,
            Authentication auth) {

        access.requireAccess(cid(auth), lectureId);
        var attachment = attachments.findByIdAndLectureId(attachmentId, lectureId).orElseThrow();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(attachment.getContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(attachment.getContentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(attachment.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(storage.resource(attachment.getFilePath()));
    }

    /**
     * 예전 고정 스트리밍 URL은 더 이상 영상 바이트를 제공하지 않습니다.
     * 로그인한 사용자가 주소를 직접 입력해도 파일 응답이 나오지 않도록 404로 종료합니다.
     */
    @RequestMapping(
            value = "/secure-media/lectures/{lectureId}/video",
            method = { RequestMethod.GET, RequestMethod.HEAD })
    @ResponseBody
    public ResponseEntity<Void> legacyDirectVideoUrl(@PathVariable Long lectureId, Authentication auth) {
        // 인증/수강권을 먼저 확인하여 다른 강의 ID 존재 여부를 비회원에게 노출하지 않습니다.
        access.requireAccess(cid(auth), lectureId);
        return ResponseEntity.notFound().build();
    }

    /**
     * HTML5 video 전용 byte-range 스트리밍.
     *
     * 필요한 조건:
     * 1) CLIENT 인증
     * 2) 현재 강의 수강 가능 상태
     * 3) 같은 HttpSession에서 발급된 임시 재생 티켓
     * 4) 브라우저의 일반 문서 탐색 요청이 아닌 media 요청
     *
     * Range의 open-ended / explicit / suffix(bytes=-N) 형식을 모두 올바르게 처리합니다.
     */
    @RequestMapping(
            value = "/secure-media/lectures/{lectureId}/video/{ticket}",
            method = { RequestMethod.GET, RequestMethod.HEAD })
    public void stream(
            @PathVariable Long lectureId,
            @PathVariable String ticket,
            Authentication auth,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        Long clientId = cid(auth);
        access.requireAccess(clientId, lectureId);

        HttpSession session = request.getSession(false);
        if (!playbackTickets.validateAndTouch(session, ticket, clientId, lectureId)) {
            respondNoStore(response);
            response.sendError(HttpStatus.FORBIDDEN.value());
            return;
        }

        if (!isMediaElementRequest(request)) {
            // 개발자도구에서 URL을 복사하여 새 탭/주소창으로 여는 일반 navigate 요청은 차단합니다.
            respondNoStore(response);
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }

        var lecture = lectures.get(lectureId);
        if (lecture.getVideoPath() == null || lecture.getVideoPath().isBlank()) {
            respondNoStore(response);
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }

        Path path = storage.resolve(lecture.getVideoPath());
        if (!Files.isRegularFile(path)) {
            respondNoStore(response);
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }

        long totalLength = Files.size(path);
        if (totalLength <= 0) {
            respondNoStore(response);
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }

        ByteRange range;
        try {
            range = ByteRange.parse(request.getHeader(HttpHeaders.RANGE), totalLength);
        } catch (IllegalArgumentException invalidRange) {
            respondNoStore(response);
            response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + totalLength);
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            return;
        }

        String mimeType = safeVideoMimeType(lecture.getVideoMimeType(), lecture.getVideoPath());
        long contentLength = range.end - range.start + 1;

        respondNoStore(response);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'; media-src 'self'");
        // 영상 응답에는 다운로드 파일명을 제공하지 않습니다. 브라우저 media element가 inline 스트림으로만 사용합니다.
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        response.setContentType(mimeType);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));

        if (range.partial) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.start + "-" + range.end + "/" + totalLength);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }

        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        response.setBufferSize(STREAM_BUFFER_SIZE);

        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
                OutputStream out = response.getOutputStream()) {

            file.seek(range.start);
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            long remaining = contentLength;

            while (remaining > 0) {
                int read = file.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
            out.flush();
        } catch (IOException e) {
            if (isClientAbort(e)) {
                log.debug("Video range connection closed by client. lectureId={}, range={}-{}",
                        lectureId, range.start, range.end);
                return;
            }
            throw e;
        }
    }

    private void respondNoStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0L);
    }

    private boolean isMediaElementRequest(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(fetchSite)) {
            return false;
        }

        String fetchMode = request.getHeader("Sec-Fetch-Mode");
        if ("navigate".equalsIgnoreCase(fetchMode)) {
            return false;
        }

        String fetchDest = request.getHeader("Sec-Fetch-Dest");
        if (fetchDest != null && !fetchDest.isBlank() && !"video".equalsIgnoreCase(fetchDest)) {
            return false;
        }

        // Fetch Metadata를 보내지 않는 브라우저(Safari 일부 버전 등)는 수강권+티켓 검증으로 허용합니다.
        return true;
    }

    private String safeVideoMimeType(String storedMimeType, String path) {
        // 브라우저/업로드 클라이언트가 application/octet-stream 등으로 보낸 경우가 있어
        // 실제 저장 확장자를 우선하여 HTML5 video가 해석할 수 있는 Content-Type을 보장합니다.
        String lower = path == null ? "" : path.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) {
            return "video/mp4";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        if (lower.endsWith(".ogv") || lower.endsWith(".ogg")) {
            return "video/ogg";
        }
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (storedMimeType != null && storedMimeType.toLowerCase().startsWith("video/")) {
            return storedMimeType;
        }
        return "video/mp4";
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "video/webm" -> ".webm";
            case "video/ogg" -> ".ogv";
            case "video/quicktime" -> ".mov";
            default -> ".mp4";
        };
    }

    private boolean isClientAbort(IOException error) {
        Throwable current = error;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.contains("ClientAbortException")
                    || (message != null && (message.contains("Broken pipe")
                    || message.contains("Connection reset by peer")
                    || message.contains("An established connection was aborted")))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Long cid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AppPrincipal principal)
                || !"CLIENT".equals(principal.getRole())) {
            throw new LectureAccessService.AccessDeniedException("로그인이 필요합니다.");
        }
        return principal.getAccountId();
    }

    private MediaType detect(String path, MediaType fallback) {
        try {
            String type = Files.probeContentType(storage.resolve(path));
            return type == null ? fallback : MediaType.parseMediaType(type);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static final class ByteRange {
        private final long start;
        private final long end;
        private final boolean partial;

        private ByteRange(long start, long end, boolean partial) {
            this.start = start;
            this.end = end;
            this.partial = partial;
        }

        private static ByteRange parse(String header, long totalLength) {
            if (header == null || header.isBlank()) {
                return new ByteRange(0, totalLength - 1, false);
            }
            if (!header.startsWith("bytes=")) {
                throw new IllegalArgumentException("Unsupported range unit");
            }

            String value = header.substring("bytes=".length()).trim();
            if (value.isBlank() || value.contains(",")) {
                // HTML5 video는 단일 range만 사용합니다. multipart ranges는 의도적으로 받지 않습니다.
                throw new IllegalArgumentException("Multiple/empty ranges are not supported");
            }

            int dash = value.indexOf('-');
            if (dash < 0) {
                throw new IllegalArgumentException("Invalid range");
            }

            String startPart = value.substring(0, dash).trim();
            String endPart = value.substring(dash + 1).trim();

            try {
                if (startPart.isEmpty()) {
                    // suffix range: bytes=-500 => 파일의 마지막 500바이트
                    if (endPart.isEmpty()) {
                        throw new IllegalArgumentException("Invalid suffix range");
                    }
                    long suffixLength = Long.parseLong(endPart);
                    if (suffixLength <= 0) {
                        throw new IllegalArgumentException("Invalid suffix length");
                    }
                    suffixLength = Math.min(suffixLength, totalLength);
                    return new ByteRange(totalLength - suffixLength, totalLength - 1, true);
                }

                long start = Long.parseLong(startPart);
                if (start < 0 || start >= totalLength) {
                    throw new IllegalArgumentException("Range start out of bounds");
                }

                long end = endPart.isEmpty() ? totalLength - 1 : Long.parseLong(endPart);
                if (end < start) {
                    throw new IllegalArgumentException("Range end before start");
                }
                end = Math.min(end, totalLength - 1);

                return new ByteRange(start, end, true);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid numeric range", e);
            }
        }
    }
}
