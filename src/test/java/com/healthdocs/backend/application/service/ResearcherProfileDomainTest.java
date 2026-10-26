package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import com.healthdocs.backend.domain.model.Role;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;

class ResearcherProfileDomainTest {
    @Test
    void assigningResearcherRoleAuthorizesOnceAndRepeatedRoleDoesNotUndoRestriction() {
        AppUserEntity user = new AppUserEntity("subject", "researcher@example.test", Role.PATIENT);

        user.changeRole(Role.RESEARCHER);
        assertEquals(ResearcherAuthorizationStatus.AUTHORIZED, user.getAuthorizationStatus());

        user.changeAuthorizationStatus(ResearcherAuthorizationStatus.RESTRICTED);
        user.changeRole(Role.RESEARCHER);
        assertEquals(ResearcherAuthorizationStatus.RESTRICTED, user.getAuthorizationStatus());
    }

    @Test
    void profileNormalizesCodeWithoutChangingAccountIdentity() {
        AppUserEntity user = new AppUserEntity("subject", "researcher@example.test", Role.RESEARCHER);
        user.updateResearcherProfile("  Ana Ruiz  ", " est-001 ");

        assertEquals("Ana Ruiz", user.getFullName());
        assertEquals("EST-001", user.getResearcherCode());
        assertEquals("subject", user.getExternalSubject());
    }

    @Test
    void enabledCoordinatorCanAssignResearcherRoleAndReturnsAuthorization() {
        AppUserRepository repository = mock(AppUserRepository.class);
        ChangeUserRoleUseCase useCase = new ChangeUserRoleUseCase(repository);
        UUID targetId = UUID.randomUUID();
        AppUserEntity target = mock(AppUserEntity.class);
        when(repository.findByIdForUpdate(targetId)).thenReturn(Optional.of(target));
        when(repository.save(target)).thenReturn(target);
        when(target.getId()).thenReturn(targetId);
        when(target.getEmail()).thenReturn("new@example.test");
        when(target.getRole()).thenReturn(Role.RESEARCHER);
        when(target.isEnabled()).thenReturn(true);
        when(target.getAuthorizationStatus()).thenReturn(ResearcherAuthorizationStatus.AUTHORIZED);

        var result = useCase.execute(new AuthenticatedUser(UUID.randomUUID(), "coord@example.test", Role.COORDINATOR, true),
                targetId, Role.RESEARCHER);

        verify(target).changeRole(Role.RESEARCHER);
        assertEquals(ResearcherAuthorizationStatus.AUTHORIZED, result.authorizationStatus());
    }
}
