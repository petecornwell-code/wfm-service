package com.wfm.util;

public final class AgentNameSplitter {

    private AgentNameSplitter() {}

    public record Split(String firstName, String lastName) {}

    /**
     * Splits a BambooHR-style display name into first/last name per the D-06 rule:
     * first whitespace token becomes firstName, the trimmed remainder becomes lastName.
     * A single-token name yields an empty (never null) lastName. Null/blank input
     * yields both fields empty.
     */
    public static Split split(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return new Split("", "");
        }
        String trimmed = displayName.trim();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace < 0) {
            return new Split(trimmed, "");
        }
        String first = trimmed.substring(0, firstSpace);
        String rest = trimmed.substring(firstSpace + 1).trim();
        return new Split(first, rest);
    }
}
