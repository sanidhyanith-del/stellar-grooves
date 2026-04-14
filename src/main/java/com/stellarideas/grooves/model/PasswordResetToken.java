package com.stellarideas.grooves.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Document(collection = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    private String id;

    /**
     * The raw token is never persisted — only its SHA-256 hash is stored.
     * This field is populated only at creation time so the caller can send it
     * to the user (e.g. in a reset email).
     */
    @Transient
    private String rawToken;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed
    private String userId;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private boolean used;

    private Instant createdAt;

    public PasswordResetToken() {
    }

    public PasswordResetToken(String userId) {
        this.userId = userId;
        this.rawToken = UUID.randomUUID().toString();
        this.tokenHash = hashToken(this.rawToken);
        this.expiresAt = Instant.now().plusSeconds(15 * 60);
        this.createdAt = Instant.now();
        this.used = false;
    }

    /**
     * SHA-256 hash a token string. UUIDs have 122 bits of entropy,
     * so a fast hash is sufficient (no brute-force risk).
     */
    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the raw (unhashed) token. Only available immediately after construction,
     * not after loading from the database.
     */
    public String getRawToken() {
        return rawToken;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordResetToken that = (PasswordResetToken) o;
        return java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }
}
