package com.healthdocs.backend.adapter.out.auth;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.AuthIdentityRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.infrastructure.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalAuthProviderTest {

    private AppUserRepository repository;
    private AuthIdentityRepository identities;
    private LocalAuthProvider provider;

    @BeforeEach
    void setUp() {
        repository = mock(AppUserRepository.class);
        identities = mock(AuthIdentityRepository.class);
        provider = new LocalAuthProvider(
                repository,
                identities,
                new BCryptPasswordEncoder(),
                new JwtService("01234567890123456789012345678901", 3600));
    }

    @Test
    void acceptsDocumentedCredentialsForAllUsers() {
        assertCredentials("paciente@healthdocs.local", "paciente123", Role.PATIENT);
        assertCredentials("estudiante@healthdocs.local", "estudiante123", Role.RESEARCHER);
        assertCredentials("profesor@healthdocs.local", "profesor123", Role.COORDINATOR);
    }

    @Test
    void rejectsIncorrectPassword() {
        AppUserEntity user = user(Role.PATIENT, "paciente123", true);
        when(repository.findByEmailIgnoreCase("paciente@healthdocs.local")).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate("paciente@healthdocs.local", "wrong"));
    }

    @Test
    void returnsForbiddenMarkerForDisabledAccountAfterCorrectPassword() {
        AppUserEntity user = user(Role.PATIENT, "paciente123", false);
        when(repository.findByEmailIgnoreCase("paciente@healthdocs.local")).thenReturn(Optional.of(user));

        assertThrows(LocalAuthProvider.DisabledAccountException.class,
                () -> provider.authenticate("paciente@healthdocs.local", "paciente123"));
    }

    private void assertCredentials(String email, String password, Role role) {
        AppUserEntity user = user(role, password, true);
        when(repository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));

        assertEquals(role, provider.authenticate(email, password).role());
    }

    private AppUserEntity user(Role role, String password, boolean enabled) {
        AppUserEntity user = mock(AppUserEntity.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getEmail()).thenReturn("user@healthdocs.local");
        when(user.getRole()).thenReturn(role);
        when(user.getPasswordHash()).thenReturn(new BCryptPasswordEncoder().encode(password));
        when(user.isEnabled()).thenReturn(enabled);
        return user;
    }
}
