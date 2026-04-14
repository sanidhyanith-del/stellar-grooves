package com.stellarideas.grooves.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    private String id;

    @Transient
    private String rawToken;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed
    private String userId;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private Instant createdAt;

    public EmailVerificationToken() {}

    public EmailVerificationToken(String userId) {
        this.userId = userId;
        this.rawToken = UUID.randomUUID().toString();
        this.tokenHash = PasswordResetToken.hashToken(this.rawToken);
        this.expiresAt = Instant.now().plusSeconds(24 * 60 * 60); // 24 hours
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRawToken() { return rawToken; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailVerificationToken that = (EmailVerificationToken) o;
        return java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }
}
