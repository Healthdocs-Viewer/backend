package com.healthdocs.backend.adapter.out.persistence.entity;

import com.healthdocs.backend.domain.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import com.healthdocs.backend.adapter.out.persistence.RoleConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "app_user")
public class AppUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_subject", nullable = false, unique = true, length = 255)
    private String externalSubject;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Convert(converter = RoleConverter.class)
    @Column(nullable = false, length = 50)
    private Role role;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUserEntity() {
    }

    public AppUserEntity(String externalSubject, String email, Role role) {
        this.externalSubject = externalSubject;
        this.email = email;
        this.role = role;
        this.enabled = true;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public void changeRole(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        this.role = role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
