package com.healthdocs.backend.application.service;

import com.healthdocs.backend.application.port.out.AuthProvider;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final AuthProvider authProvider;

    public AuthenticationService(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public LoginResult login(String email, String password) {
        AuthenticatedUser user = authProvider.authenticate(email, password);
        return new LoginResult(authProvider.issueToken(user), user);
    }

    public AuthenticatedUser currentUser(String token) {
        return authProvider.authenticateToken(token);
    }

    public record LoginResult(String token, AuthenticatedUser user) {
    }
}
