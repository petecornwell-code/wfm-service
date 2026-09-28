package com.wfm.dto;

import java.util.List;

/**
 * A page of department search results, plus what the filters removed.
 *
 * <p><b>Why the exclusions are reported rather than merely applied.</b> The job-title allowlist is a
 * case-insensitive SUBSTRING match, so a title one word away from a configured pattern —
 * "Customer Service Representative" against a pattern of "Customer Support Representative" — fails
 * it. That has already happened on the live tenant and silently removed most of a desk's roster,
 * where it read as a BambooHR data problem rather than a configuration one. Naming the excluded
 * titles turns a vanishing act into something an operator can act on.
 *
 * @param data              the page, after filtering
 * @param hasMore           whether a further page exists
 * @param totalCount        matches AFTER filtering, so it agrees with what is shown
 * @param hiddenByJobTitle  active employees in this department whose title missed the allowlist
 * @param hiddenJobTitles   the distinct titles that were excluded, capped for display
 * @param allowlistActive   false when the tenant has configured no patterns, in which case nothing
 *                          is excluded by title and the counts above are zero
 */
public record EmployeeSearchResponse(
        List<BambooEmployeeResponse> data,
        boolean hasMore,
        int totalCount,
        int hiddenByJobTitle,
        List<String> hiddenJobTitles,
        boolean allowlistActive
) {}
