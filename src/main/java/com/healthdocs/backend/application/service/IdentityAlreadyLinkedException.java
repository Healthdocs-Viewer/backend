package com.healthdocs.backend.application.service;

public class IdentityAlreadyLinkedException extends RuntimeException {

    public IdentityAlreadyLinkedException() {
        super("Authentication identity is already linked to another account");
    }
}
