package com.wfm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Structural guard for the midnight boundary: no production class may do raw {@link
 * java.time.LocalTime} interval arithmetic outside {@link com.wfm.util.DayWindow}, except for the
 * start-to-start distances enumerated in {@code src/test/resources/midnight-time-arithmetic.md}.
 *
 * <p><strong>The bug class this closes.</strong> {@code LocalTime} has no 24:00, so a day ending
 * at midnight stores {@code 00:00} — the smallest value in the type. {@code
 * Duration.between(15:00, 00:00)} is −900 minutes and {@code 00:00.isAfter(23:00)} is false.
 * Neither throws. Before {@code DayWindow}, eleven separate sites carried that assumption and a
 * desk running to midnight was silently unrepresentable: templates rejected, generation loops
 * producing zero slots, seats judged out of bounds, coverage that could never be satisfied. The
 * resource file explains the rule; this test is what keeps it true.
 *
 * <p><strong>Set equality only.</strong> The assertion is {@code
 * containsExactlyInAnyOrderElementsOf}, in both directions: a new raw-arithmetic line fails the
 * build, and so does an allowlist entry whose line no longer exists. Widening this to a subset
 * check — {@code isSubsetOf}, {@code containsAnyOf}, a bare {@code contains} — turns the guard
 * into decoration, which is the failure mode {@link
 * com.wfm.solver.ScheduleConstraintClassificationTest} and {@link UsualShiftWritePathGuardTest}
 * both warn against in their own javadoc. The stale-entry direction is what stops the list rotting
 * into a permanent exemption for code that has since been fixed.
 *
 * <p><strong>A false positive fails safe.</strong> This is a textual scan with comment lines
 * stripped. A string literal or an identifier containing one of the tokens would register as a
 * match and fail the build, forcing a human to look at a diff. That is the right trade against
 * silently missing a real new occurrence. It cannot see arithmetic hidden behind a helper in
 * another class — but such a helper would itself have to contain one of these tokens, and would
 * be caught there.
 *
 * <p>No Spring context and no database: it walks {@code src/main/java} on disk, the same technique
 * {@link UsualShiftWritePathGuardTest} uses.
 */
class MidnightTimeArithmeticGuardTest {

    private static final String RESOURCE = "midnight-time-arithmetic.md";
    private static final String ALLOWLIST_HEADING = "### Permitted raw time arithmetic";

    /**
     * The tokens that constitute raw interval arithmetic on a time. {@code ChronoUnit.MINUTES}
     * rather than {@code ChronoUnit.MINUTES.between(} so that the {@code temporal.until(x,
     * ChronoUnit.MINUTES)} spelling — which is what {@code TimeslotGeneratorService} originally
     * used, and is trivially easy to reach for again — is caught by the same rule.
     */
    private static final List<String> RAW_ARITHMETIC_TOKENS = List.of(
            "Duration.between(",
            "ChronoUnit.MINUTES",
            ".plusMinutes(",
            ".minusMinutes(");

