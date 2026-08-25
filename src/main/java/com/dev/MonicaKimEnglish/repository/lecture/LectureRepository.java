package com.dev.MonicaKimEnglish.repository.lecture;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.MonicaKimEnglish.model.lecture.Lecture;

public interface LectureRepository extends JpaRepository<Lecture, Long>, JpaSpecificationExecutor<Lecture> {

    /**
     * Hibernate 6은 @Lob String(LONGTEXT/CLOB)에 CriteriaBuilder.lower()를 적용하면
     * "lower() expects STRING but argument is mapped to CLOB" 타입 검증 예외를 발생시킵니다.
     *
     * 실제 운영 DB(MariaDB)에서는 LOWER(LONGTEXT)가 정상 동작하므로 키워드 매칭만
     * native SQL에서 수행하고, 가격/태그/정렬 등의 나머지 조건은 기존 Specification을
     * 그대로 사용합니다. 이 방식은 description을 VARCHAR로 축소하지 않으면서도
     * 한글/영문 단어 검색을 모두 유지합니다.
     */
    @Query(value = """
            SELECT l.id
              FROM lecture l
             WHERE l.status = 'PUBLISHED'
               AND (
                    LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(l.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
               )
            """, nativeQuery = true)
    List<Long> findPublishedIdsByKeyword(@Param("keyword") String keyword);
}
