package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.application.port.out.AuthProvider.AuthenticatedUser;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !user.enabled()) {
            return ResponseEntity.status(403).body(Map.<String, Object>of(
                    "code", "FORBIDDEN",
                    "message", "Account is disabled",
                    "traceId", java.util.UUID.randomUUID().toString()));
        }
        return ResponseEntity.ok(Map.of(
                "id", user.id(),
                "email", user.email(),
                "role", user.role().value(),
                "enabled", user.enabled()));
    }
}
