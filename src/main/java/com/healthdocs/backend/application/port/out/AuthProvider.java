package com.healthdocs.backend.application.port.out;

import com.healthdocs.backend.domain.model.Role;
import java.util.UUID;

public interface AuthProvider {

    AuthenticatedUser authenticate(String email, String password);

    AuthenticatedUser authenticateToken(String token);

    String issueToken(AuthenticatedUser user);

    record AuthenticatedUser(UUID id, String email, Role role, boolean enabled) {
    }
}
