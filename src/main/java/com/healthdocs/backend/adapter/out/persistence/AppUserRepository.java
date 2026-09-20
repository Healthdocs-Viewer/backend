package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import com.healthdocs.backend.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByEmailIgnoreCase(String email);

    List<AppUserEntity> findByEnabledTrueAndRoleInOrderByEmailAsc(List<Role> roles);
}
