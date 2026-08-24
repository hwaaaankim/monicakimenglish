package com.dev.MonicaKimEnglish.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.MonicaKimEnglish.model.auth.Client;

@Repository
public interface ClientRepository extends JpaRepository<Client,Long>{

}
