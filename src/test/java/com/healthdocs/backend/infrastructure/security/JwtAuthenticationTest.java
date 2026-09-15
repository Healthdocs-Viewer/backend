package com.healthdocs.backend.infrastructure.security;

import com.healthdocs.backend.application.port.out.AuthProvider;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtAuthenticationTest {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void issuedTokenContainsIdentityAndRole() {
        JwtService service = new JwtService(SECRET, 3600);
        UUID userId = UUID.randomUUID();

        JwtService.JwtIdentity identity = service.parse(service.issue(
                new AuthenticatedUser(userId, "user@example.com", Role.PACIENTE, true)));

        assertEquals(userId, identity.userId());
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService service = new JwtService(SECRET, 0);
        String token = service.issue(
                new AuthenticatedUser(UUID.randomUUID(), "user@example.com", Role.PACIENTE, true));
        Thread.sleep(Duration.ofMillis(10));

        assertThrows(JwtException.class, () -> service.parse(token));
    }

    @Test
    void malformedTokenIsRejected() {
        JwtService service = new JwtService(SECRET, 3600);

        assertThrows(JwtException.class, () -> service.parse("not-a-jwt"));
    }

    @Test
    void filterDoesNotAuthenticateExpiredToken() throws Exception {
        JwtService service = new JwtService(SECRET, 0);
        AuthenticatedUser expected = new AuthenticatedUser(
                UUID.randomUUID(), "user@example.com", Role.PACIENTE, true);
        AuthProvider provider = new AuthProvider() {
            @Override
            public AuthenticatedUser authenticate(String email, String password) {
                return expected;
            }

            @Override
            public AuthenticatedUser authenticateToken(String token) {
                service.parse(token);
                return expected;
            }

            @Override
            public String issueToken(AuthenticatedUser user) {
                return service.issue(user);
            }
        };
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + service.issue(expected));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        org.junit.jupiter.api.Assertions.assertNull(
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication());
    }

    @Test
    void filterUsesProviderValidationInsteadOfTrustingClaims() {
        JwtService service = new JwtService(SECRET, 3600);
        AuthenticatedUser expected = new AuthenticatedUser(
                UUID.randomUUID(), "user@example.com", Role.ESTUDIANTE, true);
        AuthProvider provider = new AuthProvider() {
            @Override
            public AuthenticatedUser authenticate(String email, String password) {
                return expected;
            }

            @Override
            public AuthenticatedUser authenticateToken(String token) {
                service.parse(token);
                return expected;
            }

            @Override
            public String issueToken(AuthenticatedUser user) {
                return service.issue(user);
            }
        };

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + service.issue(expected));
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, new MockFilterChain());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        assertEquals(expected,
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication().getPrincipal());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
