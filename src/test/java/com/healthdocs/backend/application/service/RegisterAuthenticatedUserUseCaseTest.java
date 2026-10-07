package com.healthdocs.backend.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.AuthIdentityRepository;
import com.healthdocs.backend.adapter.out.persistence.AuthRegistrationLock;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.adapter.out.persistence.entity.AuthIdentityEntity;
import com.healthdocs.backend.application.port.out.ExternalIdentityTokenVerifier;
import com.healthdocs.backend.application.port.out.ExternalIdentityTokenVerifier.VerifiedExternalIdentity;
import com.healthdocs.backend.application.port.out.InvalidAuthenticationTokenException;
import com.healthdocs.backend.domain.model.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RegisterAuthenticatedUserUseCaseTest {

    private static final String TOKEN = "access-token";
    private static final VerifiedExternalIdentity IDENTITY = new VerifiedExternalIdentity(
            "supabase", "https://issuer.example", "provider-subject", "New.User@Example.com");

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AuthIdentityRepository identities = mock(AuthIdentityRepository.class);
    private final AuthRegistrationLock registrationLock = mock(AuthRegistrationLock.class);
    private final ExternalIdentityTokenVerifier tokenVerifier = mock(ExternalIdentityTokenVerifier.class);
    private final RegisterAuthenticatedUserUseCase useCase = new RegisterAuthenticatedUserUseCase(
            users, identities, registrationLock, tokenVerifier);

    @Test
    void createsPatientAccountAndProviderIdentityForNewEmail() {
        UUID id = UUID.randomUUID();
        AppUserEntity savedUser = user(id, "new.user@example.com", Role.PATIENT, true);
        when(tokenVerifier.verify(TOKEN)).thenReturn(IDENTITY);
        when(identities.findByProviderAndIssuerAndSubject("supabase", IDENTITY.issuer(), IDENTITY.subject()))
                .thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("new.user@example.com")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(AppUserEntity.class))).thenReturn(savedUser);
        when(identities.saveAndFlush(any(AuthIdentityEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(TOKEN);

        assertEquals(new RegisterAuthenticatedUserUseCase.RegisteredUser(
                id, "new.user@example.com", "patient", true), result);
        verify(registrationLock).lock("identity\nsupabase\nhttps://issuer.example\nprovider-subject");
        verify(registrationLock).lock("email\nnew.user@example.com");
        verify(identities).saveAndFlush(any(AuthIdentityEntity.class));
    }

    @Test
    void repeatedIdentityReturnsTheExistingUserAndKeepsItsRole() {
        UUID id = UUID.randomUUID();
        AppUserEntity existingUser = user(id, "old.address@example.com", Role.RESEARCHER, true);
        AuthIdentityEntity existingIdentity = mock(AuthIdentityEntity.class);
        when(existingIdentity.getUser()).thenReturn(existingUser);
        when(tokenVerifier.verify(TOKEN)).thenReturn(IDENTITY);
        when(identities.findByProviderAndIssuerAndSubject("supabase", IDENTITY.issuer(), IDENTITY.subject()))
                .thenReturn(Optional.of(existingIdentity));

        var first = useCase.execute(TOKEN);
        var retry = useCase.execute(TOKEN);

        assertEquals(new RegisterAuthenticatedUserUseCase.RegisteredUser(
                id, "old.address@example.com", "researcher", true), first);
        assertEquals(first, retry);
        verify(users, never()).findByEmailIgnoreCase(any());
        verify(users, never()).saveAndFlush(any());
        verify(identities, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDifferentIdentityWhenEmailBelongsToAnotherAccountWithoutMerging() {
        when(tokenVerifier.verify(TOKEN)).thenReturn(IDENTITY);
        when(identities.findByProviderAndIssuerAndSubject("supabase", IDENTITY.issuer(), IDENTITY.subject()))
                .thenReturn(Optional.empty());
        AppUserEntity otherAccount = user(
                UUID.randomUUID(), "new.user@example.com", Role.PATIENT, true);
        when(users.findByEmailIgnoreCase("new.user@example.com"))
                .thenReturn(Optional.of(otherAccount));

        assertThrows(BusinessRuleViolationException.class, () -> useCase.execute(TOKEN));

        verify(users, never()).saveAndFlush(any());
        verify(identities, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidTokenBeforeLookingUpOrCreatingAnAccount() {
        when(tokenVerifier.verify("invalid-token")).thenThrow(new InvalidAuthenticationTokenException());

        assertThrows(InvalidAuthenticationTokenException.class, () -> useCase.execute("invalid-token"));

        verifyNoInteractions(users, identities, registrationLock);
    }

    @Test
    void turnsEmailUniqueConstraintRaceIntoConflict() {
        when(tokenVerifier.verify(TOKEN)).thenReturn(IDENTITY);
        when(identities.findByProviderAndIssuerAndSubject("supabase", IDENTITY.issuer(), IDENTITY.subject()))
                .thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("new.user@example.com")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(AppUserEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(BusinessRuleViolationException.class, () -> useCase.execute(TOKEN));
        verify(identities, never()).saveAndFlush(any());
    }

    private static AppUserEntity user(UUID id, String email, Role role, boolean enabled) {
        AppUserEntity user = mock(AppUserEntity.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(user.getRole()).thenReturn(role);
        when(user.isEnabled()).thenReturn(enabled);
        return user;
    }
}
