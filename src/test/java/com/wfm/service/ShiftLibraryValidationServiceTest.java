package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.ShiftLibraryValidationController;
import com.wfm.dto.ShiftLibraryValidationResponse;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
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
@Import({ShiftLibraryValidationService.class, ShiftLibraryValidationController.class})
@ActiveProfiles("test")
class ShiftLibraryValidationServiceTest {

    @Autowired
    private ShiftLibraryValidationService service;

    @Autowired
    private ShiftLibraryValidationController controller;

    @Autowired
    private ShiftTemplateRepository shiftTemplateRepository;

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

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
        when(timeslotGeneratorService.getLiveBounds(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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

    // ---------- Hours match (D-06/D-07) ----------

    @Test
    void validate_netDurationMatchesAgentHours_noAdvisory() {
        UUID deskId = saveDesk(TENANT_A);
        ShiftTemplate template = saveTemplate(deskId, "S1", LocalTime.of(8, 0), LocalTime.of(17, 0), 240, 60,
                Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null);
        assertThat(template.getNetHours()).isEqualByComparingTo("8.00");
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

    // ---------- helpers ----------

    private UUID saveDesk(long tenantId) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }

    private ShiftTemplate saveTemplate(UUID deskId, String name, LocalTime start, LocalTime end,
                                        int breakOffsetMinutes, int breakDurationMinutes,
                                        Set<DayOfWeek> weekdays, LocalDate effectiveFrom, LocalDate effectiveTo) {
        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(TENANT_A);
        template.setDeskId(deskId);
        template.setName(name);
        template.setStartTime(start);
        template.setEndTime(end);
        template.setBreakOffsetMinutes(breakOffsetMinutes);
        template.setBreakDurationMinutes(breakDurationMinutes);
        template.setValidWeekdays(weekdays);
        template.setEffectiveFrom(effectiveFrom);
        template.setEffectiveTo(effectiveTo);
        return shiftTemplateRepository.save(template);
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
        return timeslotRepository.save(timeslot);
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
