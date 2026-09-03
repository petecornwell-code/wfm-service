package com.wfm.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-14's structural completeness guard (USHF-05, XCUT-02) — the deliverable this phase exists for.
 *
 * <p><strong>What this proves:</strong> the set of production classes in {@code src/main/java} that
 * reference {@link com.wfm.repository.AgentUsualShiftRepository} or the {@link
 * com.wfm.model.AgentUsualShift} entity type is EXACTLY the set named in the two fenced allowlists
 * of {@code src/test/resources/ushf-05-write-paths.md} — no more, no less. A class gaining either
 * reference without an accompanying table row fails the build; a table row for a class that no
 * longer holds the reference also fails the build (stale-row detection).
 *
 * <p><strong>Set equality only, never subset or containment.</strong> Every assertion below uses
 * {@code containsExactlyInAnyOrderElementsOf}. Widening any of these to {@code isSubsetOf},
 * {@code containsAnyOf}, or a bare {@code .contains(...)} converts this guard into decoration — see
 * {@link com.wfm.solver.ScheduleConstraintClassificationTest}'s own javadoc warning against exactly
 * this failure mode. This is the single most important property of this class: it is the reason
 * v1.2 audit finding I-2 (a guarantee that held on the upload path only, undetected across two
 * consecutive milestone audits) is not repeatable here by accident.
 *
 * <p><strong>A false positive fails safe.</strong> This guard is a purely TEXTUAL scan — the type
 * name {@code AgentUsualShiftRepository} or {@code AgentUsualShift} appearing inside a comment or an
 * unrelated string literal would register as a match. That is an acceptable failure mode: it forces
 * a human to look at a diff, rather than silently missing a real new writer. The one thing this
 * guard CANNOT see is a repository injected behind an abstraction that hides its concrete type —
 * not a risk in this codebase today, since every repository observed in this project is injected by
 * its concrete Spring Data interface type directly (constructor injection into {@code private final}
 * fields, confirmed across every service class read at plan time).
 *
 * <p>No Spring context, no Testcontainers, no database — mirrors {@code
 * com.wfm.migration.MigrationEntityConsistencyTest}'s posture and its classpath-resource-reading
 * technique, extended from reading migration files off the classpath to reading both the table
 * resource AND walking {@code src/main/java} directly on disk.
 */
class UsualShiftWritePathGuardTest {

    private static final String TABLE_RESOURCE = "ushf-05-write-paths.md";

    private static final String REPOSITORY_ALLOWLIST_HEADING =
            "### AgentUsualShiftRepository references (Set A)";
    private static final String ENTITY_ALLOWLIST_HEADING =
            "### AgentUsualShift entity references (Set B)";

    private static final String REPOSITORY_TYPE_NAME = "AgentUsualShiftRepository";
    private static final String ENTITY_TYPE_NAME = "AgentUsualShift";

    /** Word-boundary derivation for Set B: matches "AgentUsualShift" only when NOT immediately
     * followed by "Repository" — a naive {@code contains} would conflate every Set A occurrence
     * (which also contains "AgentUsualShift" as a literal prefix) into Set B as well. */
    private static final Pattern ENTITY_TYPE_NOT_REPOSITORY =
            Pattern.compile(Pattern.quote(ENTITY_TYPE_NAME) + "(?!Repository)");

    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|\\s*$");
    private static final Pattern SEPARATOR_ROW = Pattern.compile("^-+$");

    // --- Set derivations over live src/main/java (the two independent scans) ---

