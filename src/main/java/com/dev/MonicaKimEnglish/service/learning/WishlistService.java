package com.dev.MonicaKimEnglish.service.learning;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.MonicaKimEnglish.model.learning.Wishlist;
import com.dev.MonicaKimEnglish.repository.auth.ClientRepository;
import com.dev.MonicaKimEnglish.repository.learning.EnrollmentRepository;
import com.dev.MonicaKimEnglish.repository.learning.WishlistRepository;
import com.dev.MonicaKimEnglish.repository.lecture.LectureRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WishlistService {
    private final WishlistRepository wishlists;
    private final EnrollmentRepository enrollments;
    private final ClientRepository clients;
    private final LectureRepository lectures;

    @Transactional
    public boolean toggle(Long clientId, Long lectureId) {
        var existing = wishlists.findByClientIdAndLectureId(clientId, lectureId);
        if (existing.isPresent()) { wishlists.delete(existing.get()); return false; }
        if (!enrollments.findHistory(clientId, lectureId).isEmpty()) throw new IllegalArgumentException("이미 수강권이 있는 영상은 찜할 수 없습니다.");
        Wishlist w = new Wishlist(); w.setClient(clients.findById(clientId).orElseThrow()); w.setLecture(lectures.findById(lectureId).orElseThrow()); w.setCreatedAt(LocalDateTime.now()); wishlists.save(w); return true;
    }

    @Transactional
    public void removeIfExists(Long clientId, Long lectureId) { wishlists.deleteByClientIdAndLectureId(clientId, lectureId); }
    public List<Wishlist> list(Long clientId) { return wishlists.findForClient(clientId); }
}
