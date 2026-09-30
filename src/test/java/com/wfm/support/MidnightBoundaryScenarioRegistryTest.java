package com.wfm.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BDAY-06's two-directional flip registry validator. Three scenarios, spread across {@code
 * com.wfm.solver.MidnightBoundaryRegressionTest} and {@code com.wfm.service.MidnightBoundaryPropertyTest},
 * assert TODAY's actual behaviour at a property this codebase cannot yet represent correctly.
 * Each is marked with {@link AssertsTodaysBehaviour} on its test method, and each mark is named in
 * {@code src/test/resources/midnight-boundary-scenarios.md}'s registry section. This test proves
 * the marked set and the parsed registry are exactly equal, in both directions, so a later phase
 * can neither flip one of these assertions without removing its registry entry, nor leave a
 * registry entry behind after the method it described is deleted or rewritten.
 *
 * <p>Resolves both scenario classes by fully qualified name rather than importing them directly,
 * so this validator sits in neither of them and privileges neither. A rename of either class must
 * fail this test loudly -- by an assertion on the resolve itself -- rather than silently reflecting
 * over zero methods and reporting every registry entry as missing.
 */
class MidnightBoundaryScenarioRegistryTest {

    private static final String REGRESSION_TEST_CLASS = "com.wfm.solver.MidnightBoundaryRegressionTest";
    private static final String PROPERTY_TEST_CLASS = "com.wfm.service.MidnightBoundaryPropertyTest";

    private static final String RESOURCE = "midnight-boundary-scenarios.md";
    private static final String REGISTRY_HEADING = "### Scenarios asserting today's behaviour";

    /** The exact number of scenarios whose property cannot exist today, per BDAY-06's planning. */
    private static final int EXPECTED_REGISTRY_SIZE = 3;

    /**
     * The milestone's own requirement IDs a {@link AssertsTodaysBehaviour#flippedBy()} value may
     * legitimately name. Hardcoded here rather than read from {@code .planning/REQUIREMENTS.md} at
     * runtime: that file is planning-time documentation, not a build artefact, and it is archived
     * out from under a shipped milestone (see {@code STATE.md}'s milestone-close pattern) -- a
     * runtime dependency on its current path or contents would make this guard fragile in exactly
     * the way {@code src/test/resources}-scoped parsed allowlists are not.
     */
    private static final Set<String> KNOWN_REQUIREMENT_IDS = Set.of(
            "OVNT-01", "OVNT-02", "OVNT-03", "OVNT-04", "OVNT-05", "OVNT-06", "OVNT-07",
            "BDAY-01", "BDAY-02", "BDAY-03", "BDAY-04", "BDAY-05", "BDAY-06", "BDAY-07", "BDAY-08",
            "SOLV-01", "SOLV-02", "SOLV-03", "SOLV-04", "SOLV-05", "SOLV-06", "SOLV-07",
            "REST-01", "REST-02", "REST-03", "REST-04", "REST-05", "REST-06", "REST-07");

    private static final Pattern REQUIREMENT_ID_PATTERN = Pattern.compile("^[A-Z]{3,6}-[0-9]{2}$");

