package com.healthdocs.backend.adapter.out.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.healthdocs.backend.application.port.out.InvalidAuthenticationTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class SupabaseIdentityTokenVerifierTest {

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final SupabaseIdentityTokenVerifier verifier = new SupabaseIdentityTokenVerifier(decoder);

    @Test
    void extractsIdentityOnlyFromDecodedTokenClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://issuer.example")
                .subject("subject-123")
                .claim("email", "person@example.com")
                .build();
        when(decoder.decode("token")).thenReturn(jwt);

        var identity = verifier.verify("token");

        assertEquals("supabase", identity.provider());
        assertEquals("https://issuer.example", identity.issuer());
        assertEquals("subject-123", identity.subject());
        assertEquals("person@example.com", identity.email());
    }

    @Test
    void rejectsInvalidJwt() {
        when(decoder.decode("invalid")).thenThrow(new JwtException("invalid"));

        assertThrows(InvalidAuthenticationTokenException.class, () -> verifier.verify("invalid"));
    }

    @Test
    void rejectsTokenWithoutEmailClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://issuer.example")
                .subject("subject-123")
                .build();
        when(decoder.decode("token")).thenReturn(jwt);

        assertThrows(InvalidAuthenticationTokenException.class, () -> verifier.verify("token"));
    }
}
