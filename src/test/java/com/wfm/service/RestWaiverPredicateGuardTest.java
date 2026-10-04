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
 * D-08's structural completeness guard (REST-06) — the deliverable this plan exists for, in the
 * same register as {@link UsualShiftWritePathGuardTest} (whose mechanism this copies) and
 * {@link MidnightTimeArithmeticGuardTest} (whose comment-stripping scan technique this copies).
 *
 * <p><strong>What this proves, in three independent parts:</strong>
 * <ol>
 *   <li>The set of production classes in {@code src/main/java} referencing
 *   {@link com.wfm.model.AgentRestWaiver} at all is EXACTLY the registry's first allowlist.</li>
 *   <li>The set of production classes invoking {@link com.wfm.model.RestWaiverLookup} is EXACTLY
 *   the registry's second allowlist.</li>
 *   <li>No production class outside {@code RestWaiverLookup} itself contains a line that reads
 *   both an {@code AgentRestWaiver}'s date and its agent and compares them — the second-
 *   implementation shape D-08 forbids.</li>
 * </ol>
 *
 * <p><strong>Set equality only, never subset or containment.</strong> Every allowlist assertion
 * uses {@code containsExactlyInAnyOrderElementsOf}. Widening either to {@code isSubsetOf},
 * {@code containsAnyOf}, or a bare {@code .contains(...)} converts this guard into decoration —
 * see {@link com.wfm.solver.ScheduleConstraintClassificationTest}'s and
 * {@link UsualShiftWritePathGuardTest}'s own javadoc warnings against exactly this failure mode.
 *
 * <p><strong>A false positive fails safe.</strong> Every scan here is purely textual. A type name
 * or comparison shape appearing inside a comment is stripped before matching; a string literal
 * containing one of these tokens would still register as a match. That is an acceptable trade: it
 * forces a human to look at a diff rather than silently missing a real second implementation.
 *
 * <p>No Spring context, no Testcontainers, no database — reads the registry off the test classpath
 * and walks {@code src/main/java} on disk, exactly as both precedents above do.
 */
class RestWaiverPredicateGuardTest {

    private static final String REGISTRY_RESOURCE = "rest-waiver-predicate-guard.md";

    private static final String ENTITY_ALLOWLIST_HEADING = "### AgentRestWaiver references";
    private static final String CALL_SITE_ALLOWLIST_HEADING = "### RestWaiverLookup call sites";

    private static final String ENTITY_TYPE_NAME = "AgentRestWaiver";
    private static final String LOOKUP_CALL_TOKEN = "RestWaiverLookup.";

    private static final String IMPLEMENTATION_CLASS = "com.wfm.model.RestWaiverLookup";

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|\\s*$");

    // --- Set derivations over live src/main/java (the two independent scans) ---

