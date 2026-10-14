package com.wfm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.wfm.dto.ScheduleDetailResponse.ConstraintViolationEntry;

import java.util.List;

/**
 * Encodes the accept-time constraint-violation report into {@code schedule.constraint_violations_snapshot}
 * (V57) and decodes it on the accepted read path. A static mapper rather than an injected one so
 * {@link ScheduleOutputService}'s constructor — which tests call directly — does not change.
 */
final class ConstraintViolationSnapshotCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            // A report written by an older build must still load after the records gain a field.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private static final TypeReference<List<ConstraintViolationEntry>> TYPE = new TypeReference<>() {};

    private ConstraintViolationSnapshotCodec() {}

    static String encode(List<ConstraintViolationEntry> entries) {
        try {
            return MAPPER.writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise constraint-violation snapshot", e);
        }
    }

    static List<ConstraintViolationEntry> decode(String json) {
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read constraint-violation snapshot", e);
        }
    }
}
