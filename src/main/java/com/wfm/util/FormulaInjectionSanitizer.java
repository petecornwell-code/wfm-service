package com.wfm.util;

/**
 * Single-source formula/CSV-injection guard (CR-02/WR-05) shared by every writer of
 * operator- or BambooHR-supplied string values into a spreadsheet cell (blank-template
 * generator, desk-agent export, and any future writer). Centralizing this here means the
 * writers cannot drift on the exact character set being neutralized, which is precisely
 * how {@code DeskAgentExportService} previously diverged from
 * {@code DeskAssignmentTemplateService}'s guard.
 *
 * Any string whose first character is one of the classic formula triggers
 * ({@code = + - @}) or a leading tab/CR (per OWASP CSV-injection guidance, since some
 * spreadsheet/CSV consumers can still trigger formula evaluation on lines beginning with
 * those characters after leading-whitespace normalization) is prefixed with a single quote
 * so spreadsheet applications render it as literal text rather than evaluating it.
 */
public final class FormulaInjectionSanitizer {

    private FormulaInjectionSanitizer() {}

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
