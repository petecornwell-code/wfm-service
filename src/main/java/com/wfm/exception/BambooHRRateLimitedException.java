package com.wfm.exception;

public class BambooHRRateLimitedException extends RuntimeException {

    private final int retryAfterSeconds;

    public BambooHRRateLimitedException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}
