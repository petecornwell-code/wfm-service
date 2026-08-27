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

    /**
     * Phase 15 resolution (plan 15-06, ENVL-05): the four break constraints Phase 14 tagged
     * {@code NEEDS_SHIFT_VARIANT} are reclassified {@code MODE_GATED} — this phase determined
     * mode-gating (an added filter, body untouched) is sufficient and no shift-mode variant is
     * needed, so {@code NEEDS_SHIFT_VARIANT} is empty from this phase onward. The tag itself stays
     * in the vocabulary as a historical record (see {@link ScheduleConstraintClassification}'s
     * class javadoc), but no row may carry it any more — this test still derives its expectation
     * from the enum, not a hardcoded absence, so a future phase reintroducing the tag is caught.
     */
    @Test
    void needsShiftVariantIsEmptyAfterPhase15() {
        Set<String> actual = new HashSet<>();
        for (Map.Entry<String, ScheduleConstraintClassification.Entry> row
                : ScheduleConstraintClassification.classifications().entrySet()) {
            if (row.getValue().classification()
                    == ScheduleConstraintClassification.ModeClassification.NEEDS_SHIFT_VARIANT) {
                actual.add(row.getKey());
            }
        }

        assertThat(actual)
                .as("Phase 15 resolved D-03's four named break constraints to MODE_GATED; "
                        + "NEEDS_SHIFT_VARIANT must be empty")
                .isEmpty();
    }

    /**
     * Phase 15 resolution (plan 15-06, ENVL-05/P-26): both preference constraints Phase 14 left
     * {@code OPEN_RESOLVE_IN_PHASE_15} (naming this phase as owner) are reclassified
     * {@code MODE_GATED} — zero rows remain {@code OPEN_RESOLVE_IN_PHASE_15}, completing XCUT-05.
     */
    @Test
    void zeroConstraintsRemainOpenResolveInPhase15() {
        Set<String> actual = new HashSet<>();
        for (Map.Entry<String, ScheduleConstraintClassification.Entry> row
                : ScheduleConstraintClassification.classifications().entrySet()) {
            if (row.getValue().classification()
                    == ScheduleConstraintClassification.ModeClassification.OPEN_RESOLVE_IN_PHASE_15) {
                actual.add(row.getKey());
            }
        }

        assertThat(actual)
                .as("XCUT-05 requires zero OPEN_RESOLVE_IN_PHASE_15 rows after Phase 15")
                .isEmpty();
    }

    /**
     * The six constraints this task actively mode-gates (added filter, unchanged body), plus the
     * pre-existing "Shift envelope compliance" (plan 15-03), must all carry MODE_GATED, and the
     * two preference rows must keep PHASE_15_OWNER verbatim (P-27) even though they are no longer
     * OPEN — the Entry record permits an owner on any classification. Later tasks in this plan
     * (Band capacity, Break clustering) grow this set further; this test is intentionally scoped
     * to Task 1's own contribution.
     */
    @Test
    void thePhase15ModeGatedSetIsExactlyTheSevenExpectedRows() {
        Set<String> expected = Set.of(
                "Exactly one break", "Break duration", "Break blocked window", "Break start alignment",
                "Honour preferred start time", "Honour preferred break time",
                "Shift envelope compliance");

        Map<String, ScheduleConstraintClassification.Entry> classifications =
                ScheduleConstraintClassification.classifications();
        Set<String> actual = new HashSet<>();
        for (Map.Entry<String, ScheduleConstraintClassification.Entry> row : classifications.entrySet()) {
            if (row.getValue().classification()
                    == ScheduleConstraintClassification.ModeClassification.MODE_GATED) {
                actual.add(row.getKey());
            }
        }

        assertThat(actual)
                .as("MODE_GATED must be exactly the nine constraints whose behaviour depends on "
                        + "SchedulingMode after Phase 15")
                .containsExactlyInAnyOrderElementsOf(expected);

        for (String name : Set.of("Honour preferred start time", "Honour preferred break time")) {
            String owner = classifications.get(name).owner();
            assertThat(owner)
                    .as("MODE_GATED row '%s' must retain PHASE_15_OWNER verbatim (P-27)", name)
                    .isNotBlank()
                    .contains("Phase 15");
        }
    }
}
