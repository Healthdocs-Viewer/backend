package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeUserRoleUseCase {

    private final AppUserRepository users;

    public ChangeUserRoleUseCase(AppUserRepository users) {
        this.users = users;
    }

    @Transactional
    public UserRoleView execute(AuthenticatedUser administrator, UUID userId, Role role) {
        if (administrator == null || !administrator.enabled() || administrator.role() != Role.ADMIN) {
            throw new ForbiddenOperationException("Only enabled administrators can change user roles");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (role == null || role == Role.ADMIN) {
            throw new IllegalArgumentException("Role must be patient, researcher, or coordinator");
        }
        if (administrator.id().equals(userId)) {
            throw new IllegalArgumentException("Administrators cannot change their own role");
        }

        AppUserEntity user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.changeRole(role);
        AppUserEntity updated = users.save(user);
        return new UserRoleView(updated.getId(), updated.getEmail(), updated.getRole().value(), updated.isEnabled());
    }

    public record UserRoleView(UUID userId, String email, String role, boolean enabled) {
    }
}