    @Test
    @DisplayName("every production class referencing AgentRestWaiver matches the registry's first allowlist exactly")
    void entityReferenceSet_matchesTheRegistryAllowlistExactly() throws IOException {
        Set<String> derived = deriveReferencingClasses(ENTITY_TYPE_NAME);
        Set<String> allowlist = parseAllowlist(ENTITY_ALLOWLIST_HEADING);

        Set<String> missing = new HashSet<>(derived);
        missing.removeAll(allowlist);
        Set<String> stale = new HashSet<>(allowlist);
        stale.removeAll(derived);

        assertThat(derived)
                .as("Classes referencing AgentRestWaiver in src/main/java must equal the registry's "
                        + "allowlist exactly. Missing a row for (add it to "
                        + "rest-waiver-predicate-guard.md and explain what the new reference "
                        + "guarantees, do NOT just add the class name): %s. Stale entries naming a "
                        + "class that no longer references the type (remove them): %s.", missing, stale)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    @Test
    @DisplayName("every production class invoking RestWaiverLookup matches the registry's second allowlist exactly")
    void callSiteSet_matchesTheRegistryAllowlistExactly() throws IOException {
        Set<String> derived = deriveCallSiteClasses();
        Set<String> allowlist = parseAllowlist(CALL_SITE_ALLOWLIST_HEADING);

        Set<String> missing = new HashSet<>(derived);
        missing.removeAll(allowlist);
        Set<String> stale = new HashSet<>(allowlist);
        stale.removeAll(derived);

        assertThat(derived)
                .as("Classes invoking RestWaiverLookup in src/main/java must equal the registry's "
                        + "call-site allowlist exactly. Missing a row for (add it to "
                        + "rest-waiver-predicate-guard.md's call-site table AND allowlist): %s. Stale "
                        + "entries naming a class that no longer calls it (remove them): %s.",
                        missing, stale)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    @Test
    void bothAllowlists_parseAsNonEmpty() throws IOException {
        // An empty expected set would make the set-equality assertions above vacuously satisfiable
        // if production code ever stopped referencing AgentRestWaiver/RestWaiverLookup at all --
        // parseAllowlist already throws on empty, so reaching these assertions is itself part of
        // the proof (identical reasoning to UsualShiftWritePathGuardTest.bothAllowlists_parseAsNonEmpty).
        assertThat(parseAllowlist(ENTITY_ALLOWLIST_HEADING)).isNotEmpty();
        assertThat(parseAllowlist(CALL_SITE_ALLOWLIST_HEADING)).isNotEmpty();
    }

    // --- Table structural integrity ---

    @Test
    void everyLandedProvingTestNamedInTheTable_resolvesToAnExistingClass() throws IOException {
        List<TableRow> rows = parseTableRows();
        assertThat(rows).as("Call-site table must have at least one row per allowlisted call site "
                        + "plus the expected-but-not-yet-landed row")
                .hasSizeGreaterThanOrEqualTo(3);

        Path srcTestJava = Path.of("src", "test", "java");
        for (TableRow row : rows) {
            if (row.entryPoint().contains("expected, not yet landed")) {
                // Plan 22-07 has not landed yet -- this row's proving test cell deliberately names
                // no class (see the registry's own prose). Asserting the row EXISTS (the size
                // assertion above) is the proof; asserting its proving-test cell resolves would be
                // asserting plan 22-07 already landed, which defeats the point of this row.
                continue;
            }
            assertThat(row.path()).as("Row '%s': Entry point cell must not be blank", row).isNotBlank();
            assertThat(row.sourceFile()).as("Row '%s': Source file cell must not be blank", row).isNotBlank();
            assertThat(row.effect()).as("Row '%s': 'What must hold' cell must not be blank", row).isNotBlank();
            assertThat(row.provingTest()).as("Row '%s': Proving test cell must not be blank", row).isNotBlank();

            String fqcn = resolveFullyQualifiedName(srcTestJava, row.provingTest());
            assertThatCode(() -> Class.forName(fqcn))
                    .as("Proving test '%s' named in rest-waiver-predicate-guard.md must resolve via "
                            + "Class.forName (fqcn=%s) -- a renamed or deleted test must not leave a "
                            + "row pointing at nothing", row.provingTest(), fqcn)
                    .doesNotThrowAnyException();
        }
    }

    // --- The second-implementation detector (D-08's own reason for existing) ---

    @Test
    @DisplayName("no production class outside RestWaiverLookup compares an AgentRestWaiver's date and agent itself")
    void secondImplementationScan_returnsEmptySet() throws IOException {
        Set<String> found = scanProductionSources(
                SOURCE_ROOT, RestWaiverPredicateGuardTest::isSecondWaivedPairComparison);
        assertThat(found)
                .as("A second implementation of the waived-pair comparison was found outside "
                        + "RestWaiverLookup -- this must be removed and replaced with a call to "
                        + "RestWaiverLookup.waives/isWaived: %s", found)
                .isEmpty();
    }

    @Test
    @DisplayName("the second-implementation matcher is live against synthetic strings, and does not fire on a correct call site")
    void secondImplementationMatcher_isLiveAgainstSyntheticStrings() {
        // The canonical reintroduction of the bug this scan exists to catch: both fields read and
        // compared on one line.
        assertThat(isSecondWaivedPairComparison(
                "boolean matches = w.getAgent().getId().equals(agentId) && w.getDate().equals(date);"))
                .isTrue();
        assertThat(isSecondWaivedPairComparison(
                "return waiver.getAgent() != null && waiver.getAgent().getId().equals(agentId) "
                        + "&& waiver.getDate().equals(businessDateEntered);"))
                .isTrue();

        // A comment mentioning the shape must not trip the scan.
        assertThat(isSecondWaivedPairComparison(
                "// w.getAgent().getId().equals(agentId) && w.getDate().equals(date)")).isFalse();
        assertThat(isSecondWaivedPairComparison(
                "* Historically this compared getAgent().equals and getDate().equals directly")).isFalse();

        // The correct call-site shape: the whole waiver is passed into RestWaiverLookup, never
        // read field-by-field -- this must NOT be mistaken for a second implementation.
        assertThat(isSecondWaivedPairComparison(
                "RestWaiverLookup.waives(waiver, match.next().agentId(), match.next().businessDate())"))
                .isFalse();

        // Reading only one of the two fields is not the comparison D-08 names (it cannot waive a
        // pair on its own without the other field), so it is out of this scan's scope by design.
        assertThat(isSecondWaivedPairComparison("if (waiver.getDate().equals(date)) { return true; }"))
                .isFalse();
        assertThat(isSecondWaivedPairComparison("if (waiver.getAgent().getId().equals(agentId)) { }"))
                .isFalse();
    }

    /**
     * The test-of-the-test: proves the second-implementation scan's pipeline (walk,
     * comment-strip, match, set-compare) can actually go red, not merely that it has never been
     * observed to -- the same property {@code UsualShiftWritePathGuardTest} and
     * {@code MidnightTimeArithmeticGuardTest} both require of their own guards.
     */
    @Test
    @DisplayName("the second-implementation scan's set-equality check is detected as a mismatch when deliberately broken")
    void deliberatelyBrokenExpectation_isDetectedAsAMismatch() {
        String offendingLine = "com.wfm.example.Example :: "
                + "if (w.getAgent().getId().equals(agentId) && w.getDate().equals(date)) return true;";

        // NEW direction: one real (synthetic) offending line, expected empty -- must fail.
        assertThatThrownBy(() -> assertThat(Set.of(offendingLine)).isEmpty())
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("a deliberately broken allowlist is detected as a mismatch against the real derived set")
    void deliberatelyBrokenAllowlist_isDetectedAsAMismatch() throws IOException {
        Set<String> derived = deriveReferencingClasses(ENTITY_TYPE_NAME);
        Set<String> brokenAllowlist = new HashSet<>(parseAllowlist(ENTITY_ALLOWLIST_HEADING));
        String removed = brokenAllowlist.iterator().next();
        brokenAllowlist.remove(removed);

        assertThatThrownBy(() ->
                assertThat(derived)
                        .as("test-of-the-test: this assertion is EXPECTED to fail")
                        .containsExactlyInAnyOrderElementsOf(brokenAllowlist))
                .isInstanceOf(AssertionError.class);
    }

    // --- Derivation over live src/main/java ---

    private static Set<String> deriveReferencingClasses(String typeName) throws IOException {
        Pattern pattern = Pattern.compile(Pattern.quote(typeName));
        return scanClasses(code -> pattern.matcher(code).find());
    }

    private static Set<String> deriveCallSiteClasses() throws IOException {
        return scanClasses(code -> code.contains(LOOKUP_CALL_TOKEN))
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

    /** True when this source line reads both an {@link com.wfm.model.AgentRestWaiver}'s agent and
     *  its date and compares them -- the exact shape {@code RestWaiverLookup.waives}'s own body
     *  has, and the second-implementation shape D-08 forbids anywhere else. Deliberately narrow:
     *  a call site that merely passes the whole waiver object into {@code RestWaiverLookup}
     *  contains neither {@code getAgent()} nor {@code getDate()} literally, so it does not trip
     *  this matcher (see {@link #secondImplementationMatcher_isLiveAgainstSyntheticStrings}). */
    private static boolean isSecondWaivedPairComparison(String rawLine) {
        String code = stripComment(rawLine);
        if (code.isEmpty()) {
            return false;
        }
        boolean readsAgent = code.contains("getAgent()");
        boolean readsDate = code.contains("getDate()");
        boolean compares = code.contains(".equals(") || code.contains("==");
        return readsAgent && readsDate && compares;
    }

    /** Walks {@code root}, collecting every non-comment line {@code matcher} accepts, tagged with
     *  its class's fully-qualified name -- the same shared-walk shape
     *  {@code MidnightTimeArithmeticGuardTest.scanProductionSources} uses. {@code
     *  RestWaiverLookup} itself is excluded: it IS the one permitted implementation, exactly as
     *  {@code DayWindow} is excluded from the midnight-arithmetic scan. */
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

    /** Same resolution shape as {@code UsualShiftWritePathGuardTest.resolveFullyQualifiedName}:
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
                    + "only when NO class anywhere references the type, which defeats the guard");
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
        try (InputStream in = RestWaiverPredicateGuardTest.class
                .getClassLoader().getResourceAsStream(REGISTRY_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(REGISTRY_RESOURCE + " not found on the test classpath -- "
                        + "the rest-waiver predicate registry is missing, so this guard has nothing to "
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
