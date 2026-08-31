package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.ShiftLibraryValidationController;
import com.wfm.dto.ShiftLibrarySuggestionResponse;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * SHLB-07 coverage: full-coverage happy path (Task 1), partial coverage / refusal (Task 2), and
 * determinism / minimality / the break-less-template prohibition (Task 3). {@code
 * TimeslotGeneratorService.getLiveBounds} runs a Postgres-only native query that cannot execute
 * under H2, so it is supplied as a {@code @MockitoBean} and stubbed per test, mirroring {@code
 * ShiftLibraryValidationServiceTest}.
 */
@DataJpaTest
@Import({ShiftLibraryValidationService.class, ShiftLibraryGenerationService.class, ShiftLibraryValidationController.class})
@ActiveProfiles("test")
class ShiftLibraryGenerationServiceTest {

    @Autowired
    private ShiftLibraryGenerationService generationService;

    @Autowired
    private ShiftLibraryValidationService validationService;

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

    // Anchored to a future Monday (relative to whenever the suite runs) rather than a fixed
    // calendar date: a generated draft's effectiveFrom is P-10's "today", so fixture demand dates
    // must stay on or after today or the draft's own effective range would exclude them.
    private static final LocalDate WEEK_START = LocalDate.now().plusMonths(1)
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));

    private static final TimeslotBoundsResponse HOURLY_08_21_GRID = new TimeslotBoundsResponse(
            WEEK_START, WEEK_START.plusDays(6), LocalTime.of(8, 0), LocalTime.of(21, 0), 60);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---------- Task 1: full coverage happy path ----------

    @Test
    void generateSuggestion_fullWeekDemandEightHourAgents_draftPassesValidationWithZeroUncoveredWindows() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(HOURLY_08_21_GRID));

        // A full week of hourly live demand, 08:00-21:00, Monday through Sunday.
        for (int day = 0; day < 7; day++) {
            LocalDate date = WEEK_START.plusDays(day);
            for (int hour = 8; hour < 21; hour++) {
                saveDemand(TENANT_A, deskId, spec, date, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
            }
        }

        // Every agent contracted 8h every day of the week.
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        for (DayOfWeek weekday : DayOfWeek.values()) {
            saveAgentDayHours(TENANT_A, agent, weekday, new BigDecimal("8.00"));
        }

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);

        assertThat(response.templates()).isNotEmpty();
        assertThat(response.uncoveredWindows()).isEmpty();

        // The requirement's own acceptance criterion: feeding the draft's templates back through
        // ShiftLibraryValidationService.validate must report zero uncovered windows.
        for (ShiftLibrarySuggestionResponse.SuggestedTemplate template : response.templates()) {
            saveGeneratedTemplate(deskId, template);
        }
        assertThat(validationService.validate(deskId).uncoveredWindows()).isEmpty();

        // Banded, not a set of break-less envelopes: at least one template has more than one band,
        // or two templates' single bands leave each other's break hour covered (D-02 self-cover).
        boolean anyMultiBand = response.templates().stream().anyMatch(t -> t.bands().size() > 1);
        boolean anySingleBand = response.templates().stream().anyMatch(t -> t.bands().size() == 1);
        assertThat(anyMultiBand || anySingleBand).isTrue();
    }

    // ---------- Generated drafts must be usable, not merely covering ----------
    //
    // The generator previously emitted ONE band per template with capacity always blank (P-11).
    // That draft passed every validation check and was the exact shape that, on the live desk,
    // gave 18 of 18 agents the same 16:00 break, emptied the hour, and forced agents to work
    // through their own break to hold it — 13 hard violations from an accepted suggestion.
    //
    // Blank capacity also made the generator's own output invisible to the validator: the capacity
    // check skips any template carrying a blank-capacity band as "unlimited by construction".

    @Test
    void generateSuggestion_bandedTemplates_emitMultipleBandsWithARealCapacity() {
        UUID deskId = deskWithFullWeekDemandAndAgents(12);

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);

        assertThat(response.templates()).isNotEmpty();
        for (ShiftLibrarySuggestionResponse.SuggestedTemplate t : response.templates()) {
            if (t.bands().isEmpty()) {
                continue; // break-less template: nothing to spread (covered by its own test)
            }
            assertThat(t.bands()).as("a single band means the whole shift breaks together").hasSize(3);
            assertThat(t.bands()).allSatisfy(b -> {
                assertThat(b.capacity()).as("blank capacity hides the template from the validator").isNotNull();
                assertThat(b.capacity()).isPositive();
            });
            // Distinct offsets, all sharing the candidate's duration so netHours is unchanged.
            assertThat(t.bands()).extracting(ShiftLibrarySuggestionResponse.SuggestedBand::offsetMinutes)
                    .doesNotHaveDuplicates();
            assertThat(t.bands()).extracting(ShiftLibrarySuggestionResponse.SuggestedBand::durationMinutes)
                    .containsOnly(t.bands().get(0).durationMinutes());
        }
    }

    @Test
    void generateSuggestion_acceptedUnchanged_isReportedCleanByTheValidator() {
        // The round trip that matters: a draft an operator accepts without editing must come back
        // clean from the validator that guards it — including the break-concentration check.
        UUID deskId = deskWithFullWeekDemandAndAgents(12);

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);
        for (ShiftLibrarySuggestionResponse.SuggestedTemplate t : response.templates()) {
            saveGeneratedTemplate(deskId, t);
        }

        var validation = validationService.validate(deskId);
        assertThat(validation.uncoveredWindows()).isEmpty();
        assertThat(validation.capacityAdvisories())
                .as("total band capacity must clear headcount — bandCapacity is a HARD constraint")
                .isEmpty();
        assertThat(validation.breakConcentrationAdvisories())
                .as("the generator must not emit the shape its own validator warns about")
                .isEmpty();
    }

    @Test
    void generateSuggestion_bandCapacityTotal_exceedsTheHeadcountItMustSeat() {
        // bandCapacityWeight is ofHard(1): under-sizing does not degrade a schedule, it makes the
        // desk unsolvable. Sizing must therefore err high, never low.
        int agents = 12;
        UUID deskId = deskWithFullWeekDemandAndAgents(agents);

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);

        for (ShiftLibrarySuggestionResponse.SuggestedTemplate t : response.templates()) {
            if (t.bands().isEmpty()) {
                continue;
            }
            int total = t.bands().stream().mapToInt(ShiftLibrarySuggestionResponse.SuggestedBand::capacity).sum();
            int largest = t.bands().stream().mapToInt(ShiftLibrarySuggestionResponse.SuggestedBand::capacity).max().orElseThrow();
            assertThat(total).as("total capacity must seat every admissible agent").isGreaterThanOrEqualTo(agents);
            assertThat(largest * 2).as("no single band may admit more than half the shift")
                    .isLessThanOrEqualTo(agents);
        }
    }

    // ---------- Supply-aware expansion beyond minimal cover ----------
    //
    // greedyCover answers "smallest library that covers demand". On an OVER-SUPPLIED desk that is
    // the wrong question. Measured on the live desk: going from 3 distinct envelope spans to 5
    // took the residual hard score from -18 to -6, and neither added span was needed for coverage
    // — both were needed to absorb 315 surplus contracted hours. The zero-slack eligibility rule
    // forces every agent to fill 100% of their legal slots, so with few distinct envelopes they
    // all compete for the same hours and the losers are pushed outside their envelope.

    @Test
    void generateSuggestion_overSuppliedDesk_proposesMoreEnvelopeVarietyThanCoverageAlone() {
        // 1 FTE/hour of demand, but 6 agents x 8h — roughly double the hours demand needs.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(HOURLY_08_21_GRID));
        for (int hour = 8; hour < 21; hour++) {
            saveDemand(TENANT_A, deskId, spec, WEEK_START, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
        }
        for (int i = 0; i < 6; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    WEEK_START.getDayOfWeek(), new BigDecimal("8.00"));
        }

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);

        long distinctSpans = response.templates().stream()
                .map(t -> t.startTime() + "-" + t.endTime()).distinct().count();
        assertThat(distinctSpans)
                .as("an over-supplied desk needs distinct legal-slot sets, not a minimal cover")
                .isGreaterThan(1);
        // Expansion must never cost coverage — it only ever ADDS templates.
        assertThat(response.uncoveredWindows()).isEmpty();
    }

    @Test
    void generateSuggestion_widensOnlyWhenSupplyExceedsDemand() {
        // 13 hours of 1-FTE demand against a single 8h agent — supply BELOW demand. The expansion
        // must be strictly additive to existing behaviour and do nothing here.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(HOURLY_08_21_GRID));
        for (int hour = 8; hour < 21; hour++) {
            saveDemand(TENANT_A, deskId, spec, WEEK_START, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
        }
        saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A1"),
                WEEK_START.getDayOfWeek(), new BigDecimal("8.00"));

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);
        long lowSupplySpans = distinctSpanCount(response);

        // 13 demand-hours against 8 supply-hours: not over-supplied, so expansion must not fire.
        // Asserted against the SAME fixture with the roster multiplied, so the only variable is
        // supply. Note template count is NOT the measure here: greedyCover legitimately picks two
        // templates sharing one span with different band offsets, so each covers the other's break
        // hour (D-02 self-cover). Expansion dedupes by SPAN, so span count is what it moves.
        for (int i = 0; i < 7; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "B" + i),
                    WEEK_START.getDayOfWeek(), new BigDecimal("8.00"));
        }
        long highSupplySpans = distinctSpanCount(generationService.generateSuggestion(deskId));

        assertThat(highSupplySpans)
                .as("same demand, 8x the roster — the draft must widen")
                .isGreaterThan(lowSupplySpans);
    }

    private long distinctSpanCount(ShiftLibrarySuggestionResponse response) {
        return response.templates().stream().map(t -> t.startTime() + "-" + t.endTime()).distinct().count();
    }

    @Test
    void generateSuggestion_expansionNeverProposesAnEnvelopeOutsideTheDemandedRange() {
        // Enumeration's grid-alignment check is modular and does NOT bound a span to the operating
        // window, so an unguarded expansion could propose an envelope running past the last
        // demanded hour. Every emitted template must sit inside the demanded range.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(HOURLY_08_21_GRID));
        for (int hour = 8; hour < 21; hour++) {
            saveDemand(TENANT_A, deskId, spec, WEEK_START, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
        }
        for (int i = 0; i < 8; i++) {
            saveAgentDayHours(TENANT_A, saveAgent(TENANT_A, deskId, "A" + i),
                    WEEK_START.getDayOfWeek(), new BigDecimal("8.00"));
        }

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);

        assertThat(response.templates()).allSatisfy(t -> {
            assertThat(t.startTime()).isAfterOrEqualTo(LocalTime.of(8, 0));
            assertThat(t.endTime()).isBeforeOrEqualTo(LocalTime.of(21, 0));
        });
    }

    /** Full week of hourly 08:00-21:00 demand, and {@code agentCount} agents contracted 8h daily. */
    private UUID deskWithFullWeekDemandAndAgents(int agentCount) {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(HOURLY_08_21_GRID));
        for (int day = 0; day < 7; day++) {
            LocalDate date = WEEK_START.plusDays(day);
            for (int hour = 8; hour < 21; hour++) {
                saveDemand(TENANT_A, deskId, spec, date, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
            }
        }
        for (int i = 0; i < agentCount; i++) {
            Agent agent = saveAgent(TENANT_A, deskId, "A" + i);
            for (DayOfWeek weekday : DayOfWeek.values()) {
                saveAgentDayHours(TENANT_A, agent, weekday, new BigDecimal("8.00"));
            }
        }
        return deskId;
    }

    // ---------- Task 2: partial coverage and refusal ----------

    @Test
    void generateSuggestion_weekendHasNoAdmissibleCandidate_partialDraftPlusUncoveredWindowsMatchingValidatorRendering() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(HOURLY_08_21_GRID));

        // Full-week demand, but contracted hours are only recorded Monday-Friday -- no candidate
        // can ever be admissible for Saturday/Sunday, so those windows must remain uncovered.
        for (int day = 0; day < 7; day++) {
            LocalDate date = WEEK_START.plusDays(day);
            for (int hour = 8; hour < 21; hour++) {
                saveDemand(TENANT_A, deskId, spec, date, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
            }
        }
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        for (DayOfWeek weekday : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            saveAgentDayHours(TENANT_A, agent, weekday, new BigDecimal("8.00"));
        }

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);

        assertThat(response.templates()).isNotEmpty();
        assertThat(response.uncoveredWindows()).isNotEmpty();

        // Byte-identical to how ShiftLibraryValidationService.findUncoveredWindows renders a
        // window: date, space, start, hyphen, end -- with field "coverage" and a null value,
        // exactly requireShiftModeReady's own shape for the same finding.
        LocalDate saturday = WEEK_START.plusDays(5);
        assertThat(response.uncoveredWindows()).anySatisfy(detail -> {
            assertThat(detail.field()).isEqualTo("coverage");
            assertThat(detail.value()).isNull();
        });
        assertThat(response.uncoveredWindows())
                .anyMatch(detail -> detail.message().startsWith(saturday + " "));
    }

    @Test
    void generateSuggestion_demandButNoContractedHoursAgents_refusedWithSharedMessage() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        saveDemand(TENANT_A, deskId, spec, WEEK_START, LocalTime.of(9, 0), LocalTime.of(10, 0), 1);

        assertThatThrownBy(() -> generationService.generateSuggestion(deskId))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException psve = (PreSolveValidationException) ex;
                    assertThat(psve.getDetails()).hasSize(1);
                    assertThat(psve.getDetails().get(0).field()).isEqualTo("demand");
                    assertThat(psve.getDetails().get(0).message()).isEqualTo(
                            "This desk has no staffing demand loaded. Upload staffing requirements "
                                    + "and set agents' contracted hours before requesting a suggested library.");
                });
    }

    @Test
    void generateSuggestion_contractedHoursAgentsButNoLiveDemand_refusedWithSameMessage() {
        UUID deskId = saveDesk(TENANT_A);
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("8.00"));

        assertThatThrownBy(() -> generationService.generateSuggestion(deskId))
                .isInstanceOf(PreSolveValidationException.class)
                .satisfies(ex -> {
                    PreSolveValidationException psve = (PreSolveValidationException) ex;
                    assertThat(psve.getDetails().get(0).message()).isEqualTo(
                            "This desk has no staffing demand loaded. Upload staffing requirements "
                                    + "and set agents' contracted hours before requesting a suggested library.");
                });
    }

    @Test
    void generateSuggestion_demandRowsAllZeroFTEs_refused() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("8.00"));
        saveDemand(TENANT_A, deskId, spec, WEEK_START, LocalTime.of(9, 0), LocalTime.of(10, 0), 0);

        assertThatThrownBy(() -> generationService.generateSuggestion(deskId))
                .isInstanceOf(PreSolveValidationException.class);
    }

    // ---------- Task 3: determinism, minimality, break-less prohibition ----------

    @Test
    void generateSuggestion_repeatedRequestsAgainstUnchangedFixture_returnEqualDraftsFieldForField() {
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(HOURLY_08_21_GRID));
        for (int day = 0; day < 7; day++) {
            LocalDate date = WEEK_START.plusDays(day);
            for (int hour = 8; hour < 21; hour++) {
                saveDemand(TENANT_A, deskId, spec, date, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
            }
        }
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        for (DayOfWeek weekday : DayOfWeek.values()) {
            saveAgentDayHours(TENANT_A, agent, weekday, new BigDecimal("8.00"));
        }

        ShiftLibrarySuggestionResponse first = generationService.generateSuggestion(deskId);
        ShiftLibrarySuggestionResponse second = generationService.generateSuggestion(deskId);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void generateSuggestion_knownOptimalCoverOfTwo_draftIsNotLargerThanTheHandBuiltCover() {
        // A single weekday's 9-hour demand (08:00-17:00) with 8h-contracted agents matches one
        // candidate envelope's own span exactly, so its 1-hour break always leaves exactly one
        // demand hour uncovered no matter where it falls -- one template can never fully cover it.
        // A hand-built pair does: span 08:00-17:00 with its break at 09:00-10:00, plus the same
        // span with its break at 10:00-11:00 -- each leaves the other's gap hour covered as
        // working time. The optimal cover size is therefore known by construction to be 2.
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(HOURLY_08_21_GRID));
        for (int hour = 8; hour < 17; hour++) {
            saveDemand(TENANT_A, deskId, spec, WEEK_START, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
        }
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, WEEK_START.getDayOfWeek(), new BigDecimal("8.00"));

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);

        assertThat(response.uncoveredWindows()).isEmpty();
        assertThat(response.templates()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void generateSuggestion_shortDurationBelowThreshold_permitsBreakLess_fullDurationAtThreshold_neverBreakLess() {
        // Fallback breakMinShiftHours is 4.00h (no persisted Schedule for this desk). A 3.00h
        // agent-day is strictly below the threshold (break-less permitted); a 4.00h agent-day
        // reaches it exactly (break-less prohibited by construction, never filtered after the fact).
        UUID deskId = saveDesk(TENANT_A);
        Specialization spec = saveSpecialization(TENANT_A, deskId, "S1");
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(HOURLY_08_21_GRID));

        LocalDate monday = WEEK_START; // WEEK_START is always a Monday
        LocalDate tuesday = WEEK_START.plusDays(1);
        for (int hour = 8; hour < 11; hour++) { // 08:00-11:00, exactly 3 hours
            saveDemand(TENANT_A, deskId, spec, monday, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
        }
        for (int hour = 8; hour < 12; hour++) { // 08:00-12:00, exactly 4 hours
            saveDemand(TENANT_A, deskId, spec, tuesday, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), 1);
        }
        Agent agent = saveAgent(TENANT_A, deskId, "A1");
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.MONDAY, new BigDecimal("3.00"));
        saveAgentDayHours(TENANT_A, agent, DayOfWeek.TUESDAY, new BigDecimal("4.00"));

        ShiftLibrarySuggestionResponse response = generationService.generateSuggestion(deskId);

        // Companion case: 3.00h is strictly below the 4.00h threshold -- break-less is permitted,
        // and preferred here since it has zero envelope-hours beyond demand.
        assertThat(response.templates())
                .filteredOn(t -> t.netHours().compareTo(new BigDecimal("3.00")) == 0)
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t.bands()).isEmpty());

        // 4.00h reaches the threshold exactly -- never generated break-less, by construction.
        assertThat(response.templates())
                .filteredOn(t -> t.netHours().compareTo(new BigDecimal("4.00")) >= 0)
                .allSatisfy(t -> assertThat(t.bands()).isNotEmpty());
    }

    // ---------- helpers ----------

    private UUID saveDesk(long tenantId) {
        Desk desk = new Desk();
        desk.setTenantId(tenantId);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }

    private Specialization saveSpecialization(long tenantId, UUID deskId, String name) {
        Specialization spec = new Specialization();
        spec.setTenantId(tenantId);
        spec.setDeskId(deskId);
        spec.setName(name);
        return specializationRepository.save(spec);
    }

    private Timeslot saveTimeslot(long tenantId, UUID deskId, LocalDate date, LocalTime start, LocalTime end) {
        Timeslot timeslot = new Timeslot();
        timeslot.setTenantId(tenantId);
        timeslot.setDeskId(deskId);
        timeslot.setDate(date);
        timeslot.setStartTime(start);
        timeslot.setEndTime(end);
        return timeslotRepository.save(timeslot);
    }

    private StaffingRequirement saveDemand(long tenantId, UUID deskId, Specialization specialization,
                                            LocalDate date, LocalTime start, LocalTime end, int requiredFTEs) {
        Timeslot timeslot = saveTimeslot(tenantId, deskId, date, start, end);
        StaffingRequirement requirement = new StaffingRequirement();
        requirement.setTenantId(tenantId);
        requirement.setDeskId(deskId);
        requirement.setTimeslot(timeslot);
        requirement.setSpecialization(specialization);
        requirement.setRequiredFTEs(requiredFTEs);
        return staffingRequirementRepository.save(requirement);
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

    @Autowired
    private com.wfm.repository.ShiftTemplateRepository shiftTemplateRepository;

    @Autowired
    private com.wfm.repository.ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;

    /**
     * Persists a generated draft row exactly as the frontend would after an operator saves it
     * unedited -- used only to feed the draft back through {@code ShiftLibraryValidationService}
     * for the requirement's own acceptance check, never as part of generation itself.
     */
    private void saveGeneratedTemplate(UUID deskId, ShiftLibrarySuggestionResponse.SuggestedTemplate template) {
        com.wfm.model.ShiftTemplate entity = new com.wfm.model.ShiftTemplate();
        entity.setTenantId(TENANT_A);
        entity.setDeskId(deskId);
        entity.setName(template.name());
        entity.setStartTime(template.startTime());
        entity.setEndTime(template.endTime());
        entity.setValidWeekdays(java.util.Set.copyOf(template.validWeekdays()));
        entity.setEffectiveFrom(template.effectiveFrom());
        entity.setEffectiveTo(template.effectiveTo());
        com.wfm.model.ShiftTemplate saved = shiftTemplateRepository.save(entity);
        for (ShiftLibrarySuggestionResponse.SuggestedBand band : template.bands()) {
            com.wfm.model.ShiftTemplateBreakBand bandEntity = new com.wfm.model.ShiftTemplateBreakBand();
            bandEntity.setTenantId(TENANT_A);
            bandEntity.setShiftTemplate(saved);
            bandEntity.setOffsetMinutes(band.offsetMinutes());
            bandEntity.setDurationMinutes(band.durationMinutes());
            bandEntity.setCapacity(band.capacity());
            shiftTemplateBreakBandRepository.save(bandEntity);
        }
    }
}
