package com.dev.MonicaKimEnglish.repository.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dev.MonicaKimEnglish.model.auth.AdminAccount;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByUsername(String username);
    boolean existsByUsername(String username);
}
