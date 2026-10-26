package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.util.UUID;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeUserRoleUseCase {

    private final AppUserRepository users;
    private com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit;

    public ChangeUserRoleUseCase(AppUserRepository users) {
        this.users = users;
    }

    @Autowired
    public void setSecurityAuditEventPort(
            com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit) {
        this.securityAudit = securityAudit;
    }

    @Transactional
    public UserRoleView execute(AuthenticatedUser administrator, UUID userId, Role role) {
        ResearcherDirectoryUseCase.requireManager(administrator);
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (role == null || role == Role.ADMIN) {
            throw new IllegalArgumentException("Role must be patient, researcher, or coordinator");
        }
        if (administrator.id().equals(userId)) {
            throw new IllegalArgumentException("Users cannot change their own role");
        }

        AppUserEntity user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Role previousRole = user.getRole();
        user.changeRole(role);
        AppUserEntity updated = users.save(user);
        if (previousRole != role && securityAudit != null) {
            Instant now = Instant.now();
            securityAudit.record("ACCOUNT_ROLE_CHANGED", now, administrator.id(), userId, null,
                    null, null, null, null, previousRole.name(), role.name(),
                    "ACCOUNT_ROLE_CHANGED:" + userId + ":" + UUID.randomUUID());
        }
        return new UserRoleView(updated.getId(), updated.getEmail(), updated.getRole().value(), updated.isEnabled(),
                updated.getFullName(), updated.getResearcherCode(), updated.getAuthorizationStatus());
    }

    public record UserRoleView(UUID userId, String email, String role, boolean enabled, String fullName,
            String researcherCode, com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus authorizationStatus) {
    }
}
