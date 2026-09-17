package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.wfm.config.TenantContext;
import com.wfm.dto.ConstraintWeightsDto;
import com.wfm.dto.ConstraintWeightsDto.ScoreDto;
import com.wfm.model.ConstraintWeights;
import com.wfm.repository.ConstraintWeightsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 17 plan 17-02, Task 2 — {@code ConstraintWeightsService.updateWeights}'s save-time
 * enforcement of D-07 (consistency's hard score must stay zero), D-08 (preference weight must
 * stay strictly below consistency's weight), and T-17-04 (the tolerance band must not go
 * negative). Written from scratch — {@code find src/test -iname "*ConstraintWeights*"} returned
 * nothing before this plan, so there is no existing harness to extend.
 *
 * <p>Plain {@code @ExtendWith(MockitoExtension.class)} unit test with a mocked
 * {@link ConstraintWeightsRepository}, constructing {@link ConstraintWeightsService} directly —
 * no {@code @SpringBootTest}/{@code @WebMvcTest}: this codebase has no Spring web-layer test
 * context at all (P-17, {@code GlobalExceptionHandlerTest}'s standing decision).
 *
 * <p>Every rejection case reads the MERGED entity (17-RESEARCH.md Pitfall 5) — a partial update
 * that never touches the field a check reads must still see that field's stored value, never a
 * null from the DTO.
 */
@ExtendWith(MockitoExtension.class)
class ConstraintWeightsServiceTest {

    private static final long TENANT_ID = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();

    private static final String D07_MESSAGE =
            "Usual Shift Consistency's hard score must be 0 — a hard score would risk making an "
                    + "otherwise-feasible schedule infeasible.";
    private static final String D08_MESSAGE =
            "Preferred Start (Shift Mode) weight must be lower than Usual Shift Consistency's weight.";
    private static final String TOLERANCE_MESSAGE =
            "Usual Shift Consistency Tolerance must be 0 minutes or more.";

    @Mock
    private ConstraintWeightsRepository constraintWeightsRepository;

    private ConstraintWeightsService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new ConstraintWeightsService(constraintWeightsRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Stored entity with soft-5 consistency, soft-1 preference, 60-minute tolerance. */
    private static ConstraintWeights storedEntity() {
        ConstraintWeights weights = new ConstraintWeights();
        weights.setTenantId(TENANT_ID);
        weights.setDeskId(DESK_ID);
        weights.setConsistentStartWeight(HardSoftScore.ofSoft(5));
        weights.setPreferredStartShiftModeWeight(HardSoftScore.ofSoft(1));
        weights.setConsistencyToleranceMinutes(60);
        return weights;
    }

    private void givenStoredEntity(ConstraintWeights entity) {
        when(constraintWeightsRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(Optional.of(entity));
    }

    private void stubSaveReturnsItsArgument() {
        when(constraintWeightsRepository.save(any(ConstraintWeights.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------------
    //  getWeights round-trips all three new fields
    // ------------------------------------------------------------------

    @Test
    void getWeights_returnsAllThreeNewFieldsPopulatedFromTheEntity() {
        givenStoredEntity(storedEntity());

        ConstraintWeightsDto dto = service.getWeights(DESK_ID);

        assertThat(dto.getConsistentStartWeight()).isEqualTo(new ScoreDto(0, 5));
        assertThat(dto.getConsistencyToleranceMinutes()).isEqualTo(60);
        assertThat(dto.getPreferredStartShiftModeWeight()).isEqualTo(new ScoreDto(0, 1));
    }

    // ------------------------------------------------------------------
    //  Partial update touching only the tolerance band leaves both weights untouched
    // ------------------------------------------------------------------

    @Test
    void partialUpdate_onlyToleranceMinutes_succeedsAndLeavesBothWeightsUntouched() {
        givenStoredEntity(storedEntity());
        stubSaveReturnsItsArgument();

        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setConsistencyToleranceMinutes(30);

        ConstraintWeightsDto result = service.updateWeights(DESK_ID, updates);

        assertThat(result.getConsistencyToleranceMinutes()).isEqualTo(30);
        assertThat(result.getConsistentStartWeight()).isEqualTo(new ScoreDto(0, 5));
        assertThat(result.getPreferredStartShiftModeWeight()).isEqualTo(new ScoreDto(0, 1));
        verify(constraintWeightsRepository).save(any(ConstraintWeights.class));
    }

    // ------------------------------------------------------------------
    //  Partial update: hard 0, soft above the stored preference weight -- succeeds
    // ------------------------------------------------------------------

    @Test
    void partialUpdate_consistentStartWeightHardZeroSoftAbovePreference_succeeds() {
        givenStoredEntity(storedEntity());
        stubSaveReturnsItsArgument();

        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setConsistentStartWeight(new ScoreDto(0, 10));

        ConstraintWeightsDto result = service.updateWeights(DESK_ID, updates);

        assertThat(result.getConsistentStartWeight()).isEqualTo(new ScoreDto(0, 10));
        verify(constraintWeightsRepository).save(any(ConstraintWeights.class));
    }

    // ------------------------------------------------------------------
    //  D-07: a non-zero hard component on consistency is rejected, save never called
    // ------------------------------------------------------------------

    @Test
    void partialUpdate_consistentStartWeightNonZeroHard_rejectedAndNeverSaved() {
        givenStoredEntity(storedEntity());

        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setConsistentStartWeight(new ScoreDto(1, 5));

        assertThatThrownBy(() -> service.updateWeights(DESK_ID, updates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(D07_MESSAGE);
        verify(constraintWeightsRepository, never()).save(any(ConstraintWeights.class));
    }

    // ------------------------------------------------------------------
    //  D-08 boundary: equal soft scores rejected, one below accepted
    // ------------------------------------------------------------------

    @Test
    void partialUpdate_preferredWeightSoftEqualToConsistencyWeightSoft_rejectedAndNeverSaved() {
        givenStoredEntity(storedEntity()); // consistentStartWeight soft 5

        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setPreferredStartShiftModeWeight(new ScoreDto(0, 5));

        assertThatThrownBy(() -> service.updateWeights(DESK_ID, updates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(D08_MESSAGE);
        verify(constraintWeightsRepository, never()).save(any(ConstraintWeights.class));
    }

    @Test
    void partialUpdate_preferredWeightSoftOneBelowConsistencyWeightSoft_accepted() {
        givenStoredEntity(storedEntity()); // consistentStartWeight soft 5
        stubSaveReturnsItsArgument();

        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setPreferredStartShiftModeWeight(new ScoreDto(0, 4));

        ConstraintWeightsDto result = service.updateWeights(DESK_ID, updates);

        assertThat(result.getPreferredStartShiftModeWeight()).isEqualTo(new ScoreDto(0, 4));
        verify(constraintWeightsRepository).save(any(ConstraintWeights.class));
    }

    // ------------------------------------------------------------------
    //  Merged-result semantics: the field the client did NOT send still participates
    // ------------------------------------------------------------------

    @Test
    void partialUpdate_loweringConsistencyToTheStoredPreferenceValue_rejectedAgainstTheMergedEntity() {
        // Stored: preference soft 1, consistency soft 5. Client sends ONLY a new consistency
        // weight of soft 1 -- equal to the UNCHANGED stored preference weight. The check must
        // read the merged entity (post-merge consistentStartWeight = soft 1), not the raw DTO
        // (which carries null for preferredStartShiftModeWeight), or this would either pass
        // wrongly or throw against a value the client never sent.
        givenStoredEntity(storedEntity());

        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setConsistentStartWeight(new ScoreDto(0, 1));

        assertThatThrownBy(() -> service.updateWeights(DESK_ID, updates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(D08_MESSAGE);
        verify(constraintWeightsRepository, never()).save(any(ConstraintWeights.class));
    }

    // ------------------------------------------------------------------
    //  T-17-04: a negative tolerance band is rejected; zero is accepted
    // ------------------------------------------------------------------

    @Test
    void partialUpdate_negativeToleranceMinutes_rejectedAndNeverSaved() {
        givenStoredEntity(storedEntity());

        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setConsistencyToleranceMinutes(-1);

        assertThatThrownBy(() -> service.updateWeights(DESK_ID, updates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(TOLERANCE_MESSAGE);
        verify(constraintWeightsRepository, never()).save(any(ConstraintWeights.class));
    }

    @Test
    void partialUpdate_zeroToleranceMinutes_accepted() {
        givenStoredEntity(storedEntity());
        stubSaveReturnsItsArgument();

        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setConsistencyToleranceMinutes(0);

        ConstraintWeightsDto result = service.updateWeights(DESK_ID, updates);

        assertThat(result.getConsistencyToleranceMinutes()).isZero();
        verify(constraintWeightsRepository).save(any(ConstraintWeights.class));
    }

    // ------------------------------------------------------------------
    //  A partial update touching only an unrelated existing weight is unaffected
    // ------------------------------------------------------------------

    @Test
    void partialUpdate_unrelatedFieldOnly_succeedsEvenAsTheOnlyFieldSent() {
        givenStoredEntity(storedEntity());
        stubSaveReturnsItsArgument();

        ConstraintWeightsDto updates = new ConstraintWeightsDto();
        updates.setSpecMatchWeight(new ScoreDto(1, 0));

        ConstraintWeightsDto result = service.updateWeights(DESK_ID, updates);

        assertThat(result.getSpecMatchWeight()).isEqualTo(new ScoreDto(1, 0));
        verify(constraintWeightsRepository).save(any(ConstraintWeights.class));
    }
}
