package com.springmfg.ims.iam.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens", schema = "ims")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    private Long replacedBy;

    @Column(length = 200)
    private String userAgent;

    @Column(length = 45)
    private String ip;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(User user, String tokenHash, UUID familyId, Instant expiresAt, String userAgent, String ip) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ip = ip;
    }

    public boolean isExpired() { return expiresAt.isBefore(Instant.now()); }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isValid()   { return !isExpired() && !isRevoked(); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Long getReplacedBy() { return replacedBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void revoke() { this.revokedAt = Instant.now(); }
    public void setReplacedBy(Long replacedBy) { this.replacedBy = replacedBy; }
}
