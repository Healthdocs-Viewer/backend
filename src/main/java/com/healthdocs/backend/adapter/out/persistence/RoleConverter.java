package com.healthdocs.backend.adapter.out.persistence;

import com.healthdocs.backend.domain.model.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RoleConverter implements AttributeConverter<Role, String> {

    @Override
    public String convertToDatabaseColumn(Role role) {
        return role == null ? null : role.value();
    }

    @Override
    public Role convertToEntityAttribute(String value) {
        return value == null ? null : Role.fromValue(value);
    }
}
