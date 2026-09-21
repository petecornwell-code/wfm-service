package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.dto.TimeslotBoundsResponse;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Proves {@code shift-library.suggestion.max-candidates} actually reaches
 * {@link ShiftLibraryGenerationService}. Without this, the property could be silently ignored —
 * the field would keep its initialiser and the knob would look configurable while doing nothing.
 *
 * <p>Set to 1 rather than a large number deliberately: a cap of 1 is exceeded by any desk with
 * real demand, so the refusal fires on a fixture that the default cap of 200 accepts. The
 * assertion therefore fails if the property is ignored, rather than passing vacuously.
 */
@DataJpaTest
@Import({ShiftLibraryValidationService.class, ShiftLibraryGenerationService.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "shift-library.suggestion.max-candidates=1")
class ShiftLibraryGenerationCapConfigTest {

    @Autowired private ShiftLibraryGenerationService generationService;
    @Autowired private StaffingRequirementRepository staffingRequirementRepository;
    @Autowired private TimeslotRepository timeslotRepository;
    @Autowired private SpecializationRepository specializationRepository;
    @Autowired private AgentDayHoursRepository agentDayHoursRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private DeskRepository deskRepository;

    @MockitoBean private TimeslotGeneratorService timeslotGeneratorService;

    private static final long TENANT_A = 1L;
    private static final LocalDate WEEK_START = LocalDate.now().plusMonths(1)
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void configuredCapOfOne_isHonoured_andNamedInTheRefusal() {
        UUID deskId = saveDesk();
        Specialization spec = saveSpecialization(deskId);
        when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
                new TimeslotBoundsResponse(WEEK_START, WEEK_START.plusDays(6),
                        LocalTime.of(8, 0), LocalTime.of(21, 0), 60)));

        for (int hour = 8; hour < 21; hour++) {
            saveDemand(deskId, spec, WEEK_START, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0));
        }
        Agent agent = saveAgent(deskId);
        for (DayOfWeek weekday : DayOfWeek.values()) {
            saveAgentDayHours(agent, weekday, new BigDecimal("8.00"));
        }

        // The message must quote the CONFIGURED cap, not the 200 default — that is the assertion
        // that distinguishes "property wired" from "property silently ignored".
        assertThatThrownBy(() -> generationService.generateSuggestion(deskId))
                .isInstanceOf(PreSolveValidationException.class)
                .hasMessageContaining("exceeding the cap of 1")
                .hasMessageNotContaining("cap of 200");
    }

    private UUID saveDesk() {
        Desk desk = new Desk();
        desk.setTenantId(TENANT_A);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }

    private Specialization saveSpecialization(UUID deskId) {
        Specialization spec = new Specialization();
        spec.setTenantId(TENANT_A);
        spec.setDeskId(deskId);
        spec.setName("S1");
        return specializationRepository.save(spec);
    }

    private void saveDemand(UUID deskId, Specialization specialization,
                            LocalDate date, LocalTime start, LocalTime end) {
        Timeslot timeslot = new Timeslot();
        timeslot.setTenantId(TENANT_A);
        timeslot.setDeskId(deskId);
        timeslot.setDate(date);
        timeslot.setStartTime(start);
        timeslot.setEndTime(end);
        timeslot = timeslotRepository.save(timeslot);

        StaffingRequirement requirement = new StaffingRequirement();
        requirement.setTenantId(TENANT_A);
        requirement.setDeskId(deskId);
        requirement.setTimeslot(timeslot);
        requirement.setSpecialization(specialization);
        requirement.setRequiredFTEs(1);
        staffingRequirementRepository.save(requirement);
    }

    private Agent saveAgent(UUID deskId) {
        Agent agent = new Agent();
        agent.setTenantId(TENANT_A);
        agent.setDeskId(deskId);
        agent.setBamboohrId("A1");
        agent.setName("Agent A1");
        return agentRepository.save(agent);
    }

    private void saveAgentDayHours(Agent agent, DayOfWeek dayOfWeek, BigDecimal hours) {
        AgentDayHours agentDayHours = new AgentDayHours();
        agentDayHours.setTenantId(TENANT_A);
        agentDayHours.setAgent(agent);
        agentDayHours.setDayOfWeek(dayOfWeek);
        agentDayHours.setHours(hours);
        agentDayHoursRepository.save(agentDayHours);
    }
}
