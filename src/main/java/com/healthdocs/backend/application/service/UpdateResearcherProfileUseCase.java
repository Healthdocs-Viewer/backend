package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateResearcherProfileUseCase {
    private final AppUserRepository users;
    private com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit;
    public UpdateResearcherProfileUseCase(AppUserRepository users) { this.users = users; }

    @Autowired
    public void setSecurityAuditEventPort(
            com.healthdocs.backend.application.port.out.SecurityAuditEventPort securityAudit) {
        this.securityAudit = securityAudit;
    }

    @Transactional
    public ResearcherDirectoryUseCase.ResearcherView execute(AuthenticatedUser actor, UUID userId,
            String fullName, String researcherCode) {
        ResearcherDirectoryUseCase.requireManager(actor);
        AppUserEntity user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.RESEARCHER) throw new IllegalArgumentException("User is not a researcher");
        String previousName = user.getFullName();
        String previousCode = user.getResearcherCode();
        user.updateResearcherProfile(fullName, researcherCode);
        AppUserEntity saved;
        try {
            saved = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessRuleViolationException("Researcher code is already in use");
        }
        if (securityAudit != null) {
            String transitionId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            if (!java.util.Objects.equals(previousName, saved.getFullName())) {
                securityAudit.record("ACCOUNT_PROFILE_UPDATED", now, actor.id(), userId, null,
                        null, null, null, "FULL_NAME", null, null,
                        "ACCOUNT_PROFILE_UPDATED:" + userId + ":" + transitionId + ":FULL_NAME");
            }
            if (!java.util.Objects.equals(previousCode, saved.getResearcherCode())) {
                securityAudit.record("ACCOUNT_PROFILE_UPDATED", now, actor.id(), userId, null,
                        null, null, null, "RESEARCHER_CODE", null, null,
                        "ACCOUNT_PROFILE_UPDATED:" + userId + ":" + transitionId + ":RESEARCHER_CODE");
            }
        }
        return new ResearcherDirectoryUseCase.ResearcherView(saved.getId(), saved.getResearcherCode(), saved.getFullName(),
                saved.getEmail(), saved.getAuthorizationStatus(), users.countAssignmentsByUserId(saved.getId()));
    }
}
