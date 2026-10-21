
package com.chargeshare.backend;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HostAccountRepository
        extends JpaRepository<HostAccount, Long> {

    Optional<HostAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
