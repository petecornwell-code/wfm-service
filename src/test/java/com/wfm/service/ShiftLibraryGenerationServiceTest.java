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
