package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.ShiftLibraryValidationController;
import com.wfm.dto.ShiftLibraryValidationResponse;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateBreakBandRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import com.wfm.util.DayWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

/**
 * SHLB-05/SHLB-06/MODE-03 coverage: structural envelope coverage (D-04), live-demand-only scope
 * (D-05), the D-02 grid re-check, and the D-06/D-07 exact-equality hours match — all through the
 * single {@code validate}/{@code requireShiftModeReady} pair (D-08). {@code
 * TimeslotGeneratorService.getLiveBounds} runs a Postgres-only native query that cannot execute
 * under H2, so it is supplied here as a {@code @MockitoBean} and stubbed per test.
 */
@DataJpaTest
@Import({ShiftLibraryValidationService.class, ShiftLibraryGenerationService.class, ShiftLibraryValidationController.class,
        ShiftTemplateService.class})
@ActiveProfiles("test")
class ShiftLibraryValidationServiceTest {

    @Autowired
    private ShiftLibraryValidationService service;

    // OVNT-05 Task 2, Test 6 (agreement): the real save path, imported alongside the report/gate
    // pair so the same desk/envelope can be driven through both without a second test class.
    @Autowired
    private ShiftTemplateService shiftTemplateService;

    @Autowired
    private ShiftLibraryValidationController controller;

    @Autowired
    private ShiftTemplateRepository shiftTemplateRepository;

    @Autowired
    private ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;

    @Autowired
    private StaffingRequirementRepository staffingRequirementRepository;

    @Autowired
    private TimeslotRepository timeslotRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private AgentDayHoursRepository agentDayHoursRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private DeskRepository deskRepository;

