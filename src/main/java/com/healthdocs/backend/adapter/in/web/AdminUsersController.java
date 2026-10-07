package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.application.service.ChangeUserRoleUseCase;
import com.healthdocs.backend.domain.model.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUsersController {

    private final ChangeUserRoleUseCase changeUserRole;

    public AdminUsersController(ChangeUserRoleUseCase changeUserRole) {
        this.changeUserRole = changeUserRole;
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_ENABLED')")
    public ResponseEntity<ChangeUserRoleUseCase.UserRoleView> changeRole(
            @AuthenticationPrincipal AuthenticatedUser administrator,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        Role role;
        try {
            role = Role.fromValue(request.role().trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Role must be patient, researcher, or coordinator", exception);
        }
        return ResponseEntity.ok(changeUserRole.execute(administrator, userId, role));
    }

    public record ChangeRoleRequest(@NotBlank String role) {
    }
}
