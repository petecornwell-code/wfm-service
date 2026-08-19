package com.wfm.exception;

/**
 * A non-rate-limit BambooHR sync failure during an upload-triggered fetch (MRG-07). Carries
 * an operator-readable message stating that no changes were made; the original failure is
 * preserved as the cause for diagnostics.
 */
public class BambooHRSyncFailedException extends RuntimeException {

    public BambooHRSyncFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
