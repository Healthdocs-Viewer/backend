package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.service.AuthenticationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.LoginResult result =
                authenticationService.login(request.email(), request.password());
        var user = result.user();
        return ResponseEntity.ok(new LoginResponse(
                result.token(),
                Map.of(
                        "id", user.id(),
                        "email", user.email(),
                        "role", user.role().value(),
                        "enabled", user.enabled())));
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record LoginResponse(String token, Map<String, Object> user) {
    }
}
