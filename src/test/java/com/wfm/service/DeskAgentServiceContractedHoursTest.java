package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies DeskAgentService.setContractedHours fans the new value out to all 7
 * agent_day_hours rows (D-10 set side), so the operator hours-edit endpoint stays
 * durable now that the solver reads per-day rows instead of the scalar.
 */
@DataJpaTest
@Import(DeskAgentService.class)
@ActiveProfiles("test")
class DeskAgentServiceContractedHoursTest {

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
    void setContractedHours_writesSevenPerDayRows_normalizedToScaleTwo() {
        deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("6"));

        List<AgentDayHours> rows = agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId());

        assertThat(rows).hasSize(7);
        assertThat(rows.stream().map(AgentDayHours::getDayOfWeek))
                .containsExactlyInAnyOrder(DayOfWeek.values());
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getHours()).isEqualByComparingTo(new BigDecimal("6.00"));
            assertThat(row.getTenantId()).isEqualTo(TENANT_ID);
        });
    }

    @Test
    void setContractedHours_calledTwice_replacesRatherThanAppends() {
        deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("6"));
        deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("7.5"));

        List<AgentDayHours> rows = agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId());

        assertThat(rows).hasSize(7);
        assertThat(rows).allSatisfy(row ->
                assertThat(row.getHours()).isEqualByComparingTo(new BigDecimal("7.50")));
    }

    @Test
    void setContractedHours_null_revertsToDefault_leavesZeroRows_withoutError() {
        // Seed 7 rows first, then a null "revert to desk default" must clear them and
        // NOT crash on the NOT NULL agent_day_hours.hours column (regression: CR-01).
        deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("6"));
        assertThat(agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).hasSize(7);

        assertThatCode(() ->
                deskAgentService.setContractedHours(desk.getId(), agent.getId(), null))
                .doesNotThrowAnyException();

        assertThat(agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getContractedHoursPerDay()).isNull();
    }

    @Test
    void setContractedHours_negative_isRejected() {
        assertThatThrownBy(() ->
                deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    @Test
    void setContractedHours_returnsResponseWithUpdatedScalar() {
        DeskAgentResponse response = deskAgentService.setContractedHours(
                desk.getId(), agent.getId(), new BigDecimal("6"));

        assertThat(response.contractedHoursPerDay()).isEqualByComparingTo(new BigDecimal("6.00"));
    }
}
