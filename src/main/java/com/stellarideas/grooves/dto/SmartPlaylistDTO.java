package com.stellarideas.grooves.dto;

import com.stellarideas.grooves.model.SmartPlaylist;

import java.time.Instant;

public class SmartPlaylistDTO {

    private String id;
    private String name;
    private String queryString;
    private String description;
    private String shareToken;
    private boolean subscribed;
    private String curatorUsername;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Build a DTO for the user's own list view. {@code resolvedQuery} and
     * {@code resolvedDescription} should reflect the live source values for
     * subscriptions; {@code curatorUsername} should be set for subscriptions.
     */
    public static SmartPlaylistDTO from(SmartPlaylist sp,
                                        String resolvedQuery,
                                        String resolvedDescription,
                                        String curatorUsername) {
        SmartPlaylistDTO dto = new SmartPlaylistDTO();
        dto.id = sp.getId();
        dto.name = sp.getName();
        dto.queryString = resolvedQuery;
        dto.description = resolvedDescription;
        dto.shareToken = sp.getShareToken();
        dto.subscribed = sp.isSubscription();
        dto.curatorUsername = curatorUsername;
        dto.createdAt = sp.getCreatedAt();
        dto.updatedAt = sp.getUpdatedAt();
        return dto;
    }

    /** Convenience for owner rows with no resolution required. */
    public static SmartPlaylistDTO from(SmartPlaylist sp) {
        return from(sp, sp.getQueryString(), sp.getDescription(), null);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }

    public boolean isSubscribed() { return subscribed; }
    public void setSubscribed(boolean subscribed) { this.subscribed = subscribed; }

    public String getCuratorUsername() { return curatorUsername; }
    public void setCuratorUsername(String curatorUsername) { this.curatorUsername = curatorUsername; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
