package com.wfm.dto;

import java.util.List;

public record ErrorResponse(Error error) {
    public record Error(String code, String message, List<ErrorDetail> details) {}
    public record ErrorDetail(String field, String message, String value) {}
}
