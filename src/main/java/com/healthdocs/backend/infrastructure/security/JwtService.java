package com.healthdocs.backend.infrastructure.security;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final String issuer;

    @Autowired
    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expirationSeconds,
            @Value("${auth.jwt.issuer}") String issuer) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
    }

    public JwtService(String secret, long expirationSeconds) {
        this(secret, expirationSeconds, "healthdocs-local");
    }

    public String issue(AuthenticatedUser user) {
        return issue(user, user.id().toString());
    }

    public String issue(AuthenticatedUser user, String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .claim("role", user.role().value())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public JwtIdentity parse(String token) {
        var claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload();
        String tokenIssuer = claims.getIssuer();
        String subject = claims.getSubject();
        // Accept unexpired pre-migration local tokens (UUID subject, no issuer).
        if (tokenIssuer == null) {
            return new JwtIdentity(UUID.fromString(subject), null, subject);
        }
        if (!issuer.equals(tokenIssuer)) {
            throw new JwtException("Unexpected JWT issuer");
        }
        UUID userId = null;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException ignored) {
            // Federated subjects are opaque strings and need not be UUIDs.
        }
        return new JwtIdentity(userId, tokenIssuer, subject);
    }

    public String issuer() {
        return issuer;
    }

    public record JwtIdentity(UUID userId, String issuer, String subject) {
    }
}
