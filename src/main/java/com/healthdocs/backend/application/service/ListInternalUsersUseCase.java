package com.healthdocs.backend.application.service;

import com.healthdocs.backend.adapter.out.persistence.AppUserRepository;
import com.healthdocs.backend.adapter.out.persistence.entity.AppUserEntity;
import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListInternalUsersUseCase {
    private final AppUserRepository users;

    public ListInternalUsersUseCase(AppUserRepository users) {
        this.users = users;
    }

    public List<InternalUser> execute(AuthenticatedUser requester) {
        if (requester == null || !requester.enabled() || requester.role() != Role.COORDINATOR) {
            throw new ForbiddenOperationException("Only enabled coordinators can list internal users");
        }
        return users.findByEnabledTrueAndRoleInOrderByEmailAsc(List.of(Role.RESEARCHER, Role.COORDINATOR))
                .stream()
                .map(InternalUser::from)
                .toList();
    }

    public record InternalUser(UUID id, String email, Role role) {
        static InternalUser from(AppUserEntity user) {
            return new InternalUser(user.getId(), user.getEmail(), user.getRole());
        }
    }
}
