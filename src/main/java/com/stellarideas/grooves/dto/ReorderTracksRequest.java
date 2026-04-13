package com.stellarideas.grooves.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class ReorderTracksRequest {

    @NotEmpty
    private List<String> trackIds;

    public List<String> getTrackIds() { return trackIds; }
    public void setTrackIds(List<String> trackIds) { this.trackIds = trackIds; }
}
