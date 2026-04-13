package com.stellarideas.grooves.dto;

import java.util.ArrayList;
import java.util.List;

public class ScanResult {

    private int saved;
    private int skipped;
    private int errors;
    private final List<String> errorDetails = new ArrayList<>();

    public void incrementSaved()  { saved++; }
    public void incrementSkipped() { skipped++; }

    public void addError(String fileName, String message) {
        errors++;
        if (errorDetails.size() < 50) {
            errorDetails.add(fileName + ": " + message);
        }
    }

    public void addSaved(int count) { saved += count; }

    public int getSaved()   { return saved; }
    public int getSkipped() { return skipped; }
    public int getErrors()  { return errors; }
    public List<String> getErrorDetails() { return errorDetails; }
}
