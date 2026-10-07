package com.healthdocs.backend.adapter.in.web;

import com.healthdocs.backend.adapter.out.auth.LocalAuthProvider.DisabledAccountException;
import com.healthdocs.backend.application.port.out.InvalidAuthenticationTokenException;
import com.healthdocs.backend.application.service.BusinessRuleViolationException;
import com.healthdocs.backend.application.service.PayloadTooLargeException;
import com.healthdocs.backend.application.service.UnsupportedMediaTypeException;
import com.healthdocs.backend.application.service.ForbiddenOperationException;
import com.healthdocs.backend.application.service.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleValidation(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", exception.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenOperationException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", exception.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", exception.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(BusinessRuleViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", exception.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(InvalidAuthenticationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAuthenticationToken() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "Invalid authentication token", UUID.randomUUID().toString()));
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(PayloadTooLargeException exception) { return ResponseEntity.status(413).body(new ErrorResponse("PAYLOAD_TOO_LARGE", exception.getMessage(), UUID.randomUUID().toString())); }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ResponseEntity<ErrorResponse> handleMedia(UnsupportedMediaTypeException exception) { return ResponseEntity.status(415).body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", exception.getMessage(), UUID.randomUUID().toString())); }

    public record ErrorResponse(String code, String message, String traceId) {
    }
}