    @Test
    void bothScenarioClassesResolveByName() {
        assertThatCode(() -> Class.forName(REGRESSION_TEST_CLASS))
                .as("a rename of the constraint-level scenario class must fail loudly here, not "
                        + "silently empty the reflected method set")
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(PROPERTY_TEST_CLASS))
                .as("a rename of the plain-unit scenario class must fail loudly here, not "
                        + "silently empty the reflected method set")
                .doesNotThrowAnyException();
    }

    @Test
    void markedMethodSet_equalsTheParsedRegistry_inBothDirections() throws Exception {
        Set<String> marked = collectMarkedMethods();
        Set<String> registered = parseRegistry();

        Set<String> notRegistered = new LinkedHashSet<>(marked);
        notRegistered.removeAll(registered);
        Set<String> stale = new LinkedHashSet<>(registered);
        stale.removeAll(marked);

        assertThat(marked)
                .as("""
                        @AssertsTodaysBehaviour-marked methods in %s and %s must equal the registry \
                        in %s exactly.

                        NOT REGISTERED -- a marked method with no registry entry: %s
                        STALE -- a registry entry naming no marked method: %s""",
                        REGRESSION_TEST_CLASS, PROPERTY_TEST_CLASS, RESOURCE, notRegistered, stale)
                .containsExactlyInAnyOrderElementsOf(registered);
    }

    @Test
    void registryHoldsExactlyThreeEntries() throws Exception {
        assertThat(parseRegistry())
                .as("the registry must hold exactly the number of scenarios whose property cannot "
                        + "exist today -- a fourth marked scenario must not be addable without a "
                        + "decision to widen this count")
                .hasSize(EXPECTED_REGISTRY_SIZE);
    }

    @Test
    void everyFlippedByValue_isAKnownRequirementId() throws Exception {
        for (Method method : collectMarkedMethodObjects()) {
            AssertsTodaysBehaviour annotation = method.getAnnotation(AssertsTodaysBehaviour.class);
            String flippedBy = annotation.flippedBy();
            assertThat(REQUIREMENT_ID_PATTERN.matcher(flippedBy).matches())
                    .as("'%s' on %s#%s does not look like a requirement ID", flippedBy,
                            method.getDeclaringClass().getCanonicalName(), method.getName())
                    .isTrue();
            assertThat(KNOWN_REQUIREMENT_IDS)
                    .as("'%s' on %s#%s is not one of the milestone's actual requirement IDs -- a "
                            + "typo here would register a flip nobody will ever perform", flippedBy,
                            method.getDeclaringClass().getCanonicalName(), method.getName())
                    .contains(flippedBy);
        }
    }

    @Test
    void missingRegistryHeading_failsLoudly() throws IOException {
        // The parser must never silently return an empty set for a malformed resource -- that is
        // the one way this guard could pass while enforcing nothing.
        assertThatThrownBy(() -> parseFencedBlock(readResource(), "### No Such Heading"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Such Heading");
    }

    // --- reflection ---

    private static Set<String> collectMarkedMethods() throws ClassNotFoundException {
        Set<String> rendered = new LinkedHashSet<>();
        for (Method method : collectMarkedMethodObjects()) {
            rendered.add(render(method));
        }
        return rendered;
    }

    private static List<Method> collectMarkedMethodObjects() throws ClassNotFoundException {
        List<Method> methods = new ArrayList<>();
        collectFromClassAndNested(Class.forName(REGRESSION_TEST_CLASS), methods);
        collectFromClassAndNested(Class.forName(PROPERTY_TEST_CLASS), methods);
        return methods;
    }

    /**
     * Walks {@code root}'s own declared methods, then recurses into every {@code @Nested} class it
     * declares -- both scenario classes group their test methods into {@code @Nested} classes
     * named for the property under test, so a shallow {@code getDeclaredMethods()} over the
     * top-level class alone would miss every one of them.
     */
    private static void collectFromClassAndNested(Class<?> root, List<Method> out) {
        for (Method method : root.getDeclaredMethods()) {
            if (method.isAnnotationPresent(AssertsTodaysBehaviour.class)) {
                out.add(method);
            }
        }
        for (Class<?> nested : root.getDeclaredClasses()) {
            collectFromClassAndNested(nested, out);
        }
    }

    private static String render(Method method) {
        AssertsTodaysBehaviour annotation = method.getAnnotation(AssertsTodaysBehaviour.class);
        return method.getDeclaringClass().getCanonicalName() + "#" + method.getName()
                + " -> " + annotation.flippedBy();
    }

    // --- resource parsing (same idiom as the other structural guards in this codebase) ---

    private static Set<String> parseRegistry() throws IOException {
        Set<String> entries = parseFencedBlock(readResource(), REGISTRY_HEADING);
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                    RESOURCE + " parsed to an EMPTY registry under '" + REGISTRY_HEADING
                            + "'. An empty expected set would make the set-equality assertion "
                            + "vacuously satisfiable.");
        }
        return entries;
    }

    private static String readResource() throws IOException {
        try (InputStream in = MidnightBoundaryScenarioRegistryTest.class
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
            throw new IllegalStateException("No fenced block found after heading '" + heading + "' in " + RESOURCE);
        }
        return new LinkedHashSet<>(collected);
    }
}
