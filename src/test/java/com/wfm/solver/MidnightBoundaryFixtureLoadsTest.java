package com.wfm.solver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Touches {@link MidnightBoundaryFixture} so its class-load {@code static { }} validator
 * actually runs, and asserts the two collections it produces are non-empty. Without this test, a
 * validator failure would first surface as an {@code ExceptionInInitializerError} inside some
 * other class that happens to reference the fixture first, and be mistaken for a problem with
 * THAT class (BDAY-06). With this test sitting in the fixture's own package, a validator failure
 * is attributable to the fixture itself.
 */
class MidnightBoundaryFixtureLoadsTest {

    @Test
    void fixtureLoadsAndBothCollectionsAreNonEmpty() {
        assertThat(MidnightBoundaryFixture.ALL_SCENARIOS)
                .as("the constructed scenario list must not be empty")
                .isNotEmpty();
        assertThat(MidnightBoundaryFixture.STRUCTURAL_PREDICATES)
                .as("the structural predicate map must not be empty")
                .isNotEmpty();
    }
}
