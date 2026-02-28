package com.wfm.exception;

import java.util.List;

/**
 * Thrown when a request is syntactically valid but semantically unprocessable,
 * e.g. pre-solve validation failures.
 */
public class UnprocessableException extends RuntimeException {

    private final List<String> details;

    public UnprocessableException(String message, List<String> details) {
        super(message);
        this.details = details;
    }

    public UnprocessableException(String message) {
        this(message, List.of());
    }

    public List<String> getDetails() {
        return details;
    }
}
