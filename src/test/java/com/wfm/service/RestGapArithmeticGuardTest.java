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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-02's structural tripwire against a THIRD occurrence of the overnight-predecessor rest-gap
 * defect (REST-01, REST-02, REST-05) — a HYBRID of two precedent guards in this project:
 * {@link RestWaiverPredicateGuardTest} supplies the registry/table/allowlist parsing mechanism,
 * and {@link MidnightTimeArithmeticGuardTest} supplies the comment-stripping line-level scan
 * technique.
 *
 * <p><strong>What this proves, in two independent parts:</strong>
 * <ol>
 *   <li>No line under {@code src/main/java}, outside {@link com.wfm.util.DayWindow} itself,
 *   composes {@code DayWindow.MINUTES_PER_DAY} with a single-argument anchored end accessor call
 *   and a subtraction on one line — the exact "minutes remaining in the predecessor's business
 *   day" idiom that was inlined twice and drifted once.</li>
 *   <li>The set of production classes that call {@code DayWindow.anchoredWrappedEndMinute} —
 *   excluding {@code DayWindow} as the implementation — equals EXACTLY the registry's two-entry
 *   allowlist.</li>
 * </ol>
 *
 * <p><strong>Set equality only, never subset or containment.</strong> The call-site assertion uses
 * {@code containsExactlyInAnyOrderElementsOf}. Widening it to a weaker one-directional containment
 * check — a "this set is a subset of that one" test, an "any element of this matches" test, or a
 * bare {@code .contains(...)} on the derived set — converts this guard into decoration — see
 * {@link RestWaiverPredicateGuardTest}'s and {@link MidnightTimeArithmeticGuardTest}'s own javadoc
 * warnings against exactly this failure mode.
 *
 * <p><strong>This is NOT a blanket ban on the single-argument anchored end accessor.</strong> That
 * accessor is legitimate wherever only one time within one business day is being read — see
 * {@code rest-gap-arithmetic-guard.md}'s "Known scope boundaries" section. Only the specific
 * composition this matcher targets is forbidden outside the sanctioned callers.
 *
 * <p><strong>A false positive fails safe.</strong> Every scan here is purely textual. A string
 * literal or comment containing one of these tokens would be stripped (comments) or still register
 * as a match (string literals) — an acceptable trade that forces a human to look at a diff rather
 * than silently missing a real second composition.
 *
 * <p>No Spring context, no Testcontainers, no database — reads the registry off the test classpath
 * and walks {@code src/main/java} on disk, exactly as both precedents do.
 */
class RestGapArithmeticGuardTest {

    private static final String REGISTRY_RESOURCE = "rest-gap-arithmetic-guard.md";

    private static final String CALL_SITE_ALLOWLIST_HEADING = "### anchoredWrappedEndMinute call sites";

    private static final String PRIMITIVE_CALL_TOKEN = "anchoredWrappedEndMinute";

    private static final String IMPLEMENTATION_CLASS = "com.wfm.util.DayWindow";

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|\\s*$");

    /**
     * The single-argument anchored end accessor's name, immediately followed by an open
     * parenthesis — built by concatenation rather than spelled out as a literal. Spelling it out in
     * full would make this very file a textual match for any future whole-file grep auditing the
     * forbidden shape, which is exactly the kind of self-reference that makes a guard hard to
     * audit. {@code anchoredWrappedEndMinute(} does NOT contain this token — the character
     * immediately after {@code anchored} is {@code W}, not {@code E} — so the wrap-aware method's
     * own name never trips this matcher.
     */
    private static final String END_ACCESSOR_CALL_TOKEN = "anchored" + "End" + "Minute(";

    // --- Set derivations over live src/main/java (the two independent scans) ---

    @Test
    @DisplayName("no line outside DayWindow composes MINUTES_PER_DAY, a subtraction and the single-argument end accessor")
    void wrapBlindCompositionScan_returnsEmptySet() throws IOException {
        Set<String> found = scanProductionSources(
                SOURCE_ROOT, RestGapArithmeticGuardTest::isWrapBlindPredecessorEndComposition);
        assertThat(found)
                .as("A wrap-blind predecessor-end composition was found outside DayWindow -- this "
                        + "must be removed and replaced with a call to "
                        + "DayWindow.anchoredWrappedEndMinute(start, end), never widened or "
                        + "allowlisted: %s", found)
                .isEmpty();
    }

