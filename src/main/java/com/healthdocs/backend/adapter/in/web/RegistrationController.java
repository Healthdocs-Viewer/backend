package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.service.RegisterAuthenticatedUserUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "app.auth-provider", havingValue = "supabase")
public class RegistrationController {

    private final RegisterAuthenticatedUserUseCase registerUser;

    public RegistrationController(RegisterAuthenticatedUserUseCase registerUser) {
        this.registerUser = registerUser;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterAuthenticatedUserUseCase.RegisteredUser> register(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A bearer access token is required");
        }

        var user = registerUser.execute(authorization.substring("Bearer ".length()));
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
}
