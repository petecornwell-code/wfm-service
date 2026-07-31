package com.wfm.util;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Single source of truth for the enriched per-desk upload/template/export column shape (D-13).
 * The parser (header -> index), the template generator (index -> header), and the export
 * service (column order) all resolve header text/order from this class so the three shapes
 * can never drift. No enriched-shape header string literal should be hardcoded anywhere else.
 */
public final class EnrichedColumnLayout {

    public static final String COL_BAMBOOHR_ID = "BambooHR ID";
    public static final String COL_FIRST_NAME = "First Name";
    public static final String COL_LAST_NAME = "Last Name";
    public static final String COL_JOB_TITLE = "Job Title";
    public static final String COL_EMAIL = "Email";
    public static final String COL_DEPARTMENT = "Department";
    public static final String COL_ACTIVE = "Active";

    /** Retired per-row desk column (D-01) — absent from the new per-desk-sheet shape. */
    public static final String RETIRED_COL_DESK = "Desk";

    /** Legacy 6-col upload shape marker header (D-15 — rejected, not accepted). */
    public static final String LEGACY_HEADER_DESK_ASSIGNMENT = "Desk Assignment";

    public static final DayOfWeek[] DAY_ORDER = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
    };

    // Digit group bounded to 9 digits (max 999,999,999, well under Integer.MAX_VALUE)
    // so Integer.parseInt below can never overflow (WR-02) — an unbounded \d+ let a
    // header like "Specialty 99999999999999999999" match the regex and then throw an
    // uncaught NumberFormatException, crashing the whole upload with a 500.
    private static final Pattern SPECIALTY_HEADER =
            Pattern.compile("^specialty\\s*(\\d{1,9})$", Pattern.CASE_INSENSITIVE);

    private EnrichedColumnLayout() {}

    /** Title-case day header, e.g. MONDAY -> "Monday". */
    public static String dayHeader(DayOfWeek d) {
        String name = d.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /** The seven identity headers, in canonical order. */
    public static List<String> identityHeaders() {
        return List.of(COL_BAMBOOHR_ID, COL_FIRST_NAME, COL_LAST_NAME,
                COL_JOB_TITLE, COL_EMAIL, COL_DEPARTMENT, COL_ACTIVE);
    }

    /**
     * Detects an unbounded {@code Specialty N} header (D-06), case-insensitively and
     * whitespace-tolerantly. Expects an already-normalized (trim+lowercase) header string.
     */
    public static Optional<Integer> specialtyIndex(String headerLowerTrimmed) {
        if (headerLowerTrimmed == null) {
            return Optional.empty();
        }
        var matcher = SPECIALTY_HEADER.matcher(headerLowerTrimmed);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            // Defense-in-depth alongside the bounded regex above (WR-02): treat an
            // unparseable/overflowing digit group as "not a Specialty N header" rather
            // than propagating an uncaught exception that fails the whole upload.
            return Optional.empty();
        }
    }

    /** Trim + lowercase, matching the parser's existing header-key convention. Null -> "". */
    public static String normalize(String header) {
        return header == null ? "" : header.trim().toLowerCase();
    }
}
