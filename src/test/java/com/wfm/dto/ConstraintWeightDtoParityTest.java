package com.wfm.dto;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeight;
import com.wfm.model.ConstraintWeights;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every tunable weight must be reachable through the API.
 *
 * <p><b>This guard exists because the same omission has happened twice.</b>
 * {@link ConstraintWeightsDto} carries a comment recording the first: Phase 15's three shift
 * weights were absent from the DTO, so "a 100:1 ratio between shiftWorkContiguity and
 * shiftEnvelopeCompliance became untunable without a deploy" — directly against the intent their
 * own migrations state, that hard-vs-soft is the column's value and never a code decision. The
 * second was {@code nonWorkingDaySeatWeight} (V52), added to the entity and the solver but not to
 * the DTO.
 *
 * <p>Nothing structural stopped either: the entity, the DTO and the two mapping directions in
 * {@code ConstraintWeightsService} are four separate hand-maintained lists. A migration's claim
 * that a weight is configurable is only true while the column is reachable, so this test derives
 * the weight set reflectively from the {@code @ConstraintWeight} annotations — the same technique
 * {@code ScheduleConstraintClassificationTest} uses — and fails the build the next time the lists
 * drift.
 */
class ConstraintWeightDtoParityTest {

    private static List<String> annotatedWeightFieldNames() {
        return Arrays.stream(ConstraintWeights.class.getDeclaredFields())
                .filter(f -> f.getAnnotation(ConstraintWeight.class) != null)
                .map(Field::getName)
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("every @ConstraintWeight on the entity has a field of the same name on the DTO")
    void everyWeightIsExposedOnTheDto() {
        Set<String> dtoFields = Arrays.stream(ConstraintWeightsDto.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        List<String> missing = annotatedWeightFieldNames().stream()
                .filter(name -> !dtoFields.contains(name))
                .toList();

        assertThat(missing)
                .as("Weights annotated @ConstraintWeight but absent from ConstraintWeightsDto — "
                        + "these cannot be read or tuned through the API, so their migration's "
                        + "'configurable per desk' claim is false until someone adds them here "
                        + "AND to both mapping directions in ConstraintWeightsService")
                .isEmpty();
    }

    @Test
    @DisplayName("the guard is meaningful — the entity really does declare weights to check")
    void theWeightSetIsNotAccidentallyEmpty() {
        // A reflective guard that silently matches nothing passes forever. Anchor it.
        assertThat(annotatedWeightFieldNames())
                .hasSizeGreaterThan(20)
                .contains("shiftEnvelopeComplianceWeight", "nonWorkingDaySeatWeight");
    }
}