    @Test
    void repositoryReferenceSet_matchesTheTableAllowlistExactly() throws IOException, URISyntaxException {
        Set<String> derived = deriveReferencingClasses(REPOSITORY_TYPE_NAME, false);
        Set<String> allowlist = parseAllowlist(REPOSITORY_ALLOWLIST_HEADING);

        Set<String> missingFromTable = new HashSet<>(derived);
        missingFromTable.removeAll(allowlist);
        Set<String> staleInTable = new HashSet<>(allowlist);
        staleInTable.removeAll(derived);

        assertThat(derived)
                .as("Classes referencing AgentUsualShiftRepository in src/main/java must equal the "
                        + "table's Set A allowlist exactly. Missing a row for (add a row to "
                        + "ushf-05-write-paths.md describing what the new writer guarantees, do NOT "
                        + "just add the class name and move on): %s. Stale rows naming a class that no "
                        + "longer references the type (remove the row): %s.", missingFromTable, staleInTable)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    @Test
    void entityReferenceSet_matchesTheTableAllowlistExactly() throws IOException, URISyntaxException {
        Set<String> derived = deriveReferencingClasses(ENTITY_TYPE_NAME, true);
        Set<String> allowlist = parseAllowlist(ENTITY_ALLOWLIST_HEADING);

        Set<String> missingFromTable = new HashSet<>(derived);
        missingFromTable.removeAll(allowlist);
        Set<String> staleInTable = new HashSet<>(allowlist);
        staleInTable.removeAll(derived);

        assertThat(derived)
                .as("Classes referencing the AgentUsualShift entity type in src/main/java must equal "
                        + "the table's Set B allowlist exactly. Missing a row for (add a row to "
                        + "ushf-05-write-paths.md describing what the new writer guarantees, do NOT "
                        + "just add the class name and move on): %s. Stale rows naming a class that no "
                        + "longer references the type (remove the row): %s.", missingFromTable, staleInTable)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    @Test
    void bothAllowlists_parseAsNonEmpty() throws IOException {
        Set<String> repositoryAllowlist = parseAllowlist(REPOSITORY_ALLOWLIST_HEADING);
        Set<String> entityAllowlist = parseAllowlist(ENTITY_ALLOWLIST_HEADING);

        // An empty expected set would make the set-equality assertions above trivially satisfiable
        // (vacuously true) if src/main/java ever stopped referencing either type -- parseAllowlist
        // already throws on empty, so reaching these assertions is itself part of the proof.
        assertThat(repositoryAllowlist).isNotEmpty();
        assertThat(entityAllowlist).isNotEmpty();
    }

    // --- Table structural integrity ---

    @Test
    void everyProvingTestNamedInTheTable_resolvesToAnExistingClass() throws IOException, URISyntaxException {
        List<TableRow> rows = parseTableRows();
        Set<String> simpleNames = new HashSet<>();
        for (TableRow row : rows) {
            for (String cell : row.provingTest().split(",")) {
                String trimmed = cell.trim();
                if (!trimmed.isEmpty()) {
                    simpleNames.add(trimmed);
                }
            }
        }
        assertThat(simpleNames).as("Table must name at least one proving test").isNotEmpty();

        Path srcTestJava = resolveModuleRoot().resolve("src/test/java");
        for (String simpleName : simpleNames) {
            String fqcn = resolveFullyQualifiedName(srcTestJava, simpleName);
            assertThatCode(() -> Class.forName(fqcn))
                    .as("Proving test '%s' named in ushf-05-write-paths.md must resolve via "
                            + "Class.forName (fqcn=%s) -- a renamed or deleted test must not leave a "
                            + "row pointing at nothing", simpleName, fqcn)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void theTableHasExactlyNineDataRows_withNoBlankRequiredCells() throws IOException, URISyntaxException {
        List<TableRow> rows = parseTableRows();
        assertThat(rows).as("USHF-05 table must have exactly nine data rows (D-14's seven + P-18's two)")
                .hasSize(9);

        for (TableRow row : rows) {
            assertThat(row.path()).as("Row '%s': Path cell must not be blank", row).isNotBlank();
            assertThat(row.entryPoint()).as("Row '%s': Entry point cell must not be blank", row).isNotBlank();
            assertThat(row.effect()).as("Row '%s': Effect cell must not be blank", row).isNotBlank();
            assertThat(row.provingTest()).as("Row '%s': Proving test cell must not be blank", row).isNotBlank();
        }
    }

    /**
     * The test-of-the-test (plan requirement, Task 1 acceptance criteria): proves this guard is
     * actually CAPABLE of going red, not merely that it has never been observed to. Removes one
     * entry from a copy of the real repository allowlist and asserts the set-equality check against
     * the real derived set fails with an AssertionError -- the same property Phase 15 required of
     * {@code SolverQualityGuardTest} (a guard that has never failed cannot claim it is able to).
     */
    @Test
    void deliberatelyBrokenAllowlist_isDetectedAsAMismatch() throws IOException, URISyntaxException {
        Set<String> derived = deriveReferencingClasses(REPOSITORY_TYPE_NAME, false);
        Set<String> allowlistWithOneEntryRemoved = new HashSet<>(parseAllowlist(REPOSITORY_ALLOWLIST_HEADING));
        String removed = allowlistWithOneEntryRemoved.iterator().next();
        allowlistWithOneEntryRemoved.remove(removed);

        assertThatThrownBy(() ->
                assertThat(derived)
                        .as("test-of-the-test: this assertion is EXPECTED to fail")
                        .containsExactlyInAnyOrderElementsOf(allowlistWithOneEntryRemoved))
                .isInstanceOf(AssertionError.class);
    }

    // --- Derivation over live src/main/java ---

    /**
     * Walks {@code src/main/java}, reads every {@code .java} file's text, and returns the set of
     * fully-qualified class names whose file contains the given literal type name. When {@code
     * excludeRepositorySuffix} is true, a match immediately followed by "Repository" is NOT counted
     * (the Set B / entity-type derivation) -- this is what keeps the two derivations independent
     * rather than one being a strict superset of every file matching the other for an unrelated
     * reason.
     */
    private static Set<String> deriveReferencingClasses(String typeName, boolean excludeRepositorySuffix)
            throws IOException, URISyntaxException {
        Path srcMainJava = resolveModuleRoot().resolve("src/main/java");
        if (!Files.isDirectory(srcMainJava)) {
            throw new IllegalStateException("src/main/java not found at " + srcMainJava
                    + " -- guard cannot scan production code without it");
        }

        Set<String> classes = new HashSet<>();
        Pattern pattern = excludeRepositorySuffix ? ENTITY_TYPE_NOT_REPOSITORY : Pattern.compile(Pattern.quote(typeName));
        try (Stream<Path> files = Files.walk(srcMainJava)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (pattern.matcher(text).find()) {
                    classes.add(toFullyQualifiedName(srcMainJava, file));
                }
            }
        }
        return classes;
    }

    private static String toFullyQualifiedName(Path root, Path javaFile) {
        String relative = root.relativize(javaFile).toString();
        String withoutSuffix = relative.substring(0, relative.length() - ".java".length());
        return withoutSuffix.replace('/', '.').replace('\\', '.');
    }

    /** Same resolution shape, but starting from {@code src/test/java}, returning the FQCN of the
     * single {@code .java} file whose simple name (filename minus extension) matches. */
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

    /**
     * Resolves the Gradle module root the same way {@code MigrationEntityConsistencyTest} resolves
     * its migration directory, adapted for a source directory that is NOT copied onto the runtime
     * classpath (unlike {@code src/main/resources/db/migration}, {@code src/main/java} has no
     * classpath resource to look up). Instead: locate this test class's own compiled-class
     * directory (typically {@code <module>/build/classes/java/test}) via its code source URL, then
     * walk up to the module root. Fails loudly with the attempted path if the directory it computes
     * does not exist, rather than silently scanning nothing and passing.
     */
    private static Path resolveModuleRoot() throws URISyntaxException {
        URL codeSourceUrl = UsualShiftWritePathGuardTest.class.getProtectionDomain().getCodeSource().getLocation();
        Path testClassesDir = Path.of(codeSourceUrl.toURI());
        // testClassesDir is typically <module>/build/classes/java/test -- four parents reaches the
        // module root: test -> java -> classes -> build -> <module root>.
        Path moduleRoot = testClassesDir.getParent().getParent().getParent().getParent();
        if (moduleRoot == null || !Files.isDirectory(moduleRoot)) {
            throw new IllegalStateException("Could not resolve module root from test classpath location "
                    + testClassesDir + " (computed module root: " + moduleRoot + ")");
        }
        return moduleRoot;
    }

    // --- Table + allowlist parsing ---

    private static Set<String> parseAllowlist(String heading) throws IOException {
        String text = readTableResource();
        int headingIdx = text.indexOf(heading);
        if (headingIdx < 0) {
            throw new IllegalStateException("Heading '" + heading + "' not found in " + TABLE_RESOURCE);
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

        Set<String> entries = new HashSet<>();
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
        String text = readTableResource();
        List<TableRow> rows = new ArrayList<>();
        boolean headerSeen = false;
        for (String line : text.split("\n")) {
            Matcher matcher = TABLE_ROW.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String[] cells = matcher.group(1).split("\\|", -1);
            if (cells.length < 5) {
                continue;
            }
            String firstCell = cells[0].trim();
            if (firstCell.equals("Path")) {
                headerSeen = true;
                continue;
            }
            if (!headerSeen) {
                continue;
            }
            if (SEPARATOR_ROW.matcher(firstCell.replace(" ", "")).matches()) {
                continue;
            }
            rows.add(new TableRow(
                    cells[0].trim(), cells[1].trim(), cells[2].trim(), cells[3].trim(), cells[4].trim()));
        }
        return rows;
    }

    private static String readTableResource() throws IOException {
        var classLoader = UsualShiftWritePathGuardTest.class.getClassLoader();
        URL resource = classLoader.getResource(TABLE_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException(TABLE_RESOURCE + " not found on the test classpath -- the "
                    + "USHF-05 table is missing, so this guard has nothing to enforce against");
        }
        try (var stream = resource.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable> assertThatCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.assertThatCode(callable);
    }

    private record TableRow(String path, String entryPoint, String sourceFile, String effect, String provingTest) {}
}
