package com.dev.MonicaKimEnglish.repository.learning;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dev.MonicaKimEnglish.model.learning.Wishlist;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    @Query("select w from Wishlist w join fetch w.lecture where w.client.id = :clientId order by w.createdAt desc")
    List<Wishlist> findForClient(@Param("clientId") Long clientId);
    Optional<Wishlist> findByClientIdAndLectureId(Long clientId, Long lectureId);
    boolean existsByClientIdAndLectureId(Long clientId, Long lectureId);
    void deleteByClientIdAndLectureId(Long clientId, Long lectureId);
}
