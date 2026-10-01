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
    private static final String MIDNIGHT_ANCHOR_ALLOWLIST_HEADING = "### Permitted midnight anchors";

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

    /**
     * The token that constitutes a midnight {@link com.wfm.util.DayWindow} anchor binding (D-07):
     * a call site that cannot reach a desk's real day-start anchor, so it binds midnight explicitly
     * instead. Unlike {@link #COMPARISON_TOKENS}, this is unconditional — it matches the literal
     * factory-with-midnight shape wherever it appears, since this single substring is already
     * precise (it does not fire on the real-anchor-with-fallback shape
     * {@code anchoredAt(dayStart != null ? dayStart : LocalTime.MIDNIGHT)}, which is a genuine
     * desk anchor, not a pending one).
     */
    private static final List<String> MIDNIGHT_ANCHOR_TOKENS = List.of(
            "anchoredAt(LocalTime.MIDNIGHT)");

    /** DayWindow is the implementation of the rule, so it is the one file exempt from it. */
    private static final String IMPLEMENTATION_CLASS = "com.wfm.util.DayWindow";

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /**
     * The fixture root for the pipeline-level red-proof below. Lives under {@code
     * src/test/resources} — never {@code src/main/java} — so a killed or crashed run cannot leave
     * a deliberately offending {@code .java} file inside a compiled source set.
     */
    private static final Path COMPARISON_OFFENDER_ROOT =
            Path.of("src", "test", "resources", "midnight-guard-offender");

    @Test
    void rawTimeArithmeticInProductionCode_matchesTheAllowlistExactly() throws IOException {
        Set<String> derived = scanProductionSources(SOURCE_ROOT, MidnightTimeArithmeticGuardTest::isRawArithmetic);
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
        Set<String> derived = scanProductionSources(SOURCE_ROOT, MidnightTimeArithmeticGuardTest::isRawComparison);
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

    /**
     * D-07's third family: every {@code DayWindow.anchoredAt(LocalTime.MIDNIGHT)} binding under
     * {@code src/main/java} must equal the "Permitted midnight anchors" allowlist exactly. A
     * midnight anchor always compiles, is always correct on today's data (every desk's anchor is
     * {@code 00:00} today), and is indistinguishable in a diff from a genuine desk anchor — the one
     * substitution that would silently restore the pre-migration semantics on a live desk once a
     * non-midnight anchor exists. Same set-equality contract as the two scans above: widening either
     * direction turns this into decoration.
     */
    @Test
    void midnightAnchorsInProductionCode_matchesTheMidnightAnchorAllowlistExactly() throws IOException {
        Set<String> derived = scanProductionSources(SOURCE_ROOT, MidnightTimeArithmeticGuardTest::isMidnightAnchor);
        Set<String> allowlist = parseMidnightAnchorAllowlist();

        Set<String> notAllowlisted = new HashSet<>(derived);
        notAllowlisted.removeAll(allowlist);
        Set<String> staleEntries = new HashSet<>(allowlist);
        staleEntries.removeAll(derived);

        assertThat(derived)
                .as("""
                        DayWindow.anchoredAt(LocalTime.MIDNIGHT) bindings in src/main/java must \
                        equal the midnight-anchor allowlist in %s exactly.

                        NEW, not allowlisted -- either reach the desk's real anchor at this call \
                        site, or add the line to the allowlist WITH a note saying why no desk \
                        anchor is reachable here and which requirement owns its removal: %s

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
                        offenders.add(toFullyQualifiedName(SOURCE_ROOT, file) + " :: " + header);
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
        // comparison and midnight-anchor allowlists -- an empty section would make the matching
        // assertion above vacuous in exactly the same way.
        assertThat(parseAllowlist()).isNotEmpty();
        assertThat(parseComparisonAllowlist()).isNotEmpty();
        assertThat(parseMidnightAnchorAllowlist()).isNotEmpty();
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
    void theMidnightAnchorScanDetectsAFreshOccurrence() {
        // Proves the matcher is live: the canonical pending-anchor shape is caught, a comment
        // mentioning it is not, and a genuine desk anchor (with a defensive midnight FALLBACK, not
        // a midnight anchor itself) does not trip the scan either -- it is a real anchor, not a
        // pending one, so it has no business in this allowlist.
        assertThat(isMidnightAnchor("DayWindow window = DayWindow.anchoredAt(LocalTime.MIDNIGHT);"))
                .isTrue();
        assertThat(isMidnightAnchor(
                "private static final DayWindow PENDING_DESK_ANCHOR = DayWindow.anchoredAt(LocalTime.MIDNIGHT);"))
                .isTrue();
        assertThat(isMidnightAnchor("// DayWindow.anchoredAt(LocalTime.MIDNIGHT) is permitted here"))
                .isFalse();
        assertThat(isMidnightAnchor(
                "DayWindow window = DayWindow.anchoredAt(dayStart != null ? dayStart : LocalTime.MIDNIGHT);"))
                .isFalse();
        assertThat(isMidnightAnchor("DayWindow window = DayWindow.anchoredAt(desk.getDayStart());"))
                .isFalse();
    }

    /**
     * {@link #theMidnightAnchorScanDetectsAFreshOccurrence} proves the matcher PREDICATE is live
     * against synthetic strings; this proves the SET COMPARISON itself rejects both directions —
     * an unlisted new occurrence, and an allowlisted entry whose line has gone. The exact assertion
     * shape used here ({@code containsExactlyInAnyOrderElementsOf}) is the one the real test above
     * uses; widening it to a subset/{@code containsAll} check would make both of these proofs pass
     * even though the guard had become decoration.
     */
    @Test
    @DisplayName("the midnight-anchor scan is set-equality, not a subset check")
    void midnightAnchorScan_failsOnBothAnUnlistedOccurrenceAndAStaleEntry() {
        String line = "com.wfm.example.Example :: DayWindow window = DayWindow.anchoredAt(LocalTime.MIDNIGHT);";

        // NEW direction: one real occurrence, allowlist empty -- the unlisted occurrence must fail.
        assertThatThrownBy(() -> assertThat(Set.of(line))
                .containsExactlyInAnyOrderElementsOf(Set.of()))
                .isInstanceOf(AssertionError.class);

        // STALE direction: allowlist carries the entry, derived is empty (its line has gone) --
        // must fail just as loudly, never silently tolerated.
        assertThatThrownBy(() -> assertThat(Set.<String>of())
                .containsExactlyInAnyOrderElementsOf(Set.of(line)))
                .isInstanceOf(AssertionError.class);
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

    /**
     * {@link #theScanDetectsAFreshOccurrence} and {@link #theComparisonScanDetectsAFreshOccurrence}
     * above prove the matcher PREDICATE is live against synthetic strings, and that the receiver
     * heuristic cuts both ways -- but neither exercises walk, comment-strip or set-compare. That
     * leaves one failure mode uncovered: if the final assertion were weakened from set equality to
     * a subset check, both of those red-proofs would still pass, because neither ever reaches the
     * set-compare step. This test points the exact same shared pipeline ({@link
     * #scanProductionSources}) at {@link #COMPARISON_OFFENDER_ROOT} -- a fixture holding one
     * tracked, never-compiled synthetic offender -- and proves walk, comment-strip, match and
     * set-compare are all live together.
     */
    @Test
    @DisplayName("the guard can go red through its whole pipeline, not only its matcher")
    void pipelineRedProof_walkStripMatchAndSetCompareAreAllLive() throws IOException {
        // 1. A guard on the guard's own fixture: a moved or renamed file must fail loudly here
        // rather than silently emptying the scan below.
        List<Path> fixtureFiles;
        try (Stream<Path> files = Files.walk(COMPARISON_OFFENDER_ROOT)) {
            fixtureFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(fixtureFiles)
                .as("the pipeline red-proof fixture must hold exactly one .java file under %s",
                        COMPARISON_OFFENDER_ROOT)
                .hasSize(1);

        // 2. The shared walk, pointed at the fixture root with the comparison matcher, must find
        // exactly the one real offending line -- not two, which would mean comment-stripping
        // silently stopped working, since the fixture also carries a commented-out occurrence of
        // the same token.
        Set<String> derived = scanProductionSources(
                COMPARISON_OFFENDER_ROOT, MidnightTimeArithmeticGuardTest::isRawComparison);
        assertThat(derived)
                .as("walk + comment-strip + match against the fixture must yield exactly one entry")
                .hasSize(1);

        // 3. The step the matcher-level red-proofs above cannot reach: asserting set equality
        // between that one-entry result and an empty expected set must throw an AssertionError,
        // proving the set-compare step is live too -- not only the matcher predicate. This is the
        // exact shape of the real assertions above, with an intentionally wrong expected set.
        assertThatThrownBy(() -> assertThat(derived).containsExactlyInAnyOrderElementsOf(Set.of()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void missingAllowlistHeading_failsLoudly() {
        // The parser must never silently return an empty set for a malformed resource -- that is
        // the one way this guard could pass while enforcing nothing. Proven generically for an
        // arbitrary heading, and specifically for all three concrete allowlist headings this class
        // parses, so none of the arithmetic, comparison or midnight-anchor assertions can go
        // vacuous.
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
        assertThatThrownBy(() -> parseFencedBlock(
                        "# Empty resource\n\nno headings here", MIDNIGHT_ANCHOR_ALLOWLIST_HEADING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MIDNIGHT_ANCHOR_ALLOWLIST_HEADING);
    }

    // --- scanning ---

    /**
     * Walks {@code root} once, collecting every line {@code matcher} accepts. Shared by the
     * arithmetic assertion, the comparison assertion and the pipeline-level red-proof below, so
     * there is one walk implementation, not several that can drift apart. {@code root} is a
     * parameter so the red-proof can point the exact same pipeline at a fixture directory; every
     * real (non-red-proof) assertion always passes {@link #SOURCE_ROOT} explicitly — if the
     * parameterisation ever left one of them pointed at a fixture directory instead, {@link
     * #theScanActuallySeesProductionSource} asserts against {@code SOURCE_ROOT} by name and would
     * fail rather than pass vacuously.
     */
    private Set<String> scanProductionSources(Path root, Predicate<String> matcher) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            for (Path file : javaFiles) {
                String fqcn = toFullyQualifiedName(root, file);
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

    /** True when this source line binds a {@code DayWindow.anchoredAt(LocalTime.MIDNIGHT)} pending
     *  anchor, once comments are discarded (D-07's third family). */
    private static boolean isMidnightAnchor(String rawLine) {
        String code = stripComment(rawLine);
        if (code.isEmpty()) {
            return false;
        }
        return MIDNIGHT_ANCHOR_TOKENS.stream().anyMatch(code::contains);
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

    /** Relativises {@code file} against {@code root} -- {@code root} is a parameter for the same
     *  reason {@link #scanProductionSources} takes one: the red-proof below relativises against
     *  the fixture root, not {@link #SOURCE_ROOT}. */
    private static String toFullyQualifiedName(Path root, Path file) {
        String relative = root.relativize(file).toString();
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

    private Set<String> parseMidnightAnchorAllowlist() throws IOException {
        Set<String> entries = parseFencedBlock(readResource(), MIDNIGHT_ANCHOR_ALLOWLIST_HEADING);
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                    RESOURCE + " parsed to an EMPTY midnight-anchor allowlist under '"
                            + MIDNIGHT_ANCHOR_ALLOWLIST_HEADING
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
