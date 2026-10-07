package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.AuthIdentityEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentityEntity, UUID> {

    Optional<AuthIdentityEntity> findByProviderAndIssuerAndSubject(
            String provider,
            String issuer,
            String subject);

    List<AuthIdentityEntity> findByUser_Id(UUID userId);
}