    /** DayWindow is the implementation of the rule, so it is the one file exempt from it. */
    private static final String IMPLEMENTATION_CLASS = "com.wfm.util.DayWindow";

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    @Test
    void rawTimeArithmeticInProductionCode_matchesTheAllowlistExactly() throws IOException {
        Set<String> derived = scanProductionSources();
        Set<String> allowlist = parseAllowlist();

        Set<String> notAllowlisted = new HashSet<>(derived);
        notAllowlisted.removeAll(allowlist);
        Set<String> staleEntries = new HashSet<>(allowlist);
        staleEntries.removeAll(derived);

        assertThat(derived)
                .as("""
                        Raw LocalTime interval arithmetic in src/main/java must equal the allowlist \
                        in %s exactly.

                        NEW, not allowlisted -- route these through com.wfm.util.DayWindow \
                        (durationMinutes / overlaps / contains / startsBefore / endMinute / \
                        plusWithinDay) unless BOTH endpoints are start times, in which case add \
                        the line to the allowlist WITH a note saying why: %s

                        STALE, allowlisted but no longer present -- remove the entry: %s""",
                        RESOURCE, notAllowlisted, staleEntries)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    @Test
    @DisplayName("no loop advances a LocalTime cursor with plusWithinDay")
    void noLoopUsesALocalTimeCursor() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                for (String header : forStatementHeaders(
                        Files.readAllLines(file, StandardCharsets.UTF_8))) {
                    if (header.contains("DayWindow.plusWithinDay(")) {
                        offenders.add(toFullyQualifiedName(file) + " :: " + header);
                    }
                }
            }
        }

        assertThat(offenders)
                .as("A loop must advance an int minute-of-day cursor, never a LocalTime one. "
                        + "DayWindow.plusWithinDay(23:00, 60) returns 00:00, whose START minute is "
                        + "0, so a LocalTime cursor wraps around the clock instead of terminating "
                        + "and the loop never ends -- it killed a live solve with "
                        + "OutOfMemoryError on 2026-09-23 (see MidnightGapScanTest). Write "
                        + "`for (int m = DayWindow.startMinute(a); m < DayWindow.endMinute(b); "
                        + "m += inc)` and convert with DayWindow.toLocalTime(m) inside the body. "
                        + "Offenders: %s", offenders)
                .isEmpty();
    }

    /**
     * Every {@code for (...)} header in the file, each collapsed onto one line. Headers are
     * gathered by balancing parentheses from the opening {@code for (} rather than by reading a
     * fixed number of lines, so a header wrapped across two, three or more lines is seen whole —
     * the defect this guards against was written as a THREE-line header, which a
     * previous-line-only check would have missed entirely.
     */
    private static List<String> forStatementHeaders(List<String> lines) {
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String code = stripComment(lines.get(i));
            if (!code.startsWith("for (")) {
                continue;
            }
            StringBuilder header = new StringBuilder(code);
            int depth = balance(code);
            for (int j = i + 1; depth > 0 && j < lines.size(); j++) {
                String next = stripComment(lines.get(j));
                header.append(' ').append(next);
                depth += balance(next);
            }
            headers.add(header.toString().replaceAll("\\s+", " ").trim());
        }
        return headers;
    }

    /** Net parenthesis depth contributed by one line. */
    private static int balance(String code) {
        int depth = 0;
        for (char c : code.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
        }
        return depth;
    }

    @Test
    void allowlist_parsesAsNonEmpty() throws IOException {
        // An empty expected set would make the set-equality assertion above vacuously satisfiable
        // if production code ever stopped matching at all. parseAllowlist throws on empty, so
        // reaching this assertion is itself part of the proof.
        assertThat(parseAllowlist()).isNotEmpty();
    }

    @Test
    void theScanActuallySeesProductionSource() throws IOException {
        // Guards the guard: a wrong SOURCE_ROOT (a different working directory, a moved module)
        // would make the scan return an empty set, and set equality against a non-empty allowlist
        // would then fail loudly rather than silently pass -- but only if there IS source to read.
        // This asserts the walk itself found files, so a green run means the rule was applied.
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            assertThat(files.filter(p -> p.toString().endsWith(".java")).count())
                    .as("scan must find production sources under %s", SOURCE_ROOT)
                    .isGreaterThan(50);
        }
    }

    @Test
    void theScanDetectsAFreshOccurrence() {
        // Proves the matcher is live rather than structurally unable to fail: the canonical
        // reintroduction of this bug is caught, and a comment mentioning it is not.
        assertThat(isRawArithmetic("long minutes = Duration.between(ts.getStartTime(), ts.getEndTime()).toMinutes();"))
                .isTrue();
        assertThat(isRawArithmetic("LocalTime end = start.plusMinutes(incrementMinutes);")).isTrue();
        assertThat(isRawArithmetic("// Duration.between is wrong here -- see DayWindow")).isFalse();
        assertThat(isRawArithmetic("* Duration.between(start, end) returns a negative value")).isFalse();
        assertThat(isRawArithmetic("int minutes = DayWindow.durationMinutes(start, end);")).isFalse();
        // A near-miss identifier must not trip the scan (ScheduleConfig holds one):
        assertThat(isRawArithmetic("schedulingMode, DEFAULT_CONSISTENCY_TOLERANCE_MINUTES);")).isFalse();
    }

    @Test
    void missingAllowlistHeading_failsLoudly() {
        // The parser must never silently return an empty set for a malformed resource -- that is
        // the one way this guard could pass while enforcing nothing.
        assertThatThrownBy(() -> parseFencedBlock(readResource(), "### No Such Heading"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Such Heading");
    }

    // --- scanning ---

    private Set<String> scanProductionSources() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            for (Path file : javaFiles) {
                String fqcn = toFullyQualifiedName(file);
                if (IMPLEMENTATION_CLASS.equals(fqcn)) {
                    continue;
                }
                for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String code = stripComment(rawLine);
                    if (isRawArithmetic(rawLine)) {
                        found.add(fqcn + " :: " + code);
                    }
                }
            }
        }
        return found;
    }

    /** True when this source line performs raw time arithmetic once comments are discarded. */
    private static boolean isRawArithmetic(String rawLine) {
        String code = stripComment(rawLine);
        if (code.isEmpty()) {
            return false;
        }
        return RAW_ARITHMETIC_TOKENS.stream().anyMatch(code::contains);
    }

    /**
     * Returns the code portion of a source line: empty for a line that is wholly a comment
     * ({@code //}, a javadoc continuation {@code *}, or a block opener {@code /*}), and otherwise
     * the line trimmed with any trailing {@code //} comment removed.
     */
    private static String stripComment(String rawLine) {
        String line = rawLine.strip();
        if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
            return "";
        }
        int inlineComment = line.indexOf("//");
        if (inlineComment >= 0) {
            line = line.substring(0, inlineComment);
        }
        return line.strip();
    }

    private static String toFullyQualifiedName(Path file) {
        String relative = SOURCE_ROOT.relativize(file).toString();
        return relative.substring(0, relative.length() - ".java".length())
                .replace(java.io.File.separatorChar, '.');
    }

    // --- resource parsing ---

    private Set<String> parseAllowlist() throws IOException {
        Set<String> entries = parseFencedBlock(readResource(), ALLOWLIST_HEADING);
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                    RESOURCE + " parsed to an EMPTY allowlist under '" + ALLOWLIST_HEADING
                            + "'. An empty expected set would make the guard vacuous.");
        }
        return entries;
    }

    private static String readResource() throws IOException {
        try (InputStream in = MidnightTimeArithmeticGuardTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Reads the first fenced code block following {@code heading}, one entry per non-blank line. */
    private static Set<String> parseFencedBlock(String markdown, String heading) {
        List<String> lines = markdown.lines().toList();
        int headingIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(heading)) {
                headingIndex = i;
                break;
            }
        }
        if (headingIndex < 0) {
            throw new IllegalStateException(
                    "Heading '" + heading + "' not found in " + RESOURCE);
        }
        List<String> collected = new ArrayList<>();
        boolean inFence = false;
        for (int i = headingIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.strip().startsWith("```")) {
                if (inFence) {
                    break;
                }
                inFence = true;
                continue;
            }
            if (inFence && !line.isBlank()) {
                collected.add(line.strip());
            }
        }
        if (!inFence) {
            throw new IllegalStateException(
                    "No fenced block found after heading '" + heading + "' in " + RESOURCE);
        }
        return new LinkedHashSet<>(collected);
    }
}
