package com.healthdocs.backend.adapter.out.persistence.entity;

import com.healthdocs.backend.domain.model.Role;
import com.healthdocs.backend.domain.model.ResearcherAuthorizationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "researcher_code", length = 50, unique = true)
    private String researcherCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_status", length = 20)
    private ResearcherAuthorizationStatus authorizationStatus;

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
        this.authorizationStatus = role == Role.RESEARCHER
                ? ResearcherAuthorizationStatus.AUTHORIZED : null;
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
        Role previousRole = this.role;
        this.role = role;
        if (role == Role.RESEARCHER) {
            if (previousRole != Role.RESEARCHER || authorizationStatus == null) {
                authorizationStatus = ResearcherAuthorizationStatus.AUTHORIZED;
            }
        } else {
            authorizationStatus = null;
        }
    }

    public String getFullName() { return fullName; }
    public String getResearcherCode() { return researcherCode; }
    public ResearcherAuthorizationStatus getAuthorizationStatus() { return authorizationStatus; }

    public void updateResearcherProfile(String fullName, String researcherCode) {
        if (fullName == null || fullName.isBlank() || researcherCode == null || researcherCode.isBlank()) {
            throw new IllegalArgumentException("Full name and researcher code are required");
        }
        String normalizedName = fullName.trim();
        String normalizedCode = researcherCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedName.length() > 200 || normalizedCode.length() > 50) {
            throw new IllegalArgumentException("Researcher profile fields exceed their maximum length");
        }
        this.fullName = normalizedName;
        this.researcherCode = normalizedCode;
    }

    public void changeAuthorizationStatus(ResearcherAuthorizationStatus status) {
        if (role != Role.RESEARCHER || status == null) {
            throw new IllegalArgumentException("Authorization can only be changed for researchers");
        }
        authorizationStatus = status;
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
