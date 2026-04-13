package com.stellarideas.grooves.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class BulkDeleteRequest {

    @NotEmpty
    @Size(max = 100, message = "Cannot delete more than 100 files at once")
    private List<String> fileIds;

    public List<String> getFileIds() { return fileIds; }
    public void setFileIds(List<String> fileIds) { this.fileIds = fileIds; }
}
