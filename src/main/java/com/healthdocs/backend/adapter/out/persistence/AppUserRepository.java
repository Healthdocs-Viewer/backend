package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUserEntity u where u.id = :userId")
    Optional<AppUserEntity> findByIdForCaseCreation(@Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUserEntity u where u.id = :userId")
    Optional<AppUserEntity> findByIdForUpdate(@Param("userId") UUID userId);

    Optional<AppUserEntity> findByEmailIgnoreCase(String email);

    @Query("select authIdentity.user from AuthIdentityEntity authIdentity "
            + "where authIdentity.provider = :provider and authIdentity.issuer = :issuer "
            + "and authIdentity.subject = :subject")
    Optional<AppUserEntity> findByAuthIdentity(
            @Param("provider") String provider,
            @Param("issuer") String issuer,
            @Param("subject") String subject);

    List<AppUserEntity> findByEnabledTrueAndRoleInOrderByEmailAsc(List<Role> roles);

    List<AppUserEntity> findByRole(Role role);

    @Query("select count(u) from AppUserEntity u where u.role = :role and u.authorizationStatus = :status")
    long countResearchersByStatus(@Param("role") Role role, @Param("status") ResearcherAuthorizationStatus status);

    @Query("select count(a) from CaseAssignmentEntity a where a.userId = :userId")
    long countAssignmentsByUserId(@Param("userId") UUID userId);

    @Query("select count(a) from CaseAssignmentEntity a join AppUserEntity u on u.id = a.userId where u.role = :role")
    long countAssignmentsForRole(@Param("role") Role role);

    @Query("select a.userId, count(a) from CaseAssignmentEntity a group by a.userId")
    List<Object[]> countAssignmentsByUser();
}
