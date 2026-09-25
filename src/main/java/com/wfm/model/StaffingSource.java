package com.wfm.model;

/**
 * How a staffing requirement's number was arrived at. Stored as a string in a plain
 * {@code VARCHAR(20)} with no check constraint, so adding a value needs no migration.
 */
public enum StaffingSource {
    /** Typed in, or loaded from the FTE spreadsheet. */
    DIRECT,
    /** Erlang C — the conservative baseline: every caller waits, nobody abandons. */
    ERLANG_C,
    /** Erlang X — Erlang C plus impatience and retrials. */
    ERLANG_X
}
