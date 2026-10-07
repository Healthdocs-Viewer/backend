package com.healthdocs.backend.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "auth_identity", uniqueConstraints = @UniqueConstraint(
        name = "uq_auth_identity_provider_issuer_subject",
        columnNames = {"provider", "issuer", "subject"}))
public class AuthIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 512)
    private String issuer;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "linked_by", length = 255)
    private String linkedBy;

    @Column(name = "verification_reference", length = 255)
    private String verificationReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuthIdentityEntity() {
    }

    public AuthIdentityEntity(AppUserEntity user, String provider, String issuer, String subject) {
        this(user, provider, issuer, subject, null, null);
    }

    public AuthIdentityEntity(
            AppUserEntity user,
            String provider,
            String issuer,
            String subject,
            String linkedBy,
            String verificationReference) {
        this.user = user;
        this.provider = provider;
        this.issuer = issuer;
        this.subject = subject;
        this.linkedBy = linkedBy;
        this.verificationReference = verificationReference;
    }

    public UUID getId() {
        return id;
    }

    public AppUserEntity getUser() {
        return user;
    }

    public String getProvider() {
        return provider;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    public String getLinkedBy() {
        return linkedBy;
    }

    public String getVerificationReference() {
        return verificationReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
