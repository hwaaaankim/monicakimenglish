package com.dev.MonicaKimEnglish.repository.lecture;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dev.MonicaKimEnglish.model.lecture.LectureTag;

public interface LectureTagRepository extends JpaRepository<LectureTag, Long> {
    List<LectureTag> findByLectureIdOrderByNameAsc(Long lectureId);
    void deleteByLectureId(Long lectureId);

    @Query("select distinct t.name from LectureTag t where t.lecture.status = com.dev.MonicaKimEnglish.model.enums.LectureStatus.PUBLISHED order by t.name")
    List<String> findPublishedTagNames();

    @Query("select t.lecture.id from LectureTag t where lower(t.name) = lower(:name)")
    List<Long> findLectureIdsByName(@Param("name") String name);
}
