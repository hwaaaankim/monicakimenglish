package com.dev.MonicaKimEnglish.service.learning;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dev.MonicaKimEnglish.dto.learning.BookmarkDto;
import com.dev.MonicaKimEnglish.model.learning.LectureBookmark;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.repository.learning.LectureBookmarkRepository;
import com.dev.MonicaKimEnglish.repository.learning.LectureProgressRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {
    public static final int MAX_BOOKMARKS = 5;
    private final LectureAccessService access;
    private final LectureBookmarkRepository bookmarks;
    private final ClientRepository clients;
    private final LectureProgressRepository progresses;

    /** 저장된 학습 위치에 한해 프레임을 제공하여 임의의 전체 영상 썸네일 추출을 제한합니다. */
    public boolean canReadFrame(Long clientId, Long lectureId, long positionSeconds) {
        if (positionSeconds == 0) return true;
        if (progresses.findByClientIdAndLectureId(clientId, lectureId)
                .map(p -> p.getLastPositionSeconds() == positionSeconds).orElse(false)) return true;
        return bookmarks.findByClientIdAndLectureIdOrderByPositionSecondsAsc(clientId, lectureId).stream()
                .anyMatch(b -> b.getPositionSeconds() == positionSeconds);
    }

    public List<BookmarkDto> list(Long clientId, Long lectureId) {
        access.requireOwnedEnrollment(clientId, lectureId);
        return bookmarks.findByClientIdAndLectureIdOrderByPositionSecondsAsc(clientId, lectureId).stream().map(this::toDto).toList();
    }

    @Transactional
    public BookmarkDto add(Long clientId, Long lectureId, double positionSeconds, String label) {
        // 빈 목록은 잠글 행이 없으므로 회원 행을 잠가 동시 6번째 등록을 차단합니다.
        var client = clients.findByIdForUpdate(clientId).orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        var enrollment = access.requireAccess(clientId, lectureId);
        long duration = enrollment.getLecture().getDurationSeconds();
        if (!Double.isFinite(positionSeconds) || positionSeconds < 0 || duration <= 0 || positionSeconds > duration) {
            throw new IllegalArgumentException("책갈피 시간은 영상 재생 범위 안에 있어야 합니다.");
        }
        long position = Math.min(duration, Math.round(positionSeconds));
        var current = bookmarks.findByClientIdAndLectureIdOrderByPositionSecondsAsc(clientId, lectureId);
        if (current.stream().anyMatch(b -> Math.abs(b.getPositionSeconds() - position) < 2)) {
            throw new IllegalArgumentException("현재 위치 근처에 이미 책갈피가 있습니다.");
        }
        if (current.size() >= MAX_BOOKMARKS) throw new IllegalArgumentException("책갈피는 강의당 최대 5개까지 저장할 수 있습니다.");
        LectureBookmark bookmark = new LectureBookmark();
        bookmark.setClient(client);
        bookmark.setLecture(enrollment.getLecture());
        bookmark.setPositionSeconds(position);
        bookmark.setLabel(validateLabel(label));
        return toDto(bookmarks.saveAndFlush(bookmark));
    }

    @Transactional
    public BookmarkDto rename(Long clientId, Long lectureId, Long bookmarkId, String label) {
        access.requireAccess(clientId, lectureId);
        var bookmark = owned(clientId, lectureId, bookmarkId);
        bookmark.setLabel(validateLabel(label));
        return toDto(bookmark);
    }

    @Transactional
    public void remove(Long clientId, Long lectureId, Long bookmarkId) {
        access.requireAccess(clientId, lectureId);
        bookmarks.delete(owned(clientId, lectureId, bookmarkId));
    }

    private LectureBookmark owned(Long clientId, Long lectureId, Long bookmarkId) {
        return bookmarks.findByIdAndClientIdAndLectureId(bookmarkId, clientId, lectureId)
                .orElseThrow(() -> new IllegalArgumentException("책갈피를 찾을 수 없습니다."));
    }

    static String validateLabel(String label) {
        String clean = label == null || label.isBlank() ? "책갈피" : label.trim();
        if (clean.length() > 60 || clean.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("책갈피 이름은 줄바꿈 없이 60자 이내로 입력해주세요.");
        }
        return clean;
    }

    private BookmarkDto toDto(LectureBookmark bookmark) {
        return new BookmarkDto(bookmark.getId(), bookmark.getPositionSeconds(), bookmark.getLabel(),
                frameUrl(bookmark.getLecture().getId(), bookmark.getPositionSeconds()), bookmark.getCreatedAt());
    }

    public static String frameUrl(Long lectureId, long positionSeconds) {
        return "/secure-media/lectures/" + lectureId + "/frames/" + positionSeconds;
    }
}
