package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import java.util.UUID;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeResearcherAuthorizationUseCase {
    private final AppUserRepository users;
    private com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit;
    public ChangeResearcherAuthorizationUseCase(AppUserRepository users) { this.users = users; }

    @Autowired
    public void setSecurityAuditEventPort(
            com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit) {
        this.securityAudit = securityAudit;
    }

    @Transactional
    public ResearcherDirectoryUseCase.ResearcherView execute(AuthenticatedUser actor, UUID userId,
            ResearcherAuthorizationStatus status) {
        ResearcherDirectoryUseCase.requireManager(actor);
        if (userId == null || status == null) throw new IllegalArgumentException("User ID and authorization status are required");
        AppUserEntity user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ResearcherAuthorizationStatus previousStatus = user.getAuthorizationStatus();
        user.changeAuthorizationStatus(status);
        AppUserEntity saved = users.save(user);
        if (previousStatus != status && securityAudit != null) {
            securityAudit.record("RESEARCHER_AUTHORIZATION_CHANGED", Instant.now(), actor.id(), userId, null,
                    null, null, null, null, previousStatus == null ? "UNSET" : previousStatus.name(), status.name(),
                    "RESEARCHER_AUTHORIZATION_CHANGED:" + userId + ":" + UUID.randomUUID());
        }
        return new ResearcherDirectoryUseCase.ResearcherView(saved.getId(), saved.getResearcherCode(), saved.getFullName(),
                saved.getEmail(), saved.getAuthorizationStatus(), users.countAssignmentsByUserId(saved.getId()));
    }
}
