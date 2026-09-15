package com.healthdocs.backend.application.port.out;

public class InvalidAuthenticationTokenException extends RuntimeException {

    public InvalidAuthenticationTokenException() {
        super("Invalid authentication token");
    }
}
