package com.healthdocs.backend.adapter.out.auth;

import com.healthdocs.backend.application.port.out.AuthProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "cognito")
public class CognitoAuthProvider implements AuthProvider {

    @Override
    public AuthenticatedUser authenticate(String email, String password) {
        throw new UnsupportedOperationException("Cognito provider is not implemented yet");
    }

    @Override
    public AuthenticatedUser authenticateToken(String token) {
        throw new UnsupportedOperationException("Cognito provider is not implemented yet");
    }

    @Override
    public String issueToken(AuthenticatedUser user) {
        throw new UnsupportedOperationException("Cognito provider is not implemented yet");
    }
}
