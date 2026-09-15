package com.healthdocs.backend.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.UUID;
import com.healthdocs.backend.adapter.out.auth.LocalAuthProvider.DisabledAccountException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "Invalid credentials", UUID.randomUUID().toString()));
    }

    @ExceptionHandler(DisabledAccountException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", "Account is disabled", UUID.randomUUID().toString()));
    }

    public record ErrorResponse(String code, String message, String traceId) {
    }
}
