package com.wfm.dto;

/**
 * Structured notice for a worksheet that was skipped entirely during an enriched
 * desk-assignment upload — most commonly because its sheet name did not match any
 * configured desk (D-02). Non-blocking: other valid sheets in the same workbook
 * still import.
 */
public record SkippedSheet(String sheetName, String reason) {}
