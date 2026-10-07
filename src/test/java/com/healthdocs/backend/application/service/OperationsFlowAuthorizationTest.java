package com.healthdocs.backend.application.service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import com.healthdocs.backend.domain.model.Role;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationsFlowAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void nonProfessorReceivesForbiddenForPendingCases() throws Exception {
        mockMvc.perform(get("/api/v1/operations/cases/pending")
                        .with(authentication(authToken(Role.RESEARCHER, true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void enabledProfessorCanConsultPendingCases() throws Exception {
        mockMvc.perform(get("/api/v1/operations/cases/pending")
                        .with(authentication(authToken(Role.COORDINATOR, true))))
                .andExpect(status().isOk());
    }

    private UsernamePasswordAuthenticationToken authToken(Role role, boolean enabled) {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@test", role, enabled);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        if (enabled) {
            authorities.add(new SimpleGrantedAuthority("ACCOUNT_ENABLED"));
        }
        return new UsernamePasswordAuthenticationToken(user, null, authorities);
    }
}