    @Test
    @DisplayName("every production class invoking anchoredWrappedEndMinute matches the registry allowlist exactly")
    void callSiteSet_matchesTheRegistryAllowlistExactly() throws IOException {
        Set<String> derived = deriveCallSiteClasses();
        Set<String> allowlist = parseAllowlist(CALL_SITE_ALLOWLIST_HEADING);

        Set<String> missing = new HashSet<>(derived);
        missing.removeAll(allowlist);
        Set<String> stale = new HashSet<>(allowlist);
        stale.removeAll(derived);

        assertThat(derived)
                .as("Classes invoking DayWindow.anchoredWrappedEndMinute in src/main/java must equal "
                        + "the registry's call-site allowlist exactly. Missing a row for (add it to "
                        + "rest-gap-arithmetic-guard.md's call-site table AND allowlist): %s. Stale "
                        + "entries naming a class that no longer calls it (remove them): %s.",
                        missing, stale)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    @Test
    void allowlist_parsesAsNonEmpty() throws IOException {
        // An empty expected set would make the set-equality assertion above vacuously satisfiable
        // if production code ever stopped calling the primitive at all -- parseAllowlist already
        // throws on empty, so reaching this assertion is itself part of the proof.
        assertThat(parseAllowlist(CALL_SITE_ALLOWLIST_HEADING)).isNotEmpty();
    }

    // --- Table structural integrity ---

    @Test
    void everyProvingTestNamedInTheTable_resolvesToAnExistingClass() throws IOException {
        List<TableRow> rows = parseTableRows();
        assertThat(rows).as("Call-site table must have at least one row per allowlisted call site")
                .hasSizeGreaterThanOrEqualTo(2);

        Path srcTestJava = Path.of("src", "test", "java");
        for (TableRow row : rows) {
            assertThat(row.path()).as("Row '%s': Entry point cell must not be blank", row).isNotBlank();
            assertThat(row.sourceFile()).as("Row '%s': Source file cell must not be blank", row).isNotBlank();
            assertThat(row.effect()).as("Row '%s': 'What must hold' cell must not be blank", row).isNotBlank();
            assertThat(row.provingTest()).as("Row '%s': Proving test cell must not be blank", row).isNotBlank();

            String fqcn = resolveFullyQualifiedName(srcTestJava, row.provingTest());
            assertThatCode(() -> Class.forName(fqcn))
                    .as("Proving test '%s' named in rest-gap-arithmetic-guard.md must resolve via "
                            + "Class.forName (fqcn=%s) -- a renamed or deleted test must not leave a "
                            + "row pointing at nothing", row.provingTest(), fqcn)
                    .doesNotThrowAnyException();
        }
    }

    // --- The matcher's own test-of-the-test ---

    @Test
    @DisplayName("the wrap-blind composition matcher is live against synthetic strings, and does not fire on either corrected call shape")
    void wrapBlindMatcher_isLiveAgainstSyntheticStrings() {
        // The canonical reintroduction of the bug: MINUTES_PER_DAY, a subtraction, and the
        // single-argument accessor's call, all on one line -- the RestSpan-shaped offender.
        assertThat(isWrapBlindPredecessorEndComposition(
                "int remaining = DayWindow.MINUTES_PER_DAY - window.anchoredEndMinute(prev.endTime());"))
                .isTrue();
        // The SolverService-shaped variant.
        assertThat(isWrapBlindPredecessorEndComposition(
                "predecessorEndMinute = DayWindow.MINUTES_PER_DAY - window.anchoredEndMinute(prior.endTime());"))
                .isTrue();

        // A comment mentioning the identical text must not trip the scan.
        assertThat(isWrapBlindPredecessorEndComposition(
                "// int remaining = DayWindow.MINUTES_PER_DAY - window.anchoredEndMinute(prev.endTime());"))
                .isFalse();
        assertThat(isWrapBlindPredecessorEndComposition(
                "* remaining = DayWindow.MINUTES_PER_DAY - window.anchoredEndMinute(prev.endTime());"))
                .isFalse();

        // The corrected call shape -- a predecessor's start AND end, through the wrap-aware
        // primitive -- must never be mistaken for the forbidden composition, since the accessor's
        // name does not contain the single-argument accessor's name followed by an open paren.
        assertThat(isWrapBlindPredecessorEndComposition(
                "predecessorEndMinute = window.anchoredWrappedEndMinute(prior.startTime(), prior.endTime());"))
                .isFalse();

        // The bestGap-shaped line: MINUTES_PER_DAY and a subtraction, but no accessor call.
        assertThat(isWrapBlindPredecessorEndComposition(
                "int bestGap = DayWindow.MINUTES_PER_DAY - predecessorEndMinute + successorLatestStartMinute;"))
                .isFalse();

        // The windowEndMinute-shaped line: an accessor call, but neither MINUTES_PER_DAY nor a
        // subtraction.
        assertThat(isWrapBlindPredecessorEndComposition(
                "int windowEndMinute = window.anchoredEndMinute(config.endTime());"))
                .isFalse();
    }

    /**
     * The test-of-the-test for the set-equality pipeline: proves the allowlist assertion can
     * actually go red, not merely that it has never been observed to -- the same property
     * {@link RestWaiverPredicateGuardTest} and {@link MidnightTimeArithmeticGuardTest} both require
     * of their own guards.
     */
    @Test
    @DisplayName("a deliberately broken allowlist is detected as a mismatch against the real derived set")
    void deliberatelyBrokenAllowlist_isDetectedAsAMismatch() throws IOException {
        Set<String> derived = deriveCallSiteClasses();
        Set<String> brokenAllowlist = new HashSet<>(parseAllowlist(CALL_SITE_ALLOWLIST_HEADING));
        String removed = brokenAllowlist.iterator().next();
        brokenAllowlist.remove(removed);

        assertThatThrownBy(() ->
                assertThat(derived)
                        .as("test-of-the-test: this assertion is EXPECTED to fail")
                        .containsExactlyInAnyOrderElementsOf(brokenAllowlist))
                .isInstanceOf(AssertionError.class);
    }

    // --- The matcher itself ---

    /**
     * True when this source line composes the wrap-blind "minutes remaining in the predecessor's
     * business day" idiom: {@code DayWindow.MINUTES_PER_DAY} minus a single-argument anchored end
     * accessor call, on one line. Deliberately narrow: it does NOT fire on the wrap-aware method,
     * because that method's name does not contain the single-argument accessor's name followed by
     * an open parenthesis, and it does NOT fire on either confirmed-clean single-token line (a bare
     * {@code MINUTES_PER_DAY}-and-subtraction line with no accessor call, or a bare accessor call
     * with neither {@code MINUTES_PER_DAY} nor a subtraction).
     */
    private static boolean isWrapBlindPredecessorEndComposition(String rawLine) {
        String code = stripComment(rawLine);
        if (code.isEmpty()) {
            return false;
        }
        boolean hasMinutesPerDay = code.contains("MINUTES_PER_DAY");
        boolean hasEndAccessorCall = code.contains(END_ACCESSOR_CALL_TOKEN);
        boolean hasSubtraction = code.contains("-");
        return hasMinutesPerDay && hasEndAccessorCall && hasSubtraction;
    }

    // --- Derivation over live src/main/java ---

    private static Set<String> deriveCallSiteClasses() throws IOException {
        return scanClasses(code -> code.contains(PRIMITIVE_CALL_TOKEN))
                .stream()
                .filter(fqcn -> !fqcn.equals(IMPLEMENTATION_CLASS))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private static Set<String> scanClasses(java.util.function.Predicate<String> wholeFileMatcher) throws IOException {
        if (!Files.isDirectory(SOURCE_ROOT)) {
            throw new IllegalStateException("src/main/java not found at " + SOURCE_ROOT
                    + " -- guard cannot scan production code without it");
        }
        Set<String> classes = new HashSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (wholeFileMatcher.test(text)) {
                    classes.add(toFullyQualifiedName(SOURCE_ROOT, file));
                }
            }
        }
        return classes;
    }

