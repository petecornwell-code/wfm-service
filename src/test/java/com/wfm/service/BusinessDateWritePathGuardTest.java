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
 * This structural completeness guard (BDAY-02) — the set of production classes calling
 * {@link com.wfm.model.Timeslot#setBusinessDate} must equal, in BOTH directions, the allowlist
 * recorded in {@code src/test/resources/bday-02-write-paths.md}. A new class calling the setter
 * without an accompanying allowlist entry fails the build; a stale allowlist entry naming a class
 * that no longer calls it also fails the build.
 *
 * <p><strong>Why two allowlists, not one.</strong> A single-set design would assume exactly one
 * legitimate caller ({@link TimeslotGeneratorService}). That assumption does not hold: V53 added
 * {@code business_date NOT NULL} with no Java-side default, so {@link ScheduleService}'s
 * accept-time snapshot copy must set the field too, via {@code
 * snapshot.setBusinessDate(live.getBusinessDate())}. That call is fundamentally different in kind
 * from {@code TimeslotGeneratorService}'s: it PROPAGATES an already-derived value from one row to
 * another rather than DERIVING a fresh one, so it cannot introduce a value SOLV-01's joins have
 * never seen before. Rather than weaken the guard to admit an unexplained second entry (or worse,
 * a subset check), this class scans the two kinds of call SEPARATELY and pins each to its own
 * exactly-one-entry allowlist — {@link #DERIVING_HEADING} for classes that compute a business date,
 * {@link #PROPAGATING_HEADING} for classes that only copy one forward. A third writer of either
 * kind — deriving or propagating — still fails the build. See {@code bday-02-write-paths.md}'s
 * "Two writers by design, not by accident" section for the full account.
 *
 * <p><strong>Set equality only, never subset or containment.</strong> Both assertions below use
 * {@code containsExactlyInAnyOrderElementsOf}. Widening either to {@code isSubsetOf}, {@code
 * containsAnyOf}, or a bare {@code .contains(...)} turns this guard into decoration — see {@link
 * UsualShiftWritePathGuardTest} and {@link MidnightTimeArithmeticGuardTest}, which warn against
 * exactly this failure mode in their own javadoc.
 *
 * <p><strong>A false positive fails safe.</strong> This is a purely TEXTUAL, comment-stripped scan
 * of {@code src/main/java} (mirroring {@link MidnightTimeArithmeticGuardTest}'s technique): a
 * string literal or comment mentioning {@code setBusinessDate(} would not match, since comment
 * lines are stripped before scanning, but an unrelated method happening to be named {@code
 * setBusinessDate} on a different type would. That is an acceptable failure mode — it forces a
 * human to look at a diff rather than silently missing a real new writer. {@link
 * com.wfm.model.Timeslot} itself is excluded from the scan (the setter's own declaration, not a
 * call), the same way {@code DayWindow} is excluded from {@link MidnightTimeArithmeticGuardTest}'s
 * scan as the class that IS the rule rather than a caller of it.
 *
 * <p>No Spring context, no database.
 */
class BusinessDateWritePathGuardTest {

    private static final String RESOURCE = "bday-02-write-paths.md";

    private static final String DERIVING_HEADING = "### Timeslot#setBusinessDate call sites";
    private static final String PROPAGATING_HEADING =
            "### Timeslot#setBusinessDate call sites -- snapshot-copy (propagating, not deriving)";

    /** The setter's own declaration is not a call site. */
    private static final String DECLARING_CLASS = "com.wfm.model.Timeslot";

    private static final String CALL_TOKEN = "setBusinessDate(";
    private static final String PROPAGATE_MARKER = ".getBusinessDate()";

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    @Test
    @DisplayName("deriving call sites (fresh business-date computation) match the allowlist exactly")
    void derivingCallSites_matchTheAllowlistExactly() throws IOException {
        Set<String> derived = scanCallSites(false);
        Set<String> allowlist = parseAllowlist(DERIVING_HEADING);
        assertSetEquality(derived, allowlist, "DERIVING");
    }

    @Test
    @DisplayName("propagating call sites (snapshot copy of an already-derived value) match the allowlist exactly")
    void propagatingCallSites_matchTheAllowlistExactly() throws IOException {
        Set<String> derived = scanCallSites(true);
        Set<String> allowlist = parseAllowlist(PROPAGATING_HEADING);
        assertSetEquality(derived, allowlist, "PROPAGATING (snapshot-copy)");
    }

    private static void assertSetEquality(Set<String> derived, Set<String> allowlist, String kind) {
        Set<String> notAllowlisted = new HashSet<>(derived);
        notAllowlisted.removeAll(allowlist);
        Set<String> staleEntries = new HashSet<>(allowlist);
        staleEntries.removeAll(derived);

        assertThat(derived)
                .as("""
                        %s Timeslot#setBusinessDate call sites in src/main/java must equal the \
                        allowlist in %s exactly, in BOTH directions. Widening this assertion to a \
                        subset, any-of, or bare containment check turns the guard into decoration \
                        (see UsualShiftWritePathGuardTest and MidnightTimeArithmeticGuardTest).

                        NEW, not allowlisted -- business_date must never be written by anything but \
                        the timeslot generator (a second writer is a silent non-join for SOLV-01's \
                        joins, not an error). Add a row to bday-02-write-paths.md describing what \
                        the new writer guarantees before allowlisting it: %s

                        STALE, allowlisted but no longer present -- remove the entry: %s""",
                        kind, RESOURCE, notAllowlisted, staleEntries)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    @Test
    void bothAllowlists_parseAsNonEmpty() throws IOException {
        // An empty expected set would make the set-equality assertions above vacuously
        // satisfiable if src/main/java ever stopped calling the setter at all -- parseAllowlist
        // throws on empty, so reaching these assertions is itself part of the proof.
        assertThat(parseAllowlist(DERIVING_HEADING)).isNotEmpty();
        assertThat(parseAllowlist(PROPAGATING_HEADING)).isNotEmpty();
    }

    @Test
    void theScanActuallySeesProductionSource() throws IOException {
        // Guards the guard: a wrong SOURCE_ROOT would make the scan return an empty set, and set
        // equality against a non-empty allowlist would then fail loudly -- but only if there IS
        // source to read in the first place.
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            assertThat(files.filter(p -> p.toString().endsWith(".java")).count())
                    .as("scan must find production sources under %s", SOURCE_ROOT)
                    .isGreaterThan(50);
        }
    }

    @Test
    @DisplayName("the matcher is live: a synthetic call matches, a comment mentioning it does not")
    void theScanDetectsAFreshOccurrence() {
        assertThat(isDerivingCall("ts.setBusinessDate(date);")).isTrue();
        assertThat(isPropagatingCall("snapshot.setBusinessDate(live.getBusinessDate());")).isTrue();

        // Each classification is exclusive of the other.
        assertThat(isDerivingCall("snapshot.setBusinessDate(live.getBusinessDate());")).isFalse();
        assertThat(isPropagatingCall("ts.setBusinessDate(date);")).isFalse();

        // Comment lines never match, in either classification.
        assertThat(isDerivingCall("// ts.setBusinessDate(date); explained here")).isFalse();
        assertThat(isPropagatingCall(
                "* snapshot.setBusinessDate(live.getBusinessDate()) is a propagating copy")).isFalse();
    }

    @Test
    void missingAllowlistHeading_failsLoudly() {
        assertThatThrownBy(() -> parseFencedBlock("# Empty resource\n\nno headings here", DERIVING_HEADING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DERIVING_HEADING);
        assertThatThrownBy(() -> parseFencedBlock("# Empty resource\n\nno headings here", PROPAGATING_HEADING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PROPAGATING_HEADING);
    }

    @Test
    void emptyAllowlist_isRejectedAsVacuous() {
        String markdown = DERIVING_HEADING + "\n\n```\n```\n";
        assertThatThrownBy(() -> requireNonEmpty(parseFencedBlock(markdown, DERIVING_HEADING), DERIVING_HEADING))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * The test-of-the-test: proves this guard is actually CAPABLE of going red, not merely that
     * it has never been observed to (the same property SolverQualityGuardTest and
     * SolverUsualShiftWritePathGuardTest also require).
     */
    @Test
    void deliberatelyBrokenAllowlist_isDetectedAsAMismatch() throws IOException {
        Set<String> derived = scanCallSites(false);
        Set<String> allowlistWithOneEntryRemoved = new HashSet<>(parseAllowlist(DERIVING_HEADING));
        allowlistWithOneEntryRemoved.clear();

        assertThatThrownBy(() ->
                assertThat(derived)
                        .as("test-of-the-test: this assertion is EXPECTED to fail")
                        .containsExactlyInAnyOrderElementsOf(allowlistWithOneEntryRemoved))
                .isInstanceOf(AssertionError.class);
    }

    // --- scanning src/main/java ---

    /**
     * Walks {@code src/main/java}, comment-stripped, and returns the set of fully-qualified class
     * names containing a call to {@code setBusinessDate(} whose classification (deriving vs.
     * propagating) matches {@code propagating}. {@link #DECLARING_CLASS} is skipped entirely —
     * the setter's own declaration line also contains the literal text {@code setBusinessDate(}
     * but is not a call.
     */
    private static Set<String> scanCallSites(boolean propagating) throws IOException {
        if (!Files.isDirectory(SOURCE_ROOT)) {
            throw new IllegalStateException(
                    "src/main/java not found at " + SOURCE_ROOT + " -- guard cannot scan production "
                            + "code without it");
        }
        Set<String> classes = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String fqcn = toFullyQualifiedName(file);
                if (DECLARING_CLASS.equals(fqcn)) {
                    continue;
                }
                for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String code = stripComment(rawLine);
                    if (code.isEmpty()) {
                        continue;
                    }
                    String arg = extractCallArgument(code);
                    if (arg == null) {
                        continue;
                    }
                    boolean isPropagatingCall = arg.contains(PROPAGATE_MARKER);
                    if (isPropagatingCall == propagating) {
                        classes.add(fqcn);
                    }
                }
            }
        }
        return classes;
    }

    private static boolean isDerivingCall(String rawLine) {
        String arg = extractCallArgument(stripComment(rawLine));
        return arg != null && !arg.contains(PROPAGATE_MARKER);
    }

    private static boolean isPropagatingCall(String rawLine) {
        String arg = extractCallArgument(stripComment(rawLine));
        return arg != null && arg.contains(PROPAGATE_MARKER);
    }

    /**
     * Returns the argument text of a {@code setBusinessDate(...)} call on this (already
     * comment-stripped) line, or {@code null} if the line contains no such call. Balances
     * parentheses from the call's opening paren so an argument that is itself a method call
     * (e.g. {@code live.getBusinessDate()}) is captured whole.
     */
    private static String extractCallArgument(String code) {
        int idx = code.indexOf(CALL_TOKEN);
        if (idx < 0) {
            return null;
        }
        int start = idx + CALL_TOKEN.length();
        int depth = 1;
        int i = start;
        while (i < code.length() && depth > 0) {
            char c = code.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            i++;
        }
        return depth == 0 ? code.substring(start, i - 1) : code.substring(start);
    }

    /**
     * Returns the code portion of a source line: empty for a line that is wholly a comment
     * ({@code //}, a javadoc continuation {@code *}, or a block opener {@code /*}), and otherwise
     * the line trimmed with any trailing {@code //} comment removed. Mirrors {@code
     * MidnightTimeArithmeticGuardTest}'s {@code stripComment}.
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

    private static Set<String> parseAllowlist(String heading) throws IOException {
        return requireNonEmpty(parseFencedBlock(readResource(), heading), heading);
    }

    private static Set<String> requireNonEmpty(Set<String> entries, String heading) {
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                    "Allowlist under heading '" + heading + "' in " + RESOURCE + " is empty -- an "
                            + "empty expected set would make the set-equality assertion vacuously "
                            + "satisfiable only when NO production class calls setBusinessDate at "
                            + "all, which defeats the guard.");
        }
        return entries;
    }

    private static String readResource() throws IOException {
        try (InputStream in = BusinessDateWritePathGuardTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found on the test classpath -- the "
                        + "BDAY-02 write-path table is missing, so this guard has nothing to enforce "
                        + "against");
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
            throw new IllegalStateException("Heading '" + heading + "' not found in " + RESOURCE);
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
