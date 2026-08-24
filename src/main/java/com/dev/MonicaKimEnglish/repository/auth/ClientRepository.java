package com.dev.MonicaKimEnglish.repository.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.dev.MonicaKimEnglish.model.auth.Client;

public interface ClientRepository extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {
    Optional<Client> findByUsername(String username);
    Optional<Client> findFirstByPhoneOrderByIdAsc(String phone);
    Optional<Client> findByUsernameAndPhone(String username, String phone);
    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);
}
