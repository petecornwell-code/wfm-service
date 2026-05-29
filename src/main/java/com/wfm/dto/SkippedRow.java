package com.wfm.dto;

public record SkippedRow(
        int rowNumber,
        String bamboohrId,
        String name,
        String reason
) {}
