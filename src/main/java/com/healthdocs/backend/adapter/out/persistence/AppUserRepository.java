package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import com.healthdocs.backend.domain.model.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByEmailIgnoreCase(String email);

    @Query("select authIdentity.user from AuthIdentityEntity authIdentity "
            + "where authIdentity.provider = :provider and authIdentity.issuer = :issuer "
            + "and authIdentity.subject = :subject")
    Optional<AppUserEntity> findByAuthIdentity(
            @Param("provider") String provider,
            @Param("issuer") String issuer,
            @Param("subject") String subject);

    List<AppUserEntity> findByEnabledTrueAndRoleInOrderByEmailAsc(List<Role> roles);
}
