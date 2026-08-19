package com.wfm.dto;

/**
 * Ephemeral per-divergence report row returned in {@code DeskAssignmentUploadResult}
 * (D-11/D-13). Built only where the two sources genuinely disagree (BambooHR overrode a
 * differing spreadsheet value) or where the spreadsheet filled a BambooHR gap -- silent
 * agreement between BambooHR and the sheet never produces a row. Not persisted: gone when
 * the Upload Results modal closes.
 */
public record MergeReportEntry(
        String bamboohrId,
        String agentName,
        String field,
        String bambooValue,
        String sheetValue,
        String outcome
) {}
