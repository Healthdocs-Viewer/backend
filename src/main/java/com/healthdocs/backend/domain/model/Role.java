package com.healthdocs.backend.domain.model;

public enum Role {
    PACIENTE("paciente"),
    ESTUDIANTE("estudiante"),
    PROFESOR("profesor");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Role fromValue(String value) {
        for (Role role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unsupported role");
    }
}
