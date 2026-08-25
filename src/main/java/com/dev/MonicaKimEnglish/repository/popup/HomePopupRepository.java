package com.dev.MonicaKimEnglish.repository.popup;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.MonicaKimEnglish.model.popup.HomePopup;

public interface HomePopupRepository extends JpaRepository<HomePopup, Long> {

    List<HomePopup> findAllByOrderByCreatedAtDescIdDesc();

    @Query("""
            select p
              from HomePopup p
             where (p.startDate is null or p.startDate <= :today)
               and (p.endDate is null or p.endDate >= :today)
             order by p.createdAt desc, p.id desc
            """)
    List<HomePopup> findActive(@Param("today") LocalDate today);
}
