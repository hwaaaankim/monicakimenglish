package com.dev.MonicaKimEnglish.controller.front;

import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.dev.MonicaKimEnglish.config.AppPrincipal;
import com.dev.MonicaKimEnglish.dto.learning.BookmarkDto;
import com.dev.MonicaKimEnglish.dto.learning.LearningSummary;
import com.dev.MonicaKimEnglish.service.learning.BookmarkService;
import com.dev.MonicaKimEnglish.service.learning.LectureAccessService;
import com.dev.MonicaKimEnglish.service.learning.ProgressService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/player/{lectureId}")
public class LearningApiController {
    private final BookmarkService bookmarks;
    private final ProgressService progress;

    @GetMapping("/learning-summary")
    public ResponseEntity<LearningSummary> summary(@PathVariable Long lectureId, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(progress.learningSummary(cid(auth), lectureId));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<List<BookmarkDto>> list(@PathVariable Long lectureId, Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookmarks.list(cid(auth), lectureId));
    }

    @PostMapping("/bookmarks")
    public ResponseEntity<BookmarkDto> add(@PathVariable Long lectureId, Authentication auth, @RequestBody CreateBookmark body) {
        if (body == null || body.positionSeconds() == null) throw new IllegalArgumentException("책갈피 시간을 입력해주세요.");
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(bookmarks.add(cid(auth), lectureId, body.positionSeconds(), body.label()));
    }

    @PutMapping("/bookmarks/{bookmarkId}")
    public ResponseEntity<BookmarkDto> rename(@PathVariable Long lectureId, @PathVariable Long bookmarkId,
            Authentication auth, @RequestBody RenameBookmark body) {
        if (body == null) throw new IllegalArgumentException("책갈피 이름을 입력해주세요.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookmarks.rename(cid(auth), lectureId, bookmarkId, body.label()));
    }

    @DeleteMapping("/bookmarks/{bookmarkId}")
    public ResponseEntity<Void> remove(@PathVariable Long lectureId, @PathVariable Long bookmarkId, Authentication auth) {
        bookmarks.remove(cid(auth), lectureId, bookmarkId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(LectureAccessService.AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(LectureAccessService.AccessDeniedException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore()).body(Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("message", error.getMessage()));
    }

    private Long cid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AppPrincipal p) || !"CLIENT".equals(p.getRole())) {
            throw new LectureAccessService.AccessDeniedException("로그인이 필요합니다.");
        }
        return p.getAccountId();
    }

    public record CreateBookmark(Double positionSeconds, String label) { }
    public record RenameBookmark(String label) { }
}
