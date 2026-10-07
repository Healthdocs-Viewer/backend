package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListInternalUsersUseCaseTest {
    private final AppUserRepository repository = mock(AppUserRepository.class);
    private final ListInternalUsersUseCase useCase = new ListInternalUsersUseCase(repository);

    @Test
    void returnsOnlyRepositorySelectedEnabledInternalUsersWithoutSensitiveFields() {
        UUID id = UUID.randomUUID();
        AppUserEntity user = mock(AppUserEntity.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn("student@healthdocs.test");
        when(user.getRole()).thenReturn(Role.RESEARCHER);
        when(repository.findByEnabledTrueAndRoleInOrderByEmailAsc(List.of(Role.RESEARCHER, Role.COORDINATOR)))
                .thenReturn(List.of(user));

        var result = useCase.execute(new AuthenticatedUser(UUID.randomUUID(), "professor@healthdocs.test", Role.COORDINATOR, true));

        assertEquals(List.of(new ListInternalUsersUseCase.InternalUser(id, "student@healthdocs.test", Role.RESEARCHER)), result);
        verify(repository).findByEnabledTrueAndRoleInOrderByEmailAsc(List.of(Role.RESEARCHER, Role.COORDINATOR));
    }

    @Test
    void rejectsNonProfessorAndDisabledRequesters() {
        assertThrows(ForbiddenOperationException.class,
                () -> useCase.execute(new AuthenticatedUser(UUID.randomUUID(), "student@test", Role.RESEARCHER, true)));
        assertThrows(ForbiddenOperationException.class,
                () -> useCase.execute(new AuthenticatedUser(UUID.randomUUID(), "prof@test", Role.COORDINATOR, false)));
        verifyNoInteractions(repository);
    }
}
