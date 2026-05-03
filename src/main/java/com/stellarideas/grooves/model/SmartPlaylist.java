package com.stellarideas.grooves.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "smart_playlists")
@CompoundIndexes({
    @CompoundIndex(name = "user_name", def = "{'userId': 1, 'name': 1}")
})
public class SmartPlaylist {

    @Id
    private String id;

    @Version
    private Long version;

    @NotBlank
    @Size(min = 1, max = 100)
    private String name;

    @NotBlank
    @Size(max = 1000)
    private String queryString;

    @Size(max = 500)
    private String description;

    /** Public share token. Null when not shared. Index is sparse so unsharing is cheap. */
    @Indexed(unique = true, sparse = true)
    private String shareToken;

    private Instant shareTokenExpiresAt;

    /**
     * If non-null, this row is a subscription to another curator's smart playlist.
     * Query and description are read live from the source on each preview; the
     * subscriber's own {@code queryString}/{@code description} fields are unused.
     * Subscribers may rename their local copy but cannot edit the query.
     */
    @Indexed
    private String subscribedFromId;

    @JsonIgnore
    private String userId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public SmartPlaylist() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }

    @JsonIgnore
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }

    public Instant getShareTokenExpiresAt() { return shareTokenExpiresAt; }
    public void setShareTokenExpiresAt(Instant shareTokenExpiresAt) { this.shareTokenExpiresAt = shareTokenExpiresAt; }

    public String getSubscribedFromId() { return subscribedFromId; }
    public void setSubscribedFromId(String subscribedFromId) { this.subscribedFromId = subscribedFromId; }

    public boolean isShareTokenExpired() {
        return shareTokenExpiresAt != null && Instant.now().isAfter(shareTokenExpiresAt);
    }

    public boolean isSubscription() {
        return subscribedFromId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmartPlaylist that = (SmartPlaylist) o;
        return java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }
}
