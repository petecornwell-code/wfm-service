package com.wfm.service;

import com.wfm.config.TenantContext;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Proves DeskAgentService.setContractedHours' transactional rollback with a genuine mid-loop
 * failure, replacing annotation-reflection as the evidence (P-16). This closes both
 * behavior_unverified_items in 13-VERIFICATION.md: the reflection-only
 * setContractedHours_isTransactional test in DeskAgentServiceContractedHoursTest proves the
 * annotation is present; this test proves a failure that occurs after the delete-and-recreate
 * loop has started genuinely leaves nothing behind.
 */
@DataJpaTest
@Import({DeskAgentService.class, UsualShiftResolutionService.class, UsualShiftService.class})
@ActiveProfiles("test")
class DeskAgentServiceBulkRollbackTest {

    @Autowired
    private DeskAgentService deskAgentService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private DeskRepository deskRepository;

    @MockitoSpyBean
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
        // With the test transaction suppressed for the rollback test below, this data is
        // genuinely committed rather than rolled back at method exit -- clean it up explicitly,
        // in dependency order. The day-hours cleanup is routed through DeskAgentService's own
        // @Transactional boundary rather than calling the spy repository directly: a
        // @MockitoSpyBean's delegate does not carry Spring Data's self-transactional proxy
        // behaviour, so a direct write call with no ambient transaction throws
        // TransactionRequiredException. agentRepository/deskRepository are plain (non-spy)
        // beans, so their own self-transactional behaviour applies as normal.
        deskAgentService.setContractedHours(desk.getId(), agent.getId(), null);
        agentRepository.deleteById(agent.getId());
        deskRepository.deleteById(desk.getId());
        TenantContext.clear();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void setContractedHours_failureOnTheFourthOfSevenRowWrites_persistsNothing() {
        // 1. Seed a committed baseline of 7 rows at 6.00 and capture ids + hours.
        deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("6"));

        List<AgentDayHours> baselineRows =
                agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId());
        assertThat(baselineRows).hasSize(7);

        Map<DayOfWeek, UUID> baselineIds = new HashMap<>();
        Map<DayOfWeek, BigDecimal> baselineHours = new HashMap<>();
        for (AgentDayHours row : baselineRows) {
            baselineIds.put(row.getDayOfWeek(), row.getId());
            baselineHours.put(row.getDayOfWeek(), row.getHours());
        }

        // Reset the invocation count so the save-count verification below reflects only the
        // failing call made in step 3, not the 7 baseline-seeding saves from step 1.
        clearInvocations(agentDayHoursRepository);

        // 2. Inject a failure on the THURSDAY row write only -- argument-matched stubbing, not
        // an answer that calls the real method (a @MockitoSpyBean over a Spring Data JDK proxy
        // is delegation-backed, not superclass-backed, so callRealMethod() cannot work here).
        // DayOfWeek.values() runs MONDAY-first, so THURSDAY is the fourth of seven writes.
        doThrow(new IllegalStateException("simulated failure on THURSDAY row write"))
                .when(agentDayHoursRepository)
                .save(argThat(row -> row != null && row.getDayOfWeek() == DayOfWeek.THURSDAY));

        // 3. The failing call must throw. Use 9 (not a large value) so it passes the range guard
        // added in task 1 and genuinely reaches the recreate loop.
        assertThatThrownBy(() ->
                deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("9")))
                .isInstanceOf(IllegalStateException.class);

        // 4. Read back: exactly 7 rows, all still at 6.00, same ids as the baseline (the
        // preceding deleteByAgent_Id was rolled back too), and the scalar unchanged.
        List<AgentDayHours> rowsAfterFailure =
                agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId());
        assertThat(rowsAfterFailure).hasSize(7);
        assertThat(rowsAfterFailure).allSatisfy(row ->
                assertThat(row.getHours()).isEqualByComparingTo(new BigDecimal("6.00")));

        Map<DayOfWeek, UUID> idsAfterFailure = new HashMap<>();
        for (AgentDayHours row : rowsAfterFailure) {
            idsAfterFailure.put(row.getDayOfWeek(), row.getId());
        }
        assertThat(idsAfterFailure).isEqualTo(baselineIds);

        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getContractedHoursPerDay())
                .isEqualByComparingTo(new BigDecimal("6.00"));

        // 5. Verify the failure was genuinely mid-loop, not pre-loop: save was invoked at least
        // 4 times (Mon/Tue/Wed succeed, Thu throws) during the failing call alone -- invocations
        // are reset via clearInvocations above so this count excludes the baseline seeding call.
        verify(agentDayHoursRepository, atLeast(4)).save(org.mockito.ArgumentMatchers.any());
    }
}
