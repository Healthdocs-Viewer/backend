package com.healthdocs.backend.adapter.out.auth;

import com.healthdocs.backend.application.port.out.AuthProvider;
import com.healthdocs.backend.application.port.out.InvalidAuthenticationTokenException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "disabled")
public class UnavailableAuthProvider implements AuthProvider {

    @Override
    public AuthenticatedUser authenticate(String email, String password) {
        throw new IllegalStateException("Authentication provider is disabled");
    }

    @Override
    public AuthenticatedUser authenticateToken(String token) {
        throw new InvalidAuthenticationTokenException();
    }

    @Override
    public String issueToken(AuthenticatedUser user) {
        throw new IllegalStateException("Authentication provider is disabled");
    }
}
