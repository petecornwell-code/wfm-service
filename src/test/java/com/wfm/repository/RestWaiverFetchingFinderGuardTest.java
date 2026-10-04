package com.wfm.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REST-07/CR-01's structural guard, in the same register as
 * {@code ScheduleSummaryConstructionSiteGuardTest} and {@code RestWaiverPredicateGuardTest}
 * (whose scan-and-set-equality mechanism this follows).
 *
 * <p>Proves that NO class under {@code src/main/java} loads rest waivers through
 * {@link AgentRestWaiverRepository#findByTenantIdAndDeskIdAndDateBetween} — the non-fetching
 * finder — so every production path gets the relations-fetching sibling instead.
 *
 * <p><strong>Why a structural guard and not a behavioural test.</strong> The defect CR-01 found is
 * invisible to this project's existing tests by construction: every test that exercises the waiver
 * disclosure mocks {@link AgentRestWaiverRepository}, so no real Hibernate proxy is ever created
 * and {@code LazyInitializationException} can never be raised. Reproducing it behaviourally needs
 * a real session boundary — a Testcontainers test that loads waivers in one transaction, closes
 * it, and reads {@code agent.getName()} outside. That is worth having and is NOT what this test
 * is; this test closes the far cheaper and more durable half: the call site can no longer regress
 * silently, whatever the mocks do.
 *
 * <p><strong>Why the expected set is empty here, when the sibling guards reject an empty
 * allowlist.</strong> Those guards assert set EQUALITY against an allowlist, where an empty
 * expected set would make the assertion vacuous. This guard asserts the opposite shape — a
 * prohibition — so empty IS the invariant. The vacuity risk moves to the other side: a guard that
 * passes because production code stopped loading waivers altogether would be worthless. That is
 * what {@link #fetchingFinder_isActuallyUsedSomewhere()} exists to rule out, and the two
 * assertions are only meaningful together.
 *
 * <p>No Spring context, no Testcontainers, no database — walks {@code src/main/java} on disk,
 * exactly as the precedents above do.
 */
class RestWaiverFetchingFinderGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /**
     * The receiver is part of the token deliberately. {@code AgentExceptionRepository} and
     * {@code AgentDayOffRepository} both declare an identically-named
     * {@code findByTenantIdAndDeskIdAndDateBetween}, and calls to those are entirely legitimate —
     * their associations are not the lazy one this guard is about. Matching the bare method name
     * would flag them and the guard would be deleted as noise within a week.
     */
    private static final String FORBIDDEN_CALL =
            "agentRestWaiverRepository.findByTenantIdAndDeskIdAndDateBetween(";

    /** Unique to {@link AgentRestWaiverRepository}, so this one needs no receiver to disambiguate. */
    private static final String FETCHING_CALL =
            "findWithAgentByTenantIdAndDeskIdAndDateBetween(";

    /**
     * The repository interface itself DECLARES the forbidden method and names it throughout its own
     * javadoc, so it is not a caller and must be excluded or the guard can never pass.
     */
    private static final String REPOSITORY_DECLARATION = "AgentRestWaiverRepository.java";

    @Test
    @DisplayName("no production class loads rest waivers through the non-fetching finder")
    void nonFetchingFinder_hasNoProductionCallers() throws IOException {
        Set<String> callers = scanClasses(FORBIDDEN_CALL);

        assertThat(callers)
                .as("AgentRestWaiver.agent is @ManyToOne(fetch = LAZY) and spring.jpa.open-in-view "
                        + "is false, so waivers loaded through findByTenantIdAndDeskIdAndDateBetween "
                        + "carry proxies that throw LazyInitializationException once their "
                        + "transaction closes — and ScheduleOutputService.buildRestWaiverDisclosure "
                        + "reads each waiver's agent's name on the non-transactional list, summary "
                        + "and detail paths. Use findWithAgentByTenantIdAndDeskIdAndDateBetween "
                        + "instead. Offending classes: %s", callers)
                .isEmpty();
    }

    @Test
    @DisplayName("the fetching finder is actually used, so the prohibition above is not vacuous")
    void fetchingFinder_isActuallyUsedSomewhere() throws IOException {
        // Without this, nonFetchingFinder_hasNoProductionCallers would also pass in a tree where
        // production code loads no waivers at all — a green guard over a removed feature.
        assertThat(scanClasses(FETCHING_CALL))
                .as("No production class calls findWithAgentByTenantIdAndDeskIdAndDateBetween, so "
                        + "the no-non-fetching-callers assertion is vacuously true. Either waiver "
                        + "loading was removed, or it moved to a finder neither token matches.")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the matcher survives a call split across lines and ignores commented-out calls")
    void matcher_isLiveAgainstSyntheticSources() {
        // The shape the defect actually had: receiver and method on one line.
        assertThat(containsCall(
                "        List<AgentRestWaiver> w = agentRestWaiverRepository.findByTenantIdAndDeskIdAndDateBetween(\n"
                        + "                tenantId, deskId, from, to);",
                FORBIDDEN_CALL)).isTrue();

        // The shape the fix has — split after the receiver. A line-by-line scan would miss this,
        // which is why the scan normalises whitespace across the whole file rather than per line.
        assertThat(containsCall(
                "        List<AgentRestWaiver> w = agentRestWaiverRepository\n"
                        + "                .findByTenantIdAndDeskIdAndDateBetween(tenantId, deskId, from, to);",
                FORBIDDEN_CALL)).isTrue();

        // A sibling repository's identically-named finder is NOT a match.
        assertThat(containsCall(
                "        List<AgentException> e = agentExceptionRepository.findByTenantIdAndDeskIdAndDateBetween(\n"
                        + "                tenantId, deskId, from, to);",
                FORBIDDEN_CALL)).isFalse();

        // The fetching finder is not the forbidden one, despite sharing a suffix.
        assertThat(containsCall(
                "        agentRestWaiverRepository.findWithAgentByTenantIdAndDeskIdAndDateBetween(a, b, c, d);",
                FORBIDDEN_CALL)).isFalse();

        // Comments must not trip the scan, in either comment form.
        assertThat(containsCall(
                "        // agentRestWaiverRepository.findByTenantIdAndDeskIdAndDateBetween(a, b, c, d);",
                FORBIDDEN_CALL)).isFalse();
        assertThat(containsCall(
                "        /* agentRestWaiverRepository.findByTenantIdAndDeskIdAndDateBetween(a, b, c, d); */",
                FORBIDDEN_CALL)).isFalse();

        // A MULTI-LINE javadoc naming the forbidden call — the shape
        // AgentRestWaiverRepository's own javadoc has, and the one that would make this guard
        // unable to pass if block comments were not stripped. Note the whole block is supplied,
        // including both delimiters: a bare continuation line (`     * …`) is deliberately NOT
        // treated as a comment, because this scanner reads whole files, where such a line is
        // always inside a block that the stripper has already removed. Asserting on a lone
        // fragment would test a property the scanner does not have and does not need.
        assertThat(containsCall(
                "    /**\n"
                        + "     * superseded as a call site by the fetching sibling:\n"
                        + "     * agentRestWaiverRepository.findByTenantIdAndDeskIdAndDateBetween(a, b, c, d)\n"
                        + "     */\n"
                        + "    void unrelated() {}",
                FORBIDDEN_CALL)).isFalse();
    }

    /**
     * The test-of-the-test: proves the emptiness assertion itself rejects a real caller, rather
     * than only that the matcher predicate can fire. The same property the sibling structural
     * guards each require of themselves.
     */
    @Test
    @DisplayName("a reintroduced caller is detected as a failure")
    void reintroducedCaller_isDetectedAsAFailure() {
        Set<String> derivedWithSyntheticCaller = new LinkedHashSet<>(Set.of("com.wfm.service.SolverService"));

        assertThatThrownBy(() ->
                assertThat(derivedWithSyntheticCaller)
                        .as("test-of-the-test: this assertion is EXPECTED to fail")
                        .isEmpty())
                .isInstanceOf(AssertionError.class);
    }

    // --- Derivation over live src/main/java ---

    private static Set<String> scanClasses(String callToken) throws IOException {
        if (!Files.isDirectory(SOURCE_ROOT)) {
            throw new IllegalStateException("src/main/java not found at " + SOURCE_ROOT
                    + " -- guard cannot scan production code without it");
        }
        Set<String> classes = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals(REPOSITORY_DECLARATION)) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (containsCall(source, callToken)) {
                    classes.add(toFullyQualifiedName(SOURCE_ROOT, file));
                }
            }
        }
        return classes;
    }

    /**
     * True when {@code source} contains {@code callToken} as live code. Comments are discarded
     * first, then ALL whitespace is removed, so a call split across lines at any point — after the
     * receiver, after the dot, inside the argument list — matches the same single token. The token
     * constants above are written without whitespace for exactly this reason.
     *
     * <p>Known boundary, disclosed rather than papered over: this is a textual scan, so a call
     * reached via reflection, or through a local alias of the repository field under a different
     * name, would pass undetected — the same boundary every structural guard in this project
     * declares. A string literal containing the token would produce a false POSITIVE, which fails
     * safe: it forces a human to read a diff.
     */
    private static boolean containsCall(String source, String callToken) {
        return stripComments(source).replaceAll("\\s+", "").contains(callToken);
    }

    /** Removes block comments and line comments. Deliberately simple — it does not try to honour
     *  comment-like sequences inside string literals, which only ever costs a false positive. */
    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }

    private static String toFullyQualifiedName(Path root, Path javaFile) {
        String relative = root.relativize(javaFile).toString();
        String withoutSuffix = relative.substring(0, relative.length() - ".java".length());
        return withoutSuffix.replace(java.io.File.separatorChar, '.');
    }
}