    /** Walks {@code root}, collecting every non-comment line {@code matcher} accepts, tagged with
     *  its class's fully-qualified name -- the same shared-walk shape
     *  {@code MidnightTimeArithmeticGuardTest.scanProductionSources} uses. {@code DayWindow} itself
     *  is excluded: it IS the one permitted implementation, exactly as {@code RestWaiverLookup} is
     *  excluded from the waiver guard's scan and {@code DayWindow} is excluded from the midnight
     *  guard's scan. */
    private static Set<String> scanProductionSources(Path root, java.util.function.Predicate<String> matcher)
            throws IOException {
        Set<String> found = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            for (Path file : javaFiles) {
                String fqcn = toFullyQualifiedName(root, file);
                if (IMPLEMENTATION_CLASS.equals(fqcn)) {
                    continue;
                }
                for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (matcher.test(rawLine)) {
                        found.add(fqcn + " :: " + stripComment(rawLine));
                    }
                }
            }
        }
        return found;
    }

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

    private static String toFullyQualifiedName(Path root, Path javaFile) {
        String relative = root.relativize(javaFile).toString();
        String withoutSuffix = relative.substring(0, relative.length() - ".java".length());
        return withoutSuffix.replace(java.io.File.separatorChar, '.');
    }

    /** Same resolution shape as {@code RestWaiverPredicateGuardTest.resolveFullyQualifiedName}:
     *  the FQCN of the single {@code .java} file under {@code srcTestJava} whose simple name
     *  (filename minus extension) matches {@code simpleName}. */
    private static String resolveFullyQualifiedName(Path srcTestJava, String simpleName) throws IOException {
        if (!Files.isDirectory(srcTestJava)) {
            throw new IllegalStateException("src/test/java not found at " + srcTestJava);
        }
        List<Path> matches;
        try (Stream<Path> files = Files.walk(srcTestJava)) {
            matches = files.filter(p -> p.getFileName().toString().equals(simpleName + ".java")).toList();
        }
        if (matches.isEmpty()) {
            throw new IllegalStateException("No source file named " + simpleName
                    + ".java found under " + srcTestJava);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Multiple source files named " + simpleName
                    + ".java found under " + srcTestJava + ": " + matches);
        }
        return toFullyQualifiedName(srcTestJava, matches.get(0));
    }

    // --- Table + allowlist parsing ---

    private static Set<String> parseAllowlist(String heading) throws IOException {
        String text = readRegistryResource();
        int headingIdx = text.indexOf(heading);
        if (headingIdx < 0) {
            throw new IllegalStateException("Heading '" + heading + "' not found in " + REGISTRY_RESOURCE);
        }
        int fenceStart = text.indexOf("```", headingIdx);
        if (fenceStart < 0) {
            throw new IllegalStateException("No fenced code block found after heading '" + heading + "'");
        }
        int contentStart = text.indexOf('\n', fenceStart) + 1;
        int fenceEnd = text.indexOf("```", contentStart);
        if (fenceEnd < 0) {
            throw new IllegalStateException("Unterminated fenced code block after heading '" + heading + "'");
        }
        String block = text.substring(contentStart, fenceEnd);

        Set<String> entries = new LinkedHashSet<>();
        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("Allowlist under heading '" + heading + "' is empty -- an "
                    + "empty expected set would make the set-equality assertion trivially satisfiable "
                    + "only when NO class anywhere calls the primitive, which defeats the guard");
        }
        return entries;
    }

    private static List<TableRow> parseTableRows() throws IOException {
        String text = readRegistryResource();
        List<TableRow> rows = new ArrayList<>();
        boolean headerSeen = false;
        for (String line : text.split("\n")) {
            Matcher matcher = TABLE_ROW.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String[] cells = matcher.group(1).split("\\|", -1);
            if (cells.length < 4) {
                continue;
            }
            String firstCell = cells[0].trim();
            if (firstCell.equals("Entry point")) {
                headerSeen = true;
                continue;
            }
            if (!headerSeen) {
                continue;
            }
            if (firstCell.replace(" ", "").chars().allMatch(c -> c == '-')) {
                continue;
            }
            rows.add(new TableRow(stripBackticks(cells[0]), stripBackticks(cells[1]),
                    stripBackticks(cells[2]), stripBackticks(cells[3])));
        }
        return rows;
    }

    private static String stripBackticks(String cell) {
        return cell.trim().replace("`", "").trim();
    }

    private static String readRegistryResource() throws IOException {
        try (InputStream in = RestGapArithmeticGuardTest.class
                .getClassLoader().getResourceAsStream(REGISTRY_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(REGISTRY_RESOURCE + " not found on the test classpath -- "
                        + "the rest-gap arithmetic registry is missing, so this guard has nothing to "
                        + "enforce against");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record TableRow(String path, String sourceFile, String effect, String provingTest) {
        String entryPoint() {
            return path();
        }
    }
}
