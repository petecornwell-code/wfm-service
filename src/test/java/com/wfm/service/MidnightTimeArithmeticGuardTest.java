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
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
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
    private static final String COMPARISON_ALLOWLIST_HEADING = "### Permitted raw time comparisons";

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

    /**
     * The tokens that constitute a raw comparison between two scheduling times. Unlike {@link
     * #RAW_ARITHMETIC_TOKENS}, these are gated by {@link #isRawComparison}'s receiver-name
     * heuristic rather than being unconditional: their raw whole-file counts under {@code
     * src/main/java} number in the dozens each, and most of those are {@code LocalDate} and
     * {@code BigDecimal} comparisons that have nothing to do with the midnight boundary. An
     * ungated scan would demand a hundred-entry allowlist, which is the guard-becomes-decoration
     * failure this class's own warning above exists to prevent.
     */
    private static final List<String> COMPARISON_TOKENS = List.of(
            ".isAfter(",
            ".isBefore(",
            ".compareTo(");

    /** DayWindow is the implementation of the rule, so it is the one file exempt from it. */
    private static final String IMPLEMENTATION_CLASS = "com.wfm.util.DayWindow";

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    @Test
    void rawTimeArithmeticInProductionCode_matchesTheAllowlistExactly() throws IOException {
        Set<String> derived = scanProductionSources(MidnightTimeArithmeticGuardTest::isRawArithmetic);
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

    /**
     * The second half of the midnight-boundary hole: {@code
     * LocalTime.MIDNIGHT.isAfter(LocalTime.of(23, 0))} is {@code false}, so a raw {@code isAfter} /
     * {@code isBefore} / {@code compareTo} on two scheduling times is exactly as wrong as the raw
     * arithmetic above, and equally unguarded until this test. Gated by {@link #isRawComparison}'s
     * receiver-name heuristic rather than unconditional, for the reason documented on {@link
     * #COMPARISON_TOKENS}. Same set-equality contract as the arithmetic assertion above: widening
     * either direction to a subset check turns the guard into decoration.
     */
    @Test
    void rawTimeComparisonsInProductionCode_matchesTheComparisonAllowlistExactly() throws IOException {
        Set<String> derived = scanProductionSources(MidnightTimeArithmeticGuardTest::isRawComparison);
        Set<String> allowlist = parseComparisonAllowlist();

        Set<String> notAllowlisted = new HashSet<>(derived);
        notAllowlisted.removeAll(allowlist);
        Set<String> staleEntries = new HashSet<>(allowlist);
        staleEntries.removeAll(derived);

        assertThat(derived)
                .as("""
                        Raw LocalTime interval comparisons in src/main/java must equal the \
                        comparison allowlist in %s exactly.

                        NEW, not allowlisted -- route these through com.wfm.util.DayWindow \
                        (startsBefore / overlaps / contains / endMinute) unless BOTH endpoints \
                        are start times, in which case add the line to the allowlist WITH a note \
                        saying why: %s

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
        // reaching this assertion is itself part of the proof. Same reasoning applies to the
        // comparison allowlist -- an empty comparison section would make the second assertion
        // above vacuous in exactly the same way.
        assertThat(parseAllowlist()).isNotEmpty();
        assertThat(parseComparisonAllowlist()).isNotEmpty();
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
    void theComparisonScanDetectsAFreshOccurrence() {
        // Proves the comparison matcher is live rather than structurally unable to fail, and that
        // its receiver-name heuristic runs both ways: a scheduling-time receiver is caught, a
        // LocalDate or BigDecimal receiver is not, and a comment mentioning any of the tokens is
        // never matched regardless of receiver.
        assertThat(isRawComparison("if (slotStart.isBefore(cutoff)) { }")).isTrue();
        assertThat(isRawComparison("if (envelopeStart.isAfter(bandEnd)) { }")).isTrue();
        assertThat(isRawComparison("if (date.isAfter(periodEnd)) { }")).isFalse();
        assertThat(isRawComparison("if (hours.compareTo(breakConfig.getMaxHours()) > 0) { }")).isFalse();
        assertThat(isRawComparison("// slotStart.isBefore(cutoff) is handled by DayWindow")).isFalse();
    }

    @Test
    void missingAllowlistHeading_failsLoudly() {
        // The parser must never silently return an empty set for a malformed resource -- that is
        // the one way this guard could pass while enforcing nothing. Proven generically for an
        // arbitrary heading, and specifically for both concrete allowlist headings this class
        // parses, so neither the arithmetic nor the comparison assertion can go vacuous.
        assertThatThrownBy(() -> parseFencedBlock(readResource(), "### No Such Heading"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Such Heading");
        assertThatThrownBy(() -> parseFencedBlock("# Empty resource\n\nno headings here", ALLOWLIST_HEADING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ALLOWLIST_HEADING);
        assertThatThrownBy(() -> parseFencedBlock(
                        "# Empty resource\n\nno headings here", COMPARISON_ALLOWLIST_HEADING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(COMPARISON_ALLOWLIST_HEADING);
    }

    // --- scanning ---

    /** Walks {@code src/main/java} once, collecting every line {@code matcher} accepts. Shared by
     *  both the arithmetic and the comparison assertions so there is one walk, not two. */
    private Set<String> scanProductionSources(Predicate<String> matcher) throws IOException {
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
                    if (matcher.test(rawLine)) {
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
     * True when this source line contains a raw comparison ({@link #COMPARISON_TOKENS}) whose
     * RECEIVER looks like a scheduling time, per {@link #looksLikeSchedulingTime}. Only the
     * receiver is inspected — the argument deliberately is not, because inspecting it pulls in
     * {@code date.isAfter(periodEnd)} and {@code hours.compareTo(breakConfig.getMaxHours())} --
     * {@code LocalDate} and {@code BigDecimal} noise this guard does not own (see
     * midnight-time-arithmetic.md: date tokens belong to SOLV-02's calendar-vs-business-date join
     * guard).
     */
    private static boolean isRawComparison(String rawLine) {
        String code = stripComment(rawLine);
        if (code.isEmpty()) {
            return false;
        }
        for (String token : COMPARISON_TOKENS) {
            int from = 0;
            while (true) {
                int pos = code.indexOf(token, from);
                if (pos < 0) {
                    break;
                }
                if (looksLikeSchedulingTime(receiverName(code, pos))) {
                    return true;
                }
                from = pos + 1;
            }
        }
        return false;
    }

    /**
     * The identifier immediately to the left of a comparison token starting at {@code tokenStart}
     * (the token's leading {@code .}), tolerating one trailing empty argument list so that both a
     * bare field ({@code slotStart}) and an accessor ({@code getStartTime()}) resolve to a single
     * name — the receiver, not any qualifier further left.
     */
    private static String receiverName(String code, int tokenStart) {
        int p = tokenStart - 1;
        if (p >= 1 && code.charAt(p) == ')' && code.charAt(p - 1) == '(') {
            p -= 2;
        }
        int end = p + 1;
        int start = end;
        while (start > 0 && isIdentifierChar(code.charAt(start - 1))) {
            start--;
        }
        return code.substring(Math.max(start, 0), Math.max(end, 0));
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * The receiver-name heuristic documented in {@code midnight-time-arithmetic.md}: a receiver
     * looks like a scheduling time when its name ENDS WITH {@code time}, {@code start} or
     * {@code end}, or BEGINS WITH {@code envelope}, {@code band}, {@code break} or {@code slot} —
     * case-insensitively. A false positive fails safe (a human looks at a diff); a receiver named
     * something else entirely is invisible to this heuristic by design.
     */
    private static boolean looksLikeSchedulingTime(String receiver) {
        if (receiver.isEmpty()) {
            return false;
        }
        String name = receiver.toLowerCase(Locale.ROOT);
        return name.endsWith("time") || name.endsWith("start") || name.endsWith("end")
                || name.startsWith("envelope") || name.startsWith("band") || name.startsWith("break")
                || name.startsWith("slot");
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

    private Set<String> parseComparisonAllowlist() throws IOException {
        Set<String> entries = parseFencedBlock(readResource(), COMPARISON_ALLOWLIST_HEADING);
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                    RESOURCE + " parsed to an EMPTY comparison allowlist under '"
                            + COMPARISON_ALLOWLIST_HEADING
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
