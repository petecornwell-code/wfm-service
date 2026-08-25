package com.wfm.solver;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeight;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import com.wfm.model.ConstraintWeights;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XCUT-05's enforcement mechanism (Phase 14 P-06/P-07). This is what keeps
 * {@link ScheduleConstraintClassification} honest: every assertion here derives its expected set
 * by reflection over live production code, never from a hardcoded count or a copy-pasted list.
 *
 * <p><strong>Widening any assertion below to a subset/containment check, or replacing a
 * derived count with a literal number, defeats XCUT-05.</strong> The whole reason this test
 * exists is that a classification blind to an unclassified constraint is how an interaction bug
 * ships unnoticed — see 14-CONTEXT.md D-15 and the Option-C silent-{@code 0hard/0soft} failure
 * this project already hit once (SPIKE-COUPLING.md). A suppressed failure here converts the
 * safeguard into decoration.
 *
 * <p>This test lives in {@code src/test/java/com/wfm/solver/} and reads
 * {@link ScheduleConstraintProvider} and {@link ConstraintWeights} reflectively; it never
 * modifies either. No Spring context is loaded — following the plain-instantiation idiom this
 * codebase already uses where a container adds nothing (see
 * {@code GlobalExceptionHandlerTest}).
 */
class ScheduleConstraintClassificationTest {

    /**
     * Derivation 1 (P-07): every {@code @ConstraintWeight} annotation value declared on
     * {@link ConstraintWeights}. These are exactly the strings each constraint's
     * {@code .asConstraint(...)} call registers.
     */
    private static Set<String> constraintWeightNames() {
        Set<String> names = new HashSet<>();
        for (Field field : ConstraintWeights.class.getDeclaredFields()) {
            ConstraintWeight annotation = field.getAnnotation(ConstraintWeight.class);
            if (annotation != null) {
                names.add(annotation.value());
            }
        }
        return names;
    }

    /**
     * Derivation 2 (P-07): every method on {@link ScheduleConstraintProvider} that returns a
     * {@link Constraint} and takes exactly one {@link ConstraintFactory} parameter. This
     * excludes {@code defineConstraints} itself (returns {@code Constraint[]}), and counts
     * builder methods independently of whether they carry a weight — a constraint added with
     * no {@code @ConstraintWeight} still fails derivation 1 above, and a constraint with a
     * weight but no builder method still fails this one.
     */
    private static long constraintBuilderMethodCount() {
        long count = 0;
        for (Method method : ScheduleConstraintProvider.class.getDeclaredMethods()) {
            if (method.getReturnType().equals(Constraint.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].equals(ConstraintFactory.class)) {
                count++;
            }
        }
        return count;
    }

    @Test
    void bothReflectionDerivationsAgreeWithEachOther() {
        // Not the completeness assertion itself -- a sanity check that the two independent
        // derivations this test relies on do not silently disagree with each other. If they
        // do, that disagreement is the finding: report it, don't reconcile by hand.
        assertThat(constraintBuilderMethodCount())
                .as("Constraint-returning builder method count on ScheduleConstraintProvider "
                        + "must equal the number of @ConstraintWeight fields on ConstraintWeights")
                .isEqualTo(constraintWeightNames().size());
    }

    @Test
    void classificationKeySetExactlyEqualsTheRegisteredConstraintSet() {
        Set<String> classified = ScheduleConstraintClassification.classifications().keySet();
        Set<String> registered = constraintWeightNames();

        Set<String> unclassified = new HashSet<>(registered);
        unclassified.removeAll(classified);

        Set<String> stale = new HashSet<>(classified);
        stale.removeAll(registered);

        assertThat(unclassified)
                .as("Constraints registered via @ConstraintWeight but missing from "
                        + "ScheduleConstraintClassification.classifications() -- these are unclassified "
                        + "and MUST be given a row")
                .isEmpty();
        assertThat(stale)
                .as("Rows in ScheduleConstraintClassification.classifications() that no longer "
                        + "correspond to a registered @ConstraintWeight -- these are stale and MUST be "
                        + "removed")
                .isEmpty();
    }

    @Test
    void classificationSizeExactlyEqualsTheBuilderMethodCount() {
        // Independent, second derivation (P-07): catches a constraint added with a builder
        // method but no weight, which the key-set assertion above cannot see.
        assertThat((long) ScheduleConstraintClassification.classifications().size())
                .as("ScheduleConstraintClassification row count must equal the number of "
                        + "Constraint-returning builder methods on ScheduleConstraintProvider")
                .isEqualTo(constraintBuilderMethodCount());
    }

    @Test
    void everyClassificationValueIsNonNullAndCarriesANonBlankBasis() {
        for (Map.Entry<String, ScheduleConstraintClassification.Entry> row
                : ScheduleConstraintClassification.classifications().entrySet()) {
            assertThat(row.getValue())
                    .as("Constraint '%s' must carry a classification entry", row.getKey())
                    .isNotNull();
            assertThat(row.getValue().classification())
                    .as("Constraint '%s' must carry a non-null tag", row.getKey())
                    .isNotNull();
            assertThat(row.getValue().basis())
                    .as("Constraint '%s' must carry a non-blank basis", row.getKey())
                    .isNotBlank();
        }
    }

    @Test
    void exactlyTheFourNamedBreakConstraintsCarryNeedsShiftVariant() {
        Set<String> expected = Set.of(
                "Exactly one break", "Break duration", "Break blocked window", "Break start alignment");

        Set<String> actual = new HashSet<>();
        for (Map.Entry<String, ScheduleConstraintClassification.Entry> row
                : ScheduleConstraintClassification.classifications().entrySet()) {
            if (row.getValue().classification()
                    == ScheduleConstraintClassification.ModeClassification.NEEDS_SHIFT_VARIANT) {
                actual.add(row.getKey());
            }
        }

        assertThat(actual)
                .as("D-03's four named break constraints must be exactly the NEEDS_SHIFT_VARIANT set")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void exactlyThePreferenceConstraintsCarryOpenResolveInPhase15WithANamedOwner() {
        Set<String> expected = Set.of("Honour preferred start time", "Honour preferred break time");

        Set<String> actual = new HashSet<>();
        Map<String, ScheduleConstraintClassification.Entry> classifications =
                ScheduleConstraintClassification.classifications();
        for (Map.Entry<String, ScheduleConstraintClassification.Entry> row : classifications.entrySet()) {
            if (row.getValue().classification()
                    == ScheduleConstraintClassification.ModeClassification.OPEN_RESOLVE_IN_PHASE_15) {
                actual.add(row.getKey());
            }
        }

        assertThat(actual)
                .as("Exactly the two preference-comparison constraints must be OPEN_RESOLVE_IN_PHASE_15")
                .containsExactlyInAnyOrderElementsOf(expected);

        for (String name : expected) {
            String owner = classifications.get(name).owner();
            assertThat(owner)
                    .as("OPEN_RESOLVE_IN_PHASE_15 row '%s' must name a non-blank owner", name)
                    .isNotBlank();
            assertThat(owner)
                    .as("OPEN_RESOLVE_IN_PHASE_15 row '%s' must name Phase 15 as owner", name)
                    .contains("Phase 15");
        }
    }
}
