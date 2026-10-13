package com.dev.MonicaKimEnglish.repository.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.dev.MonicaKimEnglish.model.auth.Client;

public interface ClientRepository extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {
    /** 동일 회원의 결제, 무료 신청 및 책갈피 생성 요청을 직렬화합니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where c.id = :id")
    Optional<Client> findByIdForUpdate(@Param("id") Long id);
    Optional<Client> findByUsername(String username);
    Optional<Client> findFirstByPhoneOrderByIdAsc(String phone);
    Optional<Client> findByUsernameAndPhone(String username, String phone);
    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);
}
