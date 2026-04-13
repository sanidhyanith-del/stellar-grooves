package com.stellarideas.grooves.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class BulkDeleteRequest {

    @NotEmpty
    @Size(max = 500)
    private List<String> fileIds;

    public List<String> getFileIds() { return fileIds; }
    public void setFileIds(List<String> fileIds) { this.fileIds = fileIds; }
}
