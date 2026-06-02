package com.wfm.integration;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tolerant parser for BambooHR custom field 4517 ({@code customWorkingdays}).
 *
 * <p>Package-private and static-only (no Spring dependency, no I/O).
 * Called only from {@code BambooRefreshService} in the same package.
 *
 * <p>Security: never throws on any input (V5 input-validation control, threat T-6-IV).
 * Unrecognised or garbage input falls through to {@code Optional.empty()} (data gap).
 *
 * <p>Privacy: raw employee values must NOT be logged at INFO+. DEBUG only if needed.
 */
final class WorkingDaysParser {

    /** Canonical week order Mon=0 .. Sun=6. Used for range expansion and wrap detection. */
    private static final List<DayOfWeek> WEEK_ORDER = List.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
    );

    /** All days as an unmodifiable set for off-day subtraction. */
    private static final Set<DayOfWeek> ALL_DAYS = EnumSet.allOf(DayOfWeek.class);

    private WorkingDaysParser() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parses a raw BambooHR {@code customWorkingdays} string into the set of working days.
     *
     * <p>Returns {@code Optional.empty()} for:
     * <ul>
     *   <li>{@code null}</li>
     *   <li>blank strings</li>
     *   <li>{@code "Variable"} (case-insensitive)</li>
     *   <li>any unrecognised / garbage input</li>
     * </ul>
     *
     * <p>Never throws.
     */
    static Optional<Set<DayOfWeek>> parseWorkingDays(String raw) {
        // Guard: null / blank -> data gap
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();

        // Guard: "Variable" (case-insensitive) -> data gap
        if (trimmed.equalsIgnoreCase("Variable")) {
            return Optional.empty();
        }

        try {
            // Step 1: normalise "X to Y" -> "X-Y" (handles "Mon. to Thurs." style)
            String normalised = trimmed.replaceAll("(?i)\\s+to\\s+", "-");

            // Step 2: strip trailing non-day annotation tokens (e.g. "HOOP" in "Mon - Sun HOOP")
            normalised = stripTrailingAnnotations(normalised);

            if (normalised.isBlank()) {
                return Optional.empty();
            }

            // Step 3: dispatch to comma-list or range parser
            Set<DayOfWeek> result;
            if (normalised.contains(",")) {
                result = parseCommaList(normalised);
            } else {
                result = parseRange(normalised);
            }

            if (result == null || result.isEmpty()) {
                // Unrecognised input (e.g. pure garbage "xyz") -> data gap
                return Optional.empty();
            }
            return Optional.of(result);

        } catch (Exception e) {
            // Safety net: should never be reached given the logic above, but
            // ensures the parser truly never throws on any input (T-6-IV).
            return Optional.empty();
        }
    }

    /**
     * Returns the set of off-days: {@code {Mon..Sun}} minus the supplied working days.
     */
    static Set<DayOfWeek> offDaysFrom(Set<DayOfWeek> workingDays) {
        EnumSet<DayOfWeek> offDays = EnumSet.allOf(DayOfWeek.class);
        offDays.removeAll(workingDays);
        return offDays;
    }

    /**
     * Returns {@code true} iff the off-days set contains exactly 2 days that are adjacent
     * in week order (Monday..Sunday, with Sunday-Monday wrap).
     *
     * <p>Used by plan 03 for the D-05 outlier flag.
     */
    static boolean isStandardTwoContiguousDaysOff(Set<DayOfWeek> offDays) {
        if (offDays == null || offDays.size() != 2) {
            return false;
        }
        List<DayOfWeek> sorted = offDays.stream()
                .map(WEEK_ORDER::indexOf)
                .sorted()
                .map(WEEK_ORDER::get)
                .collect(Collectors.toList());

        int idx0 = WEEK_ORDER.indexOf(sorted.get(0));
        int idx1 = WEEK_ORDER.indexOf(sorted.get(1));

        // Adjacent in linear week order, or Sun(6) + Mon(0) wrap
        return (idx1 - idx0 == 1) || (idx0 == 0 && idx1 == 6);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Strips trailing tokens (space-separated) that do not normalise to a DayOfWeek.
     * Handles "Mon - Sun HOOP" -> "Mon - Sun".
     */
    private static String stripTrailingAnnotations(String s) {
        // Split on spaces, working from the right, pop tokens that are not day tokens.
        // We do NOT split on "-" here — "Mon-Fri" must stay intact.
        // Strategy: only strip whole words that appear after the last day-like token.
        String[] words = s.split("\\s+");
        int lastDayIdx = -1;
        for (int i = 0; i < words.length; i++) {
            String w = words[i].replace("-", "").trim(); // isolate each word (hyphens are separators)
            // A word "contains" a day token if any comma-split sub-token normalises
            // We check at word level: strip trailing punctuation and try to parse
            for (String part : w.split(",")) {
                if (looksLikeDay(part.trim())) {
                    lastDayIdx = i;
                    break;
                }
            }
        }
        if (lastDayIdx < 0) {
            // No day tokens found at all — return original (let parseRange/parseCommaList handle it)
            return s;
        }
        // Keep words[0..lastDayIdx] inclusive; strip words beyond
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= lastDayIdx; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    /**
     * Returns true if the token looks like a day-of-week abbreviation (after stripping periods).
     */
    private static boolean looksLikeDay(String token) {
        return normaliseDayToken(token) != null;
    }

    /**
     * Parses a range string like "Mon-Fri", "Mon - Sun", "Fri-Tue" (week-wrap).
     * Returns null if the range cannot be parsed (treated as data gap by the caller).
     */
    private static Set<DayOfWeek> parseRange(String s) {
        // Split on " - " (with spaces) or "-" without requiring spaces
        String[] parts = s.split("\\s*-\\s*", 2);
        if (parts.length != 2) {
            // Not a valid range format
            return null;
        }
        DayOfWeek start = normaliseDayToken(parts[0].trim());
        DayOfWeek end   = normaliseDayToken(parts[1].trim());
        if (start == null || end == null) {
            return null;
        }

        int startIdx = WEEK_ORDER.indexOf(start);
        int endIdx   = WEEK_ORDER.indexOf(end);

        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        if (endIdx >= startIdx) {
            // No wrap: Mon-Fri, Wed-Sun, etc.
            for (int i = startIdx; i <= endIdx; i++) {
                result.add(WEEK_ORDER.get(i));
            }
        } else {
            // Week-wrap: Fri-Tue -> indices 4,5,6,0,1
            for (int i = startIdx; i < WEEK_ORDER.size(); i++) {
                result.add(WEEK_ORDER.get(i));
            }
            for (int i = 0; i <= endIdx; i++) {
                result.add(WEEK_ORDER.get(i));
            }
        }
        return result;
    }

    /**
     * Parses a comma-separated list like "Mon, Tue, Wed, Thu, Sat".
     * Returns null if any token cannot be normalised (treated as data gap).
     */
    private static Set<DayOfWeek> parseCommaList(String s) {
        String[] tokens = s.split(",");
        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String token : tokens) {
            DayOfWeek day = normaliseDayToken(token.trim());
            if (day == null) {
                // If even one token is unrecognised, treat entire list as data gap
                return null;
            }
            result.add(day);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Normalises a day token to a {@link DayOfWeek}.
     *
     * <p>Handles:
     * <ul>
     *   <li>Trailing periods: {@code "Mon."} -> {@code "Mon"}</li>
     *   <li>Three-letter prefixes: Mon, Tue, Wed, Thu, Fri, Sat, Sun</li>
     *   <li>Variants: Thur, Thurs -> THURSDAY</li>
     * </ul>
     *
     * <p>Returns {@code null} for unrecognised tokens.
     */
    static DayOfWeek normaliseDayToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        // Strip trailing period(s) and whitespace
        String t = token.replaceAll("[.\\s]+$", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        // Normalise to title case of first 3 chars (or full if shorter)
        // Map known variants explicitly for safety
        return switch (t.toLowerCase()) {
            case "mon", "monday"    -> DayOfWeek.MONDAY;
            case "tue", "tues", "tuesday"  -> DayOfWeek.TUESDAY;
            case "wed", "wednesday" -> DayOfWeek.WEDNESDAY;
            case "thu", "thur", "thurs", "thursday" -> DayOfWeek.THURSDAY;
            case "fri", "friday"    -> DayOfWeek.FRIDAY;
            case "sat", "saturday"  -> DayOfWeek.SATURDAY;
            case "sun", "sunday"    -> DayOfWeek.SUNDAY;
            default                 -> null;
        };
    }
}
