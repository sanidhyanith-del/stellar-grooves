package com.stellarideas.grooves.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ScanScheduleRequest {

    @NotBlank(message = "Cron expression is required")
    @Size(max = 100, message = "Cron expression must not exceed 100 characters")
    private String cronExpression;

    @NotBlank(message = "Scan path is required")
    @Size(max = 1024, message = "Path must not exceed 1024 characters")
    private String path;

    public ScanScheduleRequest() {}

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