    @MockitoBean
    private TimeslotGeneratorService timeslotGeneratorService;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    // Phase 24 (N-1): a 06:00-anchored desk, where business Monday 2026-10-05 runs from calendar
    // Monday 06:00 to calendar Tuesday 06:00, so its post-midnight hours carry the calendar Tuesday.
    private static final LocalTime ANCHOR_0600 = LocalTime.of(6, 0);
    private static final LocalDate BIZ_SUN = LocalDate.of(2026, 10, 4);
    private static final LocalDate BIZ_MON = LocalDate.of(2026, 10, 5);
    private static final LocalDate CAL_TUE = LocalDate.of(2026, 10, 6);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
        when(timeslotGeneratorService.getLiveBounds(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------- Bandless-competition advisory ----------

    /**
     * The Phil-US 2026-10-06 incident, reduced: deleting the bands from one of two same-envelope
     * templates left the solver a legal no-break option, and 186 of 223 agent-days came back with no
     * break at hard 0 and a BETTER soft score. Every other check on this report was clean.
     */
    @Test
    void bandlessTemplateSharingAnEnvelopeWithABandedOne_isFlaggedAsStrictlyDominant() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "No bands", LocalTime.of(0, 0), LocalTime.of(9, 0),
                0, 0, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 9, 14), null);
        saveTemplate(deskId, "Banded", LocalTime.of(0, 0), LocalTime.of(9, 0),
                300, 60, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 9, 14), null);

        var advisories = service.validate(deskId).bandlessCompetitionAdvisories();

        assertThat(advisories).hasSize(1);
        var a = advisories.get(0);
        assertThat(a.bandlessTemplateName()).isEqualTo("No bands");
        assertThat(a.bandedTemplateName()).isEqualTo("Banded");
        assertThat(a.sameEnvelope()).isTrue();
        assertThat(a.sharedWeekdays()).hasSize(7);
        assertThat(a.message()).contains("no break bands").contains("always score better");
    }

    @Test
    void bandlessTemplateAloneIsNotFlagged_zeroBandsIsALegitimateNoBreakShape() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "No bands", LocalTime.of(0, 0), LocalTime.of(9, 0),
                0, 0, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 9, 14), null);

        assertThat(service.validate(deskId).bandlessCompetitionAdvisories())
                .isEmpty();
    }

    @Test
    void allTemplatesBandedIsNotFlagged() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "A", LocalTime.of(0, 0), LocalTime.of(9, 0),
                300, 60, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 9, 14), null);
        saveTemplate(deskId, "B", LocalTime.of(0, 0), LocalTime.of(9, 0),
                360, 60, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 9, 14), null);

        assertThat(service.validate(deskId).bandlessCompetitionAdvisories())
                .isEmpty();
    }

    /** Non-overlapping eras cannot compete for one agent-day, so there is nothing to warn about. */
    @Test
    void bandlessAndBandedInNonOverlappingEras_notFlagged() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "No bands", LocalTime.of(0, 0), LocalTime.of(9, 0),
                0, 0, EnumSet.allOf(DayOfWeek.class),
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20));
        saveTemplate(deskId, "Banded", LocalTime.of(0, 0), LocalTime.of(9, 0),
                300, 60, EnumSet.allOf(DayOfWeek.class),
                LocalDate.of(2026, 9, 21), null);

        assertThat(service.validate(deskId).bandlessCompetitionAdvisories())
                .isEmpty();
    }

    /** Disjoint weekdays likewise never compete for the same agent-day. */
    @Test
    void bandlessAndBandedOnDisjointWeekdays_notFlagged() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "Weekend no bands", LocalTime.of(0, 0), LocalTime.of(9, 0),
                0, 0, EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                LocalDate.of(2026, 9, 14), null);
        saveTemplate(deskId, "Weekday banded", LocalTime.of(0, 0), LocalTime.of(9, 0),
                300, 60, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                LocalDate.of(2026, 9, 14), null);

        assertThat(service.validate(deskId).bandlessCompetitionAdvisories())
                .isEmpty();
    }

    /** Overlapping but unequal envelopes still offer a no-break route, flagged without the
     *  strictly-dominant claim. */
    @Test
    void overlappingButDifferentEnvelopes_flaggedWithoutSameEnvelope() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "No bands", LocalTime.of(0, 0), LocalTime.of(6, 0),
                0, 0, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 9, 14), null);
        saveTemplate(deskId, "Banded", LocalTime.of(0, 0), LocalTime.of(9, 0),
                300, 60, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 9, 14), null);

        var advisories = service.validate(deskId).bandlessCompetitionAdvisories();

        assertThat(advisories).hasSize(1);
        assertThat(advisories.get(0).sameEnvelope()).isFalse();
        assertThat(advisories.get(0).message()).contains("envelopes overlap");
    }

    /** Advisory, never blocking: it must not refuse the SHIFT-mode switch on its own. */
    @Test
    void bandlessCompetitionDoesNotBlockShiftModeReadiness() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "No bands", LocalTime.of(0, 0), LocalTime.of(9, 0),
                0, 0, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 9, 14), null);
        saveTemplate(deskId, "Banded", LocalTime.of(0, 0), LocalTime.of(9, 0),
                300, 60, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 9, 14), null);

        var response = service.validate(deskId);
        assertThat(response.bandlessCompetitionAdvisories()).hasSize(1);
        // No demand on this desk, so readiness refuses for the demand reason -- never for ours.
        assertThatThrownBy(() -> service.requireShiftModeReady(deskId))
                .isInstanceOf(PreSolveValidationException.class)
                .hasMessageNotContaining("no break bands");
    }

    // ---------- Zero-demand refusal (D-05) ----------

    @Test
    void validate_noLiveDemand_reportsHasLiveDemandFalse() {
        UUID deskId = saveDesk(TENANT_A);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.hasLiveDemand()).isFalse();
    }

    @Test
    void requireShiftModeReady_noLiveDemand_throwsWithDemandDetailVerbatim() {
        UUID deskId = saveDesk(TENANT_A);

        assertThatThrownBy(() -> service.requireShiftModeReady(deskId))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException psve = (PreSolveValidationException) ex;
                    assertThat(psve.getDetails()).hasSize(1);
                    assertThat(psve.getDetails().get(0).field()).isEqualTo("demand");
                    assertThat(psve.getDetails().get(0).message()).isEqualTo(
                            "This desk has no staffing demand loaded. Upload staffing requirements "
                                    + "before switching to shift-scheduled mode.");
                });
    }

    @Test
    void validate_onlySnapshotDemandRows_reportsHasLiveDemandFalse() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, UUID.randomUUID());

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.hasLiveDemand()).isFalse();
        assertThat(response.uncoveredWindows()).isEmpty();
    }

    @Test
    void validate_liveDemandZeroTemplates_everyWindowUncoveredAndRefused() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.hasLiveDemand()).isTrue();
        assertThat(response.uncoveredWindows()).containsExactly("2026-01-05 09:00-09:30");
        assertThatThrownBy(() -> service.requireShiftModeReady(deskId))
                .isInstanceOf(PreSolveValidationException.class);
    }

    // ---------- Structural coverage (D-04) ----------

    @Test
    void validate_mondayWindowInsideEnvelope_covered() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalDate.of(2026, 1, 1), null);
        // Monday 2026-01-05
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).isEmpty();
    }

    @Test
    void validate_saturdayWindow_notCoveredByWeekdayTemplate() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalDate.of(2026, 1, 1), null);
        // Saturday 2026-01-10
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 10),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).containsExactly("2026-01-10 09:00-09:30");
    }

    @Test
    void validate_windowStartsBeforeEnvelope_notCovered() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(7, 30), LocalTime.of(8, 0), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).containsExactly("2026-01-05 07:30-08:00");
    }

    @Test
    void validate_windowEndsAfterEnvelope_notCovered() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(17, 0), LocalTime.of(17, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).containsExactly("2026-01-05 17:00-17:30");
    }

    @Test
    void validate_windowInsideBreak_notCoveredByThatTemplateAlone_butCoveredByOverlappingSecondTemplate() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        // Break 240 offset, 60 duration -> break 12:00-13:00
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(12, 0), LocalTime.of(12, 30), 1, null);

        ShiftLibraryValidationResponse soloResponse = service.validate(deskId);
        assertThat(soloResponse.uncoveredWindows()).containsExactly("2026-01-05 12:00-12:30");

        // Second template with break elsewhere (offset 0, so break 08:00-09:00) spans the slot too
        saveTemplate(deskId, "S2", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        ShiftLibraryValidationResponse combinedResponse = service.validate(deskId);
        assertThat(combinedResponse.uncoveredWindows()).isEmpty();
    }

    // ---------- Any-band coverage (D-02, Task 2) ----------

    @Test
    void validate_twoBandTemplate_selfCoversItsOwnBreakHour() {
        // D-02's worked example: 08:00-17:00 template with bands at offsets 240 and 300, both 60
        // minutes -- the 12:00-13:00 window is covered because band B (13:00-14:00 break) leaves
        // it worked, even though band A's own break (12:00-13:00) does not.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        ShiftTemplate template = saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        addBand(template, 300, 60, null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(12, 0), LocalTime.of(12, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).isEmpty();
    }

    @Test
    void validate_touchingBands_secondBandCoversWhereFirstDoesNot() {
        // Band A break 12:00-13:00 (offset 240), band B break 13:00-14:00 (offset 300) touch
        // exactly at 13:00 -- two distinct legal bands, not a duplicate (P-05). Band A leaves
        // 13:00-13:30 worked even though it sits inside band B's own break window.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        ShiftTemplate template = saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        addBand(template, 300, 60, null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(13, 0), LocalTime.of(13, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).isEmpty();
    }

    @Test
    void validate_oneBandTemplate_matchesPreMigrationSingleOffsetInvariant() {
        // D-02: a one-band template's coverage, misalignment, hours-advisory and
        // unsatisfiable-weekday verdicts are byte-identical to Phase 14's single-offset
        // predecessor for the exact same fixture asserted pre-migration in this suite.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        ShiftTemplate template = saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(12, 0), LocalTime.of(12, 30), 1, null);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("8.00"));
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        LocalTime.of(8, 0), LocalTime.of(20, 0), 30)));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).containsExactly("2026-01-05 12:00-12:30");
        assertThat(response.misalignedTemplates()).isEmpty();
        assertThat(response.hoursAdvisories()).noneMatch(a -> a.templateId().equals(template.getId()));
        assertThat(response.unsatisfiableWeekdays()).isEmpty();
    }

    @Test
    void validate_zeroDurationBreak_neverExcludesAnySlot() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(12, 0), LocalTime.of(12, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).isEmpty();
    }

    @Test
    void validate_zeroRequiredFTEs_ignoredNeverUncovered() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 0, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.hasLiveDemand()).isFalse();
        assertThat(response.uncoveredWindows()).isEmpty();
    }

    @Test
    void validate_twoSpecializationsSameTimeslot_producesAtMostOneUncoveredWindow() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec1 = saveSpecialization(TENANT_A, deskId, "S1");
        Specialization spec2 = saveSpecialization(TENANT_A, deskId, "S2");
        Timeslot slot = saveTimeslot(TENANT_A, deskId, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), null);
        saveDemandForTimeslot(TENANT_A, deskId, spec1, slot, 1, null);
        saveDemandForTimeslot(TENANT_A, deskId, spec2, slot, 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).containsExactly("2026-01-05 09:00-09:30");
    }

    @Test
    void validate_templateEffectiveRangeExcludesDemandDate_notCovered() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 2, 1), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).containsExactly("2026-01-05 09:00-09:30");
    }

    @Test
    void validate_uncoveredWindows_orderedByDateThenStartTime_stableAcrossCalls() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 6),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(10, 0), LocalTime.of(10, 30), 1, null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        List<String> firstCall = service.validate(deskId).uncoveredWindows();
        List<String> secondCall = service.validate(deskId).uncoveredWindows();

        assertThat(firstCall).containsExactly(
                "2026-01-05 09:00-09:30", "2026-01-05 10:00-10:30", "2026-01-06 09:00-09:30");
        assertThat(secondCall).isEqualTo(firstCall);
    }

    // ---------- Grid re-check (D-02) ----------

    @Test
    void validate_boundsPresent_offGridTemplate_appearsInMisalignedTemplates() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "S1", LocalTime.of(8, 15), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        LocalTime.of(8, 0), LocalTime.of(20, 0), 30)));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.misalignedTemplates()).containsExactly("S1 (2026-01-01)");
    }

    @Test
    void requireShiftModeReady_offGridTemplate_withLiveDemand_throwsWithGridDetail() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 15), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        LocalTime.of(8, 0), LocalTime.of(20, 0), 30)));

        assertThatThrownBy(() -> service.requireShiftModeReady(deskId))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException psve = (PreSolveValidationException) ex;
                    assertThat(psve.getDetails()).anySatisfy(d -> {
                        assertThat(d.field()).isEqualTo("grid");
                        assertThat(d.value()).isEqualTo("S1 (2026-01-01)");
                    });
                });
    }

    @Test
    void validate_boundsAbsent_misalignedTemplatesEmpty() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "S1", LocalTime.of(8, 15), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.misalignedTemplates()).isEmpty();
    }

    @Test
    void validate_retiredTemplateOffGrid_excludedFromMisalignedTemplates() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "S1", LocalTime.of(8, 15), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        LocalTime.of(8, 0), LocalTime.of(20, 0), 30)));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.misalignedTemplates()).isEmpty();
    }

    // ---------- Operating-window escapes (OVNT-05/D-06/D-07, Task 2) ----------

    @Test
    void requireShiftModeReady_overnightTemplateOutsideOperatingWindow_throwsNamingTemplate() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, LocalTime.of(21, 0));
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Overnight", LocalTime.of(22, 0), LocalTime.of(7, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 5), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(23, 0), LocalTime.of(23, 30), 1, null);
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        LocalTime.of(22, 0), LocalTime.of(6, 0), 60)));

        ShiftLibraryValidationResponse response = service.validate(deskId);
        assertThat(response.operatingWindowFindings()).singleElement().satisfies(f -> {
            assertThat(f.blocking()).isTrue();
            assertThat(f.templateName()).isEqualTo("Overnight");
        });

        assertThatThrownBy(() -> service.requireShiftModeReady(deskId))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException psve = (PreSolveValidationException) ex;
                    assertThat(psve.getDetails()).anySatisfy(d -> {
                        assertThat(d.field()).isEqualTo("operatingWindow");
                        assertThat(d.value()).contains("Overnight");
                    });
                });
    }

    @Test
    void validate_sameDayTemplateOutsideOperatingWindow_reportsNonBlockingFinding_requireShiftModeReadyDoesNotThrowForIt() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Early", LocalTime.of(8, 0), LocalTime.of(19, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 5), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("11.00"));
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        LocalTime.of(8, 0), LocalTime.of(18, 0), 60)));

        ShiftLibraryValidationResponse response = service.validate(deskId);
        assertThat(response.operatingWindowFindings()).singleElement().satisfies(f -> {
            assertThat(f.blocking()).isFalse();
            assertThat(f.templateName()).isEqualTo("Early");
        });

        assertThatCode(() -> service.requireShiftModeReady(deskId)).doesNotThrowAnyException();
    }

    @Test
    void validate_templateFullyInsideOperatingWindow_reportsNoFinding() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "Inside", LocalTime.of(9, 0), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 5), null);
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        LocalTime.of(8, 0), LocalTime.of(18, 0), 60)));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.operatingWindowFindings()).isEmpty();
    }

    @Test
    void validate_boundsAbsent_operatingWindowFindingsEmpty() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, LocalTime.of(21, 0));
        saveTemplate(deskId, "Overnight", LocalTime.of(22, 0), LocalTime.of(7, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 5), null);
        // getLiveBounds stubbed to Optional.empty() in setUp() by default.

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.operatingWindowFindings()).isEmpty();
    }

    @Test
    void validate_retiredTemplateOutsideOperatingWindow_excludedFromOperatingWindowFindings() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "Retired", LocalTime.of(20, 0), LocalTime.of(22, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        LocalTime.of(8, 0), LocalTime.of(18, 0), 60)));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.operatingWindowFindings()).isEmpty();
    }

    @Test
    void requireShiftModeReady_and_createShiftTemplate_agreeOnTheSameOvernightEscape() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, LocalTime.of(21, 0));
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        LocalTime.of(22, 0), LocalTime.of(6, 0), 60)));
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(7, 0);

        // The save path refuses this exact envelope.
        ShiftTemplateRequest req = new ShiftTemplateRequest("Overnight", start, end,
                List.of(), Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 5), null);
        assertThatThrownBy(() -> shiftTemplateService.createShiftTemplate(deskId, req))
                .isInstanceOf(IllegalArgumentException.class);

        // The identical pair, stored directly (as if it predated the check), is reported blocking --
        // the agreement property the shared predicate exists to guarantee.
        saveTemplate(deskId, "Overnight", start, end, 0, 0, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 5), null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.operatingWindowFindings()).singleElement()
                .satisfies(f -> assertThat(f.blocking()).isTrue());
    }

    // ---------- Hours match (D-06/D-07) ----------

    @Test
    void validate_netDurationMatchesAgentHours_noAdvisory() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplate template = saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        assertThat(template.getNetHours(60, DayWindow.anchoredAt(LocalTime.MIDNIGHT))).isEqualByComparingTo("8.00");
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("8.00"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.hoursAdvisories()).noneMatch(a -> a.weekday() == DayOfWeek.MONDAY);
    }

    @Test
    void validate_netDurationMismatch_producesAdvisoryVerbatim() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("7.75"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.hoursAdvisories()).anySatisfy(a -> {
            assertThat(a.templateName()).isEqualTo("S1");
            assertThat(a.weekday()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(a.message()).isEqualTo(
                    "This shift's net duration (8.00h) doesn't match any agent's contracted hours "
                            + "on Monday. It will still save — update contracted hours or this template "
                            + "later if needed.");
        });
    }

    @Test
    void validate_scaleInsensitiveMatch_8point0MatchesNetDuration8point00() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("8.0"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.hoursAdvisories()).noneMatch(a -> a.weekday() == DayOfWeek.MONDAY);
    }

    @Test
    void validate_noToleranceBand_7point75DoesNotMatch8point00() {
        UUID deskId = saveDesk(TENANT_A);
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("7.75"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.hoursAdvisories()).anyMatch(a -> a.weekday() == DayOfWeek.MONDAY);
    }

    // ------------------------------------------------------------------
    //  Break concentration advisory — the inverse of the shortfall check.
    //
    //  findCapacityAdvisories fires only when capacity is too LOW and skips any template with a
    //  blank-capacity band ("unlimited by construction"). Unlimited is the DEFAULT — V40 migrates
    //  every Phase 14 break forward with a NULL capacity — so the one-band/blank-capacity library
    //  was inspected by nothing. Observed live on Stubhub (EN): the page reported no finding, and
    //  the solve then gave 18 of 18 Late agents the same 16:00 break, emptied the hour, and seated
    //  agents through their own break to hold it.
    // ------------------------------------------------------------------

    @Test
    void validate_singleUnlimitedBand_wholeShiftCouldBreakTogether_isReported() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        // One band, capacity null — exactly what V40 produces and what the live desk had.
        saveTemplate(deskId, "Late", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        for (int i = 0; i < 6; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    DayOfWeek.MONDAY, new BigDecimal("8.00"));
        }
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        // The existing shortfall check is structurally silent here — that is the gap being closed.
        assertThat(response.capacityAdvisories()).isEmpty();
        assertThat(response.breakConcentrationAdvisories()).singleElement().satisfies(a -> {
            assertThat(a.templateName()).isEqualTo("Late");
            assertThat(a.weekday()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(a.bandCount()).isEqualTo(1);
            assertThat(a.admissibleHeadcount()).isEqualTo(6);
            assertThat(a.worstCaseSimultaneousBreak()).isEqualTo(6); // all of them
            assertThat(a.message()).contains("blank (unlimited) capacity");
            assertThat(a.message()).contains("Up to 6 of 6");
        });
    }

    @Test
    void validate_bandsCappedAtHalfTheShift_staysQuiet() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        ShiftTemplate t = saveTemplate(deskId, "Late", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        // saveTemplate already added an unlimited band; replace the set with capped ones.
        shiftTemplateBreakBandRepository.deleteAll(
                shiftTemplateBreakBandRepository.findByTenantIdAndShiftTemplateIdOrderByOffsetMinutesAsc(TENANT_A, t.getId()));
        addBand(t, 180, 60, 3);
        addBand(t, 240, 60, 3);
        for (int i = 0; i < 6; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    DayOfWeek.MONDAY, new BigDecimal("8.00"));
        }
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        // Largest band admits 3 of 6 — exactly half. The threshold is strictly greater-than, so an
        // even split is a legitimate shape and must not nag.
        assertThat(service.validate(deskId).breakConcentrationAdvisories()).isEmpty();
    }

    @Test
    void validate_shiftTooSmallToConcentrate_neverReported() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Late", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        // 3 agents — below the advisory floor. Three people breaking together is a normal shift.
        for (int i = 0; i < 3; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    DayOfWeek.MONDAY, new BigDecimal("8.00"));
        }
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        assertThat(service.validate(deskId).breakConcentrationAdvisories()).isEmpty();
    }

    @Test
    void validate_templateWithNoBands_neverReported() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        // Zero bands means no break at all (P-01) — there is no break to concentrate.
        saveTemplate(deskId, "NoBreak", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        for (int i = 0; i < 6; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    DayOfWeek.MONDAY, new BigDecimal("9.00"));
        }
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        assertThat(service.validate(deskId).breakConcentrationAdvisories()).isEmpty();
    }

    // ------------------------------------------------------------------
    //  Peak-hour shortfall — the blind spot every PER-DATE aggregate shares.
    //
    //  Observed live: 143 demand-hours against 200 staffed (140% coverage, every aggregate check
    //  clean) while Saturday 11:00 needed 44 FTE against 25 agents on the entire desk. Short by 19
    //  people and invisible to everything, because a daily total says nothing about how that day's
    //  demand is distributed across its hours.
    // ------------------------------------------------------------------

    @Test
    void validate_hourNeedingMoreAgentsThanExist_isReportedWithTheShortfall() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Day", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        // Only 3 agents on the desk...
        for (int i = 0; i < 3; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    DayOfWeek.MONDAY, new BigDecimal("8.00"));
        }
        // ...against an hour demanding 10. Unmeetable by every agent working at once.
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(10, 0), 10, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.peakShortfallAdvisories()).singleElement().satisfies(a -> {
            assertThat(a.startTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(a.requiredFTEs()).isEqualTo(10);
            assertThat(a.reachableAgents()).isEqualTo(3);
            assertThat(a.shortfall()).isEqualTo(7);
            assertThat(a.message()).contains("short by 7");
            assertThat(a.message()).contains("more rostered agents");
        });
    }

    @Test
    void validate_hourWithinReach_isNotReported() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Day", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        for (int i = 0; i < 5; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    DayOfWeek.MONDAY, new BigDecimal("8.00"));
        }
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(10, 0), 4, null);

        assertThat(service.validate(deskId).peakShortfallAdvisories()).isEmpty();
    }

    @Test
    void validate_agentsWhoseShiftIsOnBreakThatHour_doNotCountAsReachable() {
        // The distinction that makes this check honest. The template's ONLY band puts every agent
        // on break 12:00-13:00, so nobody holding it can be working 12:00 — the hour is out of
        // reach even though the envelope spans it. Counting the envelope rather than the (template,
        // band) pair would report these agents as available and hide the shortfall.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Day", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        for (int i = 0; i < 5; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    DayOfWeek.MONDAY, new BigDecimal("8.00"));
        }
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(12, 0), LocalTime.of(13, 0), 2, null);

        // 12:00 is inside 08:00-17:00 but is the band's break window, so no template/band pair
        // covers it -> the hour has no reach at all, which is uncoveredWindows' finding, not this
        // one. This check must not double-report it.
        ShiftLibraryValidationResponse response = service.validate(deskId);
        assertThat(response.uncoveredWindows()).isNotEmpty();
        assertThat(response.peakShortfallAdvisories()).isEmpty();
    }

    @Test
    void validate_shortfallsAreOrderedWorstFirst() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Day", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        for (int i = 0; i < 2; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    DayOfWeek.MONDAY, new BigDecimal("8.00"));
        }
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(10, 0), 5, null);   // short by 3
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(10, 0), LocalTime.of(11, 0), 20, null); // short by 18

        assertThat(service.validate(deskId).peakShortfallAdvisories())
                .extracting(ShiftLibraryValidationResponse.PeakShortfallAdvisory::shortfall)
                .containsExactly(18L, 3L);
    }

    @Test
    void requireShiftModeReady_advisoriesNeverThrowAndNeverAppearInDetails() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("8.00"));
        // Live demand on Monday, satisfiable by S1 -> requireShiftModeReady must not throw
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);
        // Also add a mismatched Tuesday template so an advisory exists but must not appear in details
        saveTemplate(deskId, "S2", LocalTime.of(8, 0), LocalTime.of(16, 45), 0, 0,
                Set.of(DayOfWeek.TUESDAY), LocalDate.of(2026, 1, 1), null);

        assertThatCode(() -> service.requireShiftModeReady(deskId)).doesNotThrowAnyException();

        ShiftLibraryValidationResponse response = service.validate(deskId);
        assertThat(response.hoursAdvisories()).isNotEmpty();
    }

    @Test
    void validate_demandedMondayNoWorkablePair_mondayUnsatisfiable_requireThrowsWithContractedHoursDetail() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("7.75"));
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);
        assertThat(response.unsatisfiableWeekdays()).containsExactly("MONDAY");

        assertThatThrownBy(() -> service.requireShiftModeReady(deskId))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException psve = (PreSolveValidationException) ex;
                    assertThat(psve.getDetails()).anySatisfy(d -> {
                        assertThat(d.field()).isEqualTo("contractedHours");
                        assertThat(d.message()).isEqualTo(
                                "1 weekday(s) have no shift template any agent's contracted hours can "
                                        + "satisfy: Monday. Add or adjust a template, or update contracted "
                                        + "hours, before switching modes.");
                    });
                });
    }

    @Test
    void validate_weekdayWithNoDemand_neverReportedUnsatisfiable() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        // No template exists for Tuesday at all, but there is no demand on any Tuesday either
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5), // Monday
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("8.00"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.unsatisfiableWeekdays()).doesNotContain("TUESDAY");
    }

    @Test
    void validate_liveDemandAndTemplatesButZeroAgents_everyDemandedWeekdayUnsatisfiable() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), LocalDate.of(2026, 1, 1), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5), // Monday
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 6), // Tuesday
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.unsatisfiableWeekdays()).containsExactly("MONDAY", "TUESDAY");
    }

    @Test
    void validate_agentsWithNoDayHoursRows_fallBackToTheDeskDefault_weekdayIsSatisfiable() {
        // UAT test 17b. An agent whose per-day hours were never edited has NO agent_day_hours row,
        // and their contracted hours are the desk default -- which is what the solver uses
        // (SolverService.resolveEffectiveHours falls through dayHoursMap to the schedule default)
        // and what the agent screen displays (DeskAgentResponse.dayHours.X.effectiveHours). The
        // validator read only the rows and applied no fallback, so a desk whose agents had never
        // had per-day hours edited reported EVERY weekday unsatisfiable and could not be switched
        // into SHIFT mode at all -- with a message naming "contracted hours" the UI already showed
        // as correct.
        //
        // Desk default is 8.00; the template's net is 9h envelope - 1h band = 8.00h, so it matches
        // exactly. The contrast with the zero-agents test directly above is the point: no agents
        // still means no hours and an unsatisfiable weekday, and that must not change.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        saveAgent(TENANT_A, deskId, "A1"); // deliberately NO saveAgentDayHours
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.unsatisfiableWeekdays()).isEmpty();
        assertThat(response.hoursAdvisories()).isEmpty();
        assertThatCode(() -> service.requireShiftModeReady(deskId)).doesNotThrowAnyException();
    }

    @Test
    void validate_agentWithADayHoursRow_stillWinsOverTheDeskDefault() {
        // The fallback must not override an explicit row. This agent's Monday row says 7.75h
        // against a desk default of 8.00 and a template net of 8.00, so Monday stays unsatisfiable
        // exactly as it did before the fallback existed -- the row is authoritative where present.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("7.75"));
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.unsatisfiableWeekdays()).containsExactly("MONDAY");
    }

    // ---------- Capacity shortfall advisory (D-03 residual risk, Task 3) ----------

    @Test
    void validate_allBlankCapacities_noAdvisory() {
        UUID deskId = saveDesk(TENANT_A);
        // saveTemplate's own band is created with a blank (null) capacity by construction.
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        Agent a1 = saveAgent(TENANT_A, deskId, "A1");
        Agent a2 = saveAgent(TENANT_A, deskId, "A2");
        saveAgentDayHours(TENANT_A, a1, DayOfWeek.MONDAY, new BigDecimal("8.00"));
        saveAgentDayHours(TENANT_A, a2, DayOfWeek.MONDAY, new BigDecimal("8.00"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.capacityAdvisories()).isEmpty();
    }

    @Test
    void validate_capacityAtOrAboveHeadcount_noAdvisory() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplate template = saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        addBand(template, 240, 60, 2);
        Agent a1 = saveAgent(TENANT_A, deskId, "A1");
        Agent a2 = saveAgent(TENANT_A, deskId, "A2");
        saveAgentDayHours(TENANT_A, a1, DayOfWeek.MONDAY, new BigDecimal("8.00"));
        saveAgentDayHours(TENANT_A, a2, DayOfWeek.MONDAY, new BigDecimal("8.00"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.capacityAdvisories()).isEmpty();
    }

    @Test
    void validate_capacityBelowHeadcount_producesOneAdvisoryNamingAllFour() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplate template = saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        addBand(template, 240, 60, 1);
        Agent a1 = saveAgent(TENANT_A, deskId, "A1");
        Agent a2 = saveAgent(TENANT_A, deskId, "A2");
        saveAgentDayHours(TENANT_A, a1, DayOfWeek.MONDAY, new BigDecimal("8.00"));
        saveAgentDayHours(TENANT_A, a2, DayOfWeek.MONDAY, new BigDecimal("8.00"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.capacityAdvisories()).hasSize(1);
        ShiftLibraryValidationResponse.CapacityAdvisory advisory = response.capacityAdvisories().get(0);
        assertThat(advisory.templateId()).isEqualTo(template.getId());
        assertThat(advisory.templateName()).isEqualTo("S1");
        assertThat(advisory.weekday()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(advisory.capacityTotal()).isEqualTo(1);
        assertThat(advisory.admissibleHeadcount()).isEqualTo(2);
        assertThat(advisory.message()).isEqualTo(
                "Shift template 'S1' has total break-band capacity 1 on Monday, but 2 agent(s) "
                        + "could be scheduled on it that day. Increase capacity or add a band before solving.");
    }

    @Test
    void validate_mixedBlankAndSetCapacity_clearsTheWholeTemplate() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplate template = saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        addBand(template, 240, 60, 1);
        addBand(template, 300, 60, null); // blank band -> unlimited, clears the whole template
        Agent a1 = saveAgent(TENANT_A, deskId, "A1");
        Agent a2 = saveAgent(TENANT_A, deskId, "A2");
        saveAgentDayHours(TENANT_A, a1, DayOfWeek.MONDAY, new BigDecimal("8.00"));
        saveAgentDayHours(TENANT_A, a2, DayOfWeek.MONDAY, new BigDecimal("8.00"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.capacityAdvisories()).isEmpty();
    }

    @Test
    void validate_retiredTemplateWithShortfall_excludedFromCapacityAdvisories() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplate template = saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));
        addBand(template, 240, 60, 1);
        Agent a1 = saveAgent(TENANT_A, deskId, "A1");
        Agent a2 = saveAgent(TENANT_A, deskId, "A2");
        saveAgentDayHours(TENANT_A, a1, DayOfWeek.MONDAY, new BigDecimal("8.00"));
        saveAgentDayHours(TENANT_A, a2, DayOfWeek.MONDAY, new BigDecimal("8.00"));

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.capacityAdvisories()).isEmpty();
    }

    // ---------- Controller (Task 2) ----------

    @Test
    void controller_uncoveredWindow_returnsReportInsteadOfThrowing() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        ShiftLibraryValidationResponse response = controller.validateShiftLibrary(deskId);

        assertThat(response.uncoveredWindows()).containsExactly("2026-01-05 09:00-09:30");
    }

    // ---------- Tenancy ----------

    @Test
    void validate_crossTenant_seesNeitherTemplatesNorDemandOfRealTenant() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        saveDemand(TENANT_A, deskId, spec, LocalDate.of(2026, 1, 5),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1, null);

        TenantContext.setTenantId(TENANT_B);
        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.hasLiveDemand()).isFalse();
        assertThat(response.uncoveredWindows()).isEmpty();
    }

    // ---------- Phase 24 (N-1): demand windows keyed on the business date ----------

    /**
     * Audit flow F-2: a Monday-only overnight template on a 06:00 desk covers its OWN business
     * Monday, including the hours that fall on calendar Tuesday. Before the fix the validator read
     * the calendar date, called those two windows Tuesday's, found no Tuesday template, reported
     * TUESDAY unsatisfiable and refused the SHIFT-mode switch.
     */
    @Test
    void requireShiftModeReady_mondayOnlyOvernightTemplate_acceptsItsOwnPostMidnightHours() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, ANCHOR_0600);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Overnight", LocalTime.of(21, 0), LocalTime.of(6, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 10, 1), null);
        saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A1"), DayOfWeek.MONDAY, new BigDecimal("9.00"));
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, BIZ_MON, BIZ_MON,
                LocalTime.of(22, 0), LocalTime.of(23, 0), 1);
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, CAL_TUE, BIZ_MON,
                LocalTime.of(1, 0), LocalTime.of(2, 0), 1);
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, CAL_TUE, BIZ_MON,
                LocalTime.of(5, 0), LocalTime.of(6, 0), 1);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).isEmpty();
        assertThat(response.unsatisfiableWeekdays()).isEmpty();
        assertThatCode(() -> service.requireShiftModeReady(deskId)).doesNotThrowAnyException();
    }

    /** SOLV-07: business Sunday's 01:00 hour sits on calendar Monday but is NOT Monday's to cover. */
    @Test
    void validate_previousBusinessDaysPostMidnightWindow_isNotCreditedToTheCalendarWeekdaysTemplate() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, ANCHOR_0600);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Overnight", LocalTime.of(21, 0), LocalTime.of(6, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 10, 1), null);
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, BIZ_MON, BIZ_SUN,
                LocalTime.of(1, 0), LocalTime.of(2, 0), 1);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).singleElement().satisfies(w -> {
            assertThat(w).startsWith("2026-10-04 ");
            assertThat(w).contains("01:00-02:00");
        });
    }

    /** isEffectiveOn is judged on the business date, not on the calendar date the hour falls on. */
    @Test
    void validate_effectiveFromIsJudgedOnTheBusinessDate() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, ANCHOR_0600);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Overnight", LocalTime.of(21, 0), LocalTime.of(6, 0), 0, 0,
                Set.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), BIZ_MON, null);
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, BIZ_MON, BIZ_SUN,
                LocalTime.of(1, 0), LocalTime.of(2, 0), 1);

        ShiftLibraryValidationResponse response = service.validate(deskId);

        assertThat(response.uncoveredWindows()).singleElement()
                .satisfies(w -> assertThat(w).startsWith("2026-10-04 "));
    }

    // ---------- Phase 24 (D-02): one business-date label, calendar disclosed only when it differs ----------

    @Test
    void validate_uncoveredPostMidnightWindow_namesTheBusinessDayAndDisclosesTheCalendarDate() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, ANCHOR_0600);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Overnight", LocalTime.of(21, 0), LocalTime.of(6, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 10, 1), null);
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, BIZ_MON, BIZ_SUN,
                LocalTime.of(1, 0), LocalTime.of(2, 0), 1);

        String expected = "2026-10-04 (Sun) 01:00-02:00 [calendar 2026-10-05]";
        assertThat(service.validate(deskId).uncoveredWindows()).containsExactly(expected);
        assertThatThrownBy(() -> service.requireShiftModeReady(deskId))
                .isInstanceOfSatisfying(PreSolveValidationException.class, psve -> {
                    assertThat(psve.getMessage()).isEqualTo("1 demand window(s) have no covering shift template");
                    assertThat(psve.getDetails()).anySatisfy(d -> {
                        assertThat(d.field()).isEqualTo("coverage");
                        assertThat(d.message()).isEqualTo(expected);
                    });
                });
    }

    @Test
    void validate_uncoveredWindowsOnAnAnchoredDesk_areOrderedFromTheDayStart() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, ANCHOR_0600);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        // Saved in the WRONG order on purpose: the post-midnight hour first.
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, CAL_TUE, BIZ_MON,
                LocalTime.of(1, 0), LocalTime.of(2, 0), 1);
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, BIZ_MON, BIZ_MON,
                LocalTime.of(21, 0), LocalTime.of(22, 0), 1);

        assertThat(service.validate(deskId).uncoveredWindows()).containsExactly(
                "2026-10-05 21:00-22:00",
                "2026-10-05 (Mon) 01:00-02:00 [calendar 2026-10-06]");
    }

    /**
     * BDAY-05 adjacency: a window ENDING at the anchor belongs to the day that is ending; a window
     * STARTING at it belongs to the day that is beginning, and sits on one date, so it is undecorated.
     */
    @Test
    void validate_windowStartingExactlyAtTheDayStart_belongsToTheNewBusinessDay_andIsNotDecorated() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, ANCHOR_0600);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveTemplate(deskId, "Overnight", LocalTime.of(21, 0), LocalTime.of(6, 0), 0, 0,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 10, 1), null);
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, CAL_TUE, BIZ_MON,
                LocalTime.of(5, 0), LocalTime.of(6, 0), 1);
        saveDemandAnchored(TENANT_A, deskId, spec, ANCHOR_0600, CAL_TUE, CAL_TUE,
                LocalTime.of(6, 0), LocalTime.of(7, 0), 1);

        assertThat(service.validate(deskId).uncoveredWindows()).containsExactly("2026-10-06 06:00-07:00");
    }

    /** The 00:00 control: every string is byte-identical to what it was before this phase. */
    @Test
    void validate_midnightDesk_uncoveredStringsStayByteIdentical() {
        UUID deskId = saveDeskWithDayStart(TENANT_A, LocalTime.MIDNIGHT);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        LocalDate saturday = LocalDate.of(2026, 1, 10);
        saveDemandAnchored(TENANT_A, deskId, spec, LocalTime.MIDNIGHT, saturday, saturday,
                LocalTime.of(23, 0), LocalTime.MIDNIGHT, 1);
        saveDemandAnchored(TENANT_A, deskId, spec, LocalTime.MIDNIGHT, saturday, saturday,
                LocalTime.of(9, 0), LocalTime.of(9, 30), 1);

        assertThat(service.validate(deskId).uncoveredWindows())
                .containsExactly("2026-01-10 09:00-09:30", "2026-01-10 23:00-00:00");
    }

    // ---------- helpers ----------

    private UUID saveDesk(long tenantId) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }

    // OVNT-05 (Task 2): mirrors ShiftTemplateServiceTest's helper of the same name.
    private UUID saveDeskWithDayStart(long tenantId, LocalTime dayStart) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        desk.setDayStart(dayStart);
        return deskRepository.save(desk).getId();
    }

    /**
     * D-01 note: this helper's signature is kept identical to Phase 14's scalar-field version so
     * every pre-existing test body below is untouched (mechanical port) -- a duration > 0 becomes
     * exactly one persisted band, mirroring V40's own migration fan-out rule (a zero duration
     * yields zero bands, "no break").
     */
    private ShiftTemplate saveTemplate(UUID deskId, String name, LocalTime start, LocalTime end,
                                        int breakOffsetMinutes, int breakDurationMinutes,
                                        Set<DayOfWeek> weekdays, LocalDate effectiveFrom, LocalDate effectiveTo) {
        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(TENANT_A);
        template.setDeskId(deskId);
        template.setName(name);
        template.setStartTime(start);
        template.setEndTime(end);
        template.setValidWeekdays(weekdays);
        template.setEffectiveFrom(effectiveFrom);
        template.setEffectiveTo(effectiveTo);
        ShiftTemplate saved = shiftTemplateRepository.save(template);
        if (breakDurationMinutes > 0) {
            addBand(saved, breakOffsetMinutes, breakDurationMinutes, null);
        }
        return saved;
    }

    private ShiftTemplateBreakBand addBand(ShiftTemplate template, int offsetMinutes, int durationMinutes,
                                            Integer capacity) {
        ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
        band.setTenantId(TENANT_A);
        band.setShiftTemplate(template);
        band.setOffsetMinutes(offsetMinutes);
        band.setDurationMinutes(durationMinutes);
        band.setCapacity(capacity);
        return shiftTemplateBreakBandRepository.save(band);
    }

    private Specialization saveSpecialization(long tenantId, UUID deskId, String name) {
        Specialization spec = new Specialization();
        spec.setTenantId(tenantId);
        spec.setDeskId(deskId);
        spec.setName(name);
        return specializationRepository.save(spec);
    }

    private Timeslot saveTimeslot(long tenantId, UUID deskId, LocalDate date, LocalTime start, LocalTime end,
                                   UUID scheduleId) {
        Timeslot timeslot = new Timeslot();
        timeslot.setTenantId(tenantId);
        timeslot.setDeskId(deskId);
        timeslot.setScheduleId(scheduleId);
        timeslot.setDate(date);
        timeslot.setStartTime(start);
        timeslot.setEndTime(end);
        timeslot.setBusinessDate(date);
        return timeslotRepository.save(timeslot);
    }

    /**
     * Persists a live Timeslot carrying an EXPLICIT business date — an independent oracle for the
     * fixture, not a re-derivation. It first asserts the supplied business date really is what
     * {@link DayWindow#businessDateOf} gives for the anchor, so a mis-built fixture fails loudly
     * instead of testing an impossible row.
     */
    private StaffingRequirement saveDemandAnchored(long tenantId, UUID deskId, Specialization spec,
                                                    LocalTime anchor, LocalDate calendarDate,
                                                    LocalDate businessDate, LocalTime start, LocalTime end,
                                                    int requiredFTEs) {
        assertThat(DayWindow.businessDateOf(anchor, calendarDate, start))
                .as("fixture: business date of calendar %s %s at anchor %s", calendarDate, start, anchor)
                .isEqualTo(businessDate);
        Timeslot timeslot = new Timeslot();
        timeslot.setTenantId(tenantId);
        timeslot.setDeskId(deskId);
        timeslot.setScheduleId(null);
        timeslot.setDate(calendarDate);
        timeslot.setStartTime(start);
        timeslot.setEndTime(end);
        timeslot.setBusinessDate(businessDate);
        timeslot = timeslotRepository.save(timeslot);
        return saveDemandForTimeslot(tenantId, deskId, spec, timeslot, requiredFTEs, null);
    }

    private StaffingRequirement saveDemandForTimeslot(long tenantId, UUID deskId, Specialization specialization,
                                                        Timeslot timeslot, int requiredFTEs, UUID scheduleId) {
        StaffingRequirement requirement = new StaffingRequirement();
        requirement.setTenantId(tenantId);
        requirement.setDeskId(deskId);
        requirement.setScheduleId(scheduleId);
        requirement.setTimeslot(timeslot);
        requirement.setSpecialization(specialization);
        requirement.setRequiredFTEs(requiredFTEs);
        return staffingRequirementRepository.save(requirement);
    }

    private StaffingRequirement saveDemand(long tenantId, UUID deskId, Specialization specialization,
                                            LocalDate date, LocalTime start, LocalTime end,
                                            int requiredFTEs, UUID scheduleId) {
        Timeslot timeslot = saveTimeslot(tenantId, deskId, date, start, end, scheduleId);
        return saveDemandForTimeslot(tenantId, deskId, specialization, timeslot, requiredFTEs, scheduleId);
    }

    private Agent saveAgent(long tenantId, UUID deskId, String bamboohrId) {
        Agent agent = new Agent();
        agent.setTenantId(tenantId);
        agent.setDeskId(deskId);
        agent.setBamboohrId(bamboohrId);
        agent.setName("Agent " + bamboohrId);
        return agentRepository.save(agent);
    }

    private void saveAgentDayHours(long tenantId, Agent agent, DayOfWeek dayOfWeek, BigDecimal hours) {
        AgentDayHours agentDayHours = new AgentDayHours();
        agentDayHours.setTenantId(tenantId);
        agentDayHours.setAgent(agent);
        agentDayHours.setDayOfWeek(dayOfWeek);
        agentDayHours.setHours(hours);
        agentDayHoursRepository.save(agentDayHours);
    }
}
