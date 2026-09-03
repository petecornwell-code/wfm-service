package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.DayOffType;
import com.wfm.model.Desk;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression suite for DeskAgentService.setDayHours (D-05): a single-weekday edit must touch
 * exactly one agent_day_hours row and leave the other six byte-identical — the structural
 * property that closes audit finding I-3. Complements DeskAgentServiceContractedHoursTest,
 * which covers the surviving seven-row bulk fan-out (D-07).
 */
@DataJpaTest
@Import({DeskAgentService.class, UsualShiftResolutionService.class})
@ActiveProfiles("test")
class DeskAgentServiceDayHoursTest {

    @Autowired
    private DeskAgentService deskAgentService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private AgentDayHoursRepository agentDayHoursRepository;

    private static final long TENANT_ID = 1L;

    private Desk desk;
    private Agent agent;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        desk = new Desk();
        desk.setTenantId(TENANT_ID);
        desk.setName("Support Desk");
        desk = deskRepository.save(desk);

        agent = new Agent();
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId("B100");
        agent.setName("Jane Doe");
        agent.setDeskId(desk.getId());
        agent = agentRepository.save(agent);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setDayHours_numeric_touchesExactlyOneRow() {
        deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("8"));
        Map<DayOfWeek, AgentDayHours> before = agentDayHoursRepository
                .findByTenantIdAndAgent_Id(TENANT_ID, agent.getId()).stream()
                .collect(Collectors.toMap(AgentDayHours::getDayOfWeek, h -> h));
        Map<DayOfWeek, UUID> beforeIds = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, BigDecimal> beforeHours = new EnumMap<>(DayOfWeek.class);
        before.forEach((day, row) -> {
            beforeIds.put(day, row.getId());
            beforeHours.put(day, row.getHours());
        });

        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.WEDNESDAY,
                new BigDecimal("4.5"), null, false);

        List<AgentDayHours> after = agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId());
        assertThat(after).hasSize(7);
        for (AgentDayHours row : after) {
            if (row.getDayOfWeek() == DayOfWeek.WEDNESDAY) {
                assertThat(row.getHours()).isEqualByComparingTo(new BigDecimal("4.50"));
                assertThat(row.getDayOffType()).isNull();
            } else {
                assertThat(row.getId()).isEqualTo(beforeIds.get(row.getDayOfWeek()));
                assertThat(row.getHours()).isEqualByComparingTo(beforeHours.get(row.getDayOfWeek()));
                assertThat(row.getDayOffType()).isNull();
            }
        }
    }

    @Test
    void setDayHours_createsRowWhenNoneExists() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.FRIDAY,
                new BigDecimal("6"), null, false);

        List<AgentDayHours> rows = agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(rows.get(0).getHours()).isEqualByComparingTo(new BigDecimal("6.00"));
    }

    @Test
    void setDayHours_mandatory_storesZeroWithLabel() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.SATURDAY,
                null, DayOffType.MANDATORY, false);

        AgentDayHours row = agentDayHoursRepository
                .findByAgent_IdAndDayOfWeek(agent.getId(), DayOfWeek.SATURDAY).orElseThrow();
        assertThat(row.getHours()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(row.getDayOffType()).isEqualTo(DayOffType.MANDATORY);
    }

    @Test
    void setDayHours_pto_storesZeroWithLabel() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.SUNDAY,
                null, DayOffType.PTO, false);

        AgentDayHours row = agentDayHoursRepository
                .findByAgent_IdAndDayOfWeek(agent.getId(), DayOfWeek.SUNDAY).orElseThrow();
        assertThat(row.getHours()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(row.getDayOffType()).isEqualTo(DayOffType.PTO);
    }

    @Test
    void setDayHours_notSet_deletesOnlyThatRow() {
        deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("8"));

        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.TUESDAY, null, null, true);

        List<AgentDayHours> rows = agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId());
        assertThat(rows).hasSize(6);
        assertThat(rows).noneMatch(r -> r.getDayOfWeek() == DayOfWeek.TUESDAY);
    }

    @Test
    void setDayHours_notSet_onAbsentRow_isANoOp() {
        assertThatCode(() ->
                deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, null, null, true))
                .doesNotThrowAnyException();

        assertThat(agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    @Test
    void setDayHours_negative_isRejectedAndPersistsNothing() {
        assertThatThrownBy(() ->
                deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY,
                        new BigDecimal("-1"), null, false))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    @Test
    void setDayHours_above24_isRejectedAndPersistsNothing() {
        assertThatThrownBy(() ->
                deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY,
                        new BigDecimal("24.25"), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();

        assertThatCode(() ->
                deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY,
                        new BigDecimal("24"), null, false))
                .doesNotThrowAnyException();

        AgentDayHours row = agentDayHoursRepository
                .findByAgent_IdAndDayOfWeek(agent.getId(), DayOfWeek.MONDAY).orElseThrow();
        assertThat(row.getHours()).isEqualByComparingTo(new BigDecimal("24.00"));
    }

    @Test
    void setDayHours_normalizesToScaleTwo() {
        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY,
                new BigDecimal("7.567"), null, false);

        AgentDayHours row = agentDayHoursRepository
                .findByAgent_IdAndDayOfWeek(agent.getId(), DayOfWeek.MONDAY).orElseThrow();
        assertThat(row.getHours()).isEqualByComparingTo(new BigDecimal("7.57"));
    }

    @Test
    void setDayHours_noValueAndNoLabelAndNoClear_isRejected() {
        assertThatThrownBy(() ->
                deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY, null, null, false))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    @Test
    void setDayHours_foreignTenantAgent_throwsEntityNotFound() {
        TenantContext.setTenantId(2L);

        assertThatThrownBy(() ->
                deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY,
                        new BigDecimal("5"), null, false))
                .isInstanceOf(EntityNotFoundException.class);

        TenantContext.setTenantId(TENANT_ID);
        assertThat(agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    @Test
    void setDayHours_responseReflectsTheJustWrittenRow() {
        DeskAgentResponse response = deskAgentService.setDayHours(
                desk.getId(), agent.getId(), DayOfWeek.THURSDAY, new BigDecimal("3"), null, false);

        DeskAgentResponse.DayHoursEntry thursday = response.dayHours().get(DayOfWeek.THURSDAY);
        assertThat(thursday.hasRow()).isTrue();
        assertThat(thursday.hours()).isEqualByComparingTo(new BigDecimal("3.00"));
    }

    @Test
    void setDayHours_leavesScalarUntouched() {
        agent.setContractedHoursPerDay(new BigDecimal("5.00"));
        agent = agentRepository.save(agent);

        deskAgentService.setDayHours(desk.getId(), agent.getId(), DayOfWeek.MONDAY,
                new BigDecimal("3"), null, false);

        Agent reloaded = agentRepository.findById(agent.getId()).orElseThrow();
        assertThat(reloaded.getContractedHoursPerDay()).isEqualByComparingTo(new BigDecimal("5.00"));
    }
}
