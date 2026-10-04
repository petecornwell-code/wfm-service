package com.wfm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REST-07/WR-02's structural completeness guard -- the deliverable {@code 22-12-PLAN.md}'s Task 1
 * exists for, in the same register as {@link RestWaiverPredicateGuardTest} (whose mechanism this
 * copies near-verbatim) and {@link MidnightTimeArithmeticGuardTest} (whose comment-stripping scan
 * technique this copies).
 *
 * <p>Proves the set of production classes in {@code src/main/java} that construct a {@code
 * ScheduleSummary} is EXACTLY the registry's one-entry allowlist. This replaces a parity test
 * (which only catches a divergence someone remembered to write a fixture for) with a structural
 * guard, the same trade D-08 already made once in this phase for the waived-pair predicate.
 *
 * <p><strong>Set equality only, never subset or containment.</strong> The assertion uses {@code
 * containsExactlyInAnyOrderElementsOf}. Widening it to {@code isSubsetOf}, {@code containsAnyOf},
 * or a bare {@code .contains(...)} converts this guard into decoration -- see {@link
 * RestWaiverPredicateGuardTest}'s and {@link MidnightTimeArithmeticGuardTest}'s own javadoc
 * warnings against exactly this failure mode.
 *
 * <p><strong>A false positive fails safe.</strong> This is a purely textual scan. A javadoc or
 * comment mentioning {@code new ScheduleSummary(} is stripped before matching; a string literal
 * containing that token would still register as a match. That is an acceptable trade: it forces a
 * human to look at a diff rather than silently missing a real second construction site.
 *
 * <p>No Spring context, no Testcontainers, no database -- reads the registry off the test
 * classpath and walks {@code src/main/java} on disk, exactly as both precedents above do.
 */
class ScheduleSummaryConstructionSiteGuardTest {

    private static final String REGISTRY_RESOURCE = "schedule-summary-construction-site.md";
    private static final String ALLOWLIST_HEADING = "## Guard Allowlist";

    private static final String CONSTRUCTION_TOKEN = "new ScheduleSummary(";

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    @Test
    @DisplayName("every production class constructing a ScheduleSummary matches the registry's allowlist exactly")
    void constructionSiteSet_matchesTheRegistryAllowlistExactly() throws IOException {
        Set<String> derived = scanClasses(ScheduleSummaryConstructionSiteGuardTest::isConstructionExpression);
        Set<String> allowlist = parseAllowlist();

        Set<String> missing = new HashSet<>(derived);
        missing.removeAll(allowlist);
        Set<String> stale = new HashSet<>(allowlist);
        stale.removeAll(derived);

        assertThat(derived)
                .as("Classes constructing ScheduleSummary in src/main/java must equal the registry's "
                        + "allowlist exactly. Missing a row for (add it to "
                        + "schedule-summary-construction-site.md and explain what the new "
                        + "construction site guarantees, do NOT just add the class name): %s. Stale "
                        + "entries naming a class that no longer constructs it (remove them): %s.",
                        missing, stale)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    @Test
    void allowlist_parsesAsNonEmpty() throws IOException {
        // An empty expected set would make the set-equality assertion above vacuously satisfiable
        // only if production code ever stopped constructing ScheduleSummary at all --
        // parseAllowlist already throws on empty, so reaching this assertion is itself part of the
        // proof (identical reasoning to RestWaiverPredicateGuardTest.bothAllowlists_parseAsNonEmpty).
        assertThat(parseAllowlist()).isNotEmpty();
    }

    @Test
    @DisplayName("the construction-site matcher is live against a synthetic source string, and does not fire on a comment")
    void constructionMatcher_isLiveAgainstSyntheticStrings() {
        // The canonical shape: a direct construction call on its own line.
        assertThat(isConstructionExpression(
                "        return new ScheduleSummary(s.getId(), s.getDeskId(), deskName, status);"))
                .isTrue();

        // A comment mentioning the shape must not trip the scan.
        assertThat(isConstructionExpression(
                "// return new ScheduleSummary(s.getId(), s.getDeskId(), deskName, status);"))
                .isFalse();
        assertThat(isConstructionExpression(
                "* An example: new ScheduleSummary(...) is the one call site"))
                .isFalse();

        // A method that merely builds TOWARD a ScheduleSummary but does not itself construct one
        // must not trip the scan.
        assertThat(isConstructionExpression(
                "private ScheduleSummary toSummary(Schedule s, String deskName) {"))
                .isFalse();
    }

    /**
     * The test-of-the-test: proves the set-equality check itself rejects both directions -- a real
     * second construction site appearing, and a stale allowlist entry whose site has gone -- not
     * merely that the line-level matcher predicate can fire. The same property {@link
     * RestWaiverPredicateGuardTest} and {@link MidnightTimeArithmeticGuardTest} both require of
     * their own guards.
     */
    @Test
    @DisplayName("the set-equality check is detected as a mismatch when a second construction site is introduced")
    void secondConstructionSite_isDetectedAsAMismatch() {
        Set<String> derivedWithSyntheticSecondSite = new LinkedHashSet<>(Set.of(
                "com.wfm.service.ScheduleService",
                "com.wfm.example.Example"));
        Set<String> allowlist = Set.of("com.wfm.service.ScheduleService");

        assertThatThrownBy(() ->
                assertThat(derivedWithSyntheticSecondSite)
                        .as("test-of-the-test: this assertion is EXPECTED to fail")
                        .containsExactlyInAnyOrderElementsOf(allowlist))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("a stale allowlist entry naming a removed construction site is detected as a mismatch")
    void staleAllowlistEntry_isDetectedAsAMismatch() {
        Set<String> derived = Set.of();
        Set<String> staleAllowlist = Set.of("com.wfm.service.ScheduleService");

        assertThatThrownBy(() ->
                assertThat(derived)
                        .as("test-of-the-test: this assertion is EXPECTED to fail")
                        .containsExactlyInAnyOrderElementsOf(staleAllowlist))
                .isInstanceOf(AssertionError.class);
    }

    // --- Derivation over live src/main/java ---

    private static Set<String> scanClasses(Predicate<String> wholeLineMatcher) throws IOException {
        if (!Files.isDirectory(SOURCE_ROOT)) {
            throw new IllegalStateException("src/main/java not found at " + SOURCE_ROOT
                    + " -- guard cannot scan production code without it");
        }
        Set<String> classes = new HashSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (wholeLineMatcher.test(rawLine)) {
                        classes.add(toFullyQualifiedName(SOURCE_ROOT, file));
                        break;
                    }
                }
            }
        }
        return classes;
    }

    /** True when this source line contains a {@code new ScheduleSummary(} construction expression,
     *  once comments are discarded. */
    private static boolean isConstructionExpression(String rawLine) {
        String code = stripComment(rawLine);
        if (code.isEmpty()) {
            return false;
        }
        return code.contains(CONSTRUCTION_TOKEN);
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

    // --- Registry parsing ---

    private static Set<String> parseAllowlist() throws IOException {
        String text = readRegistryResource();
        int headingIdx = text.indexOf(ALLOWLIST_HEADING);
        if (headingIdx < 0) {
            throw new IllegalStateException(
                    "Heading '" + ALLOWLIST_HEADING + "' not found in " + REGISTRY_RESOURCE);
        }
        int fenceStart = text.indexOf("```", headingIdx);
        if (fenceStart < 0) {
            throw new IllegalStateException(
                    "No fenced code block found after heading '" + ALLOWLIST_HEADING + "'");
        }
        int contentStart = text.indexOf('\n', fenceStart) + 1;
        int fenceEnd = text.indexOf("```", contentStart);
        if (fenceEnd < 0) {
            throw new IllegalStateException(
                    "Unterminated fenced code block after heading '" + ALLOWLIST_HEADING + "'");
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
            throw new IllegalStateException(
                    "Allowlist under heading '" + ALLOWLIST_HEADING + "' is empty -- an empty "
                            + "expected set would make the set-equality assertion trivially "
                            + "satisfiable only when NO class anywhere constructs ScheduleSummary, "
                            + "which defeats the guard");
        }
        return entries;
    }

    private static String readRegistryResource() throws IOException {
        try (InputStream in = ScheduleSummaryConstructionSiteGuardTest.class
                .getClassLoader().getResourceAsStream(REGISTRY_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(REGISTRY_RESOURCE + " not found on the test "
                        + "classpath -- the ScheduleSummary construction-site registry is "
                        + "missing, so this guard has nothing to enforce against");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
