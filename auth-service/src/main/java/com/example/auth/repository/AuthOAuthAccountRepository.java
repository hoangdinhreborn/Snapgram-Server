package com.example.auth.repository;

import com.example.auth.entity.AuthOAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthOAuthAccountRepository extends JpaRepository<AuthOAuthAccount, UUID> {

    Optional<AuthOAuthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<AuthOAuthAccount> findByUserIdAndProvider(UUID userId, String provider);
}
