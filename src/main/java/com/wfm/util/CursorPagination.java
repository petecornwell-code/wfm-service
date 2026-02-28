package com.wfm.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wfm.dto.PaginatedResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilities for cursor-based (keyset) pagination.
 * <p>
 * A cursor is a Base64-encoded JSON object whose keys are the sort-column names
 * and whose values are the column values of the last row returned.
 * Callers query for {@code limit + 1} rows; if all {@code limit + 1} come back,
 * there is a next page.
 */
public final class CursorPagination {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE = new TypeReference<>() {};

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private CursorPagination() {}

    /**
     * Decodes a Base64 cursor string into an ordered key→value map.
     * Returns an empty map if cursor is null or blank.
     *
     * @throws IllegalArgumentException if the cursor is malformed
     */
    public static Map<String, String> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Map.of();
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    /**
     * Encodes a key→value map into a Base64 cursor string.
     */
    public static String encode(Map<String, String> values) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(values);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    /**
     * Clamps the requested limit to the allowed range [1, MAX_LIMIT],
     * defaulting to DEFAULT_LIMIT if null.
     */
    public static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    /**
     * Builds a {@link PaginatedResponse} from query results.
     * <p>
     * The caller should query for {@code limit + 1} items.
     * If the result list contains more than {@code limit} items, the extra item
     * is trimmed and {@code hasMore} is set to {@code true}.
     *
     * @param results        the query results (up to limit + 1 items)
     * @param limit          the page size requested by the client
     * @param cursorExtractor function that extracts cursor key→value pairs from the last item
     * @return a paginated response with cursor for the next page
     */
    public static <T> PaginatedResponse<T> buildPage(List<T> results, int limit,
                                                       Function<T, Map<String, String>> cursorExtractor) {
        if (results.size() <= limit) {
            return new PaginatedResponse<>(results, null, false);
        }
        List<T> page = results.subList(0, limit);
        T lastItem = page.get(page.size() - 1);
        String nextCursor = encode(cursorExtractor.apply(lastItem));
        return new PaginatedResponse<>(page, nextCursor, true);
    }
}
