package com.wfm.repository;

import com.wfm.model.Agent;
import com.wfm.model.AgentUsualShift;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.support.PostgresBackedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@code agent_usual_shift} (V47) against a real Postgres with real Flyway migrations, not
 * H2's entity-derived schema.
 *
 * <p>{@code application-test.yml} sets {@code flyway.enabled: false} with {@code ddl-auto:
 * create-drop} on H2, so the default suite builds the schema from the entities and never executes
 * V47; V39 shipped a blank-padded fixed-length column against a variable-length entity mapping and
 * the application failed to boot under {@code ddl-auto=validate} with a fully green 402-test suite
 * (G-14-1). Reaching the assertions below means the migration actually ran and the entity mapping
 * was validated against it -- {@code disabledWithoutDocker = true} on {@link PostgresBackedTest}
 * means this class SKIPS rather than fails where Docker is unavailable; CI's {@code ubuntu-latest}
 * runner has Docker, so this class gates the deploy pipeline even where a local run skips it.
 */
class AgentUsualShiftPostgresTest extends PostgresBackedTest {

    @Autowired
    private AgentUsualShiftRepository agentUsualShiftRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private ShiftTemplateRepository shiftTemplateRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final long TENANT_ID = 1L;

    private Desk saveDesk() {
        Desk desk = new Desk();
        desk.setTenantId(TENANT_ID);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk);
    }

    private Agent saveAgent(UUID deskId) {
        Agent agent = new Agent();
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId("B-" + UUID.randomUUID());
        agent.setName("Jane Doe");
        agent.setDeskId(deskId);
        return agentRepository.save(agent);
    }

    private ShiftTemplate saveTemplate(UUID deskId, String name) {
        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(TENANT_ID);
        template.setDeskId(deskId);
        template.setName(name);
        template.setStartTime(LocalTime.of(8, 0));
        template.setEndTime(LocalTime.of(17, 0));
        template.setValidWeekdays(Set.of(DayOfWeek.MONDAY));
        template.setEffectiveFrom(LocalDate.now().minusDays(1));
        return shiftTemplateRepository.save(template);
    }

    private AgentUsualShift saveUsualShift(Agent agent, DayOfWeek day, ShiftTemplate template) {
        AgentUsualShift row = new AgentUsualShift();
        row.setTenantId(TENANT_ID);
        row.setAgent(agent);
        row.setDayOfWeek(day);
        row.setShiftTemplate(template);
        return agentUsualShiftRepository.save(row);
    }

    /**
     * Migration-vs-entity proof (P-04 assertion 1): persist and read back one AgentUsualShift with
     * a real Agent and a real ShiftTemplate. Reaching this assertion at all IS the
     * {@code ddl-auto=validate} proof -- a mismatch between V47 and the entity mapping fails
     * application context startup before any assertion in this class runs.
     */
    @Test
    void persistAndReload_roundTripsEveryField() {
        Desk desk = saveDesk();
        Agent agent = saveAgent(desk.getId());
        ShiftTemplate template = saveTemplate(desk.getId(), "Early");

        AgentUsualShift saved = saveUsualShift(agent, DayOfWeek.MONDAY, template);
        entityManager.flush();
        entityManager.clear();

        AgentUsualShift reloaded = agentUsualShiftRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(reloaded.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(reloaded.getAgent().getId()).isEqualTo(agent.getId());
        assertThat(reloaded.getShiftTemplate().getId()).isEqualTo(template.getId());
        assertThat(reloaded.getShiftTemplate().getName()).isEqualTo("Early");
    }

    /**
     * Unique-constraint proof (assertion 2, USHF-05/concurrency edge): a second row for the same
     * (agent_id, day_of_week) fails at the database rather than creating a duplicate.
     */
    @Test
    void secondRowForSameAgentAndDay_violatesUniqueConstraint() {
        Desk desk = saveDesk();
        Agent agent = saveAgent(desk.getId());
        ShiftTemplate early = saveTemplate(desk.getId(), "Early");
        ShiftTemplate late = saveTemplate(desk.getId(), "Late");

        saveUsualShift(agent, DayOfWeek.MONDAY, early);
        entityManager.flush();

        AgentUsualShift duplicate = new AgentUsualShift();
        duplicate.setTenantId(TENANT_ID);
        duplicate.setAgent(agent);
        duplicate.setDayOfWeek(DayOfWeek.MONDAY);
        duplicate.setShiftTemplate(late);

        // saveAndFlush (not save + entityManager.flush()) so the write goes through the Spring
        // Data repository proxy -- only that path applies PersistenceExceptionTranslation,
        // converting the raw JPA/Hibernate constraint violation into a DataIntegrityViolationException.
        assertThatThrownBy(() -> agentUsualShiftRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Assert on the exception only -- Postgres marks the whole transaction aborted after a
        // constraint violation, so any further query in this same @DataJpaTest transaction would
        // itself fail with "current transaction is aborted", not prove anything about row counts.
        // Clearing the persistence context discards the now-stale in-memory `duplicate` reference.
        entityManager.clear();
    }

    /**
     * Agent cascade proof (assertion 3): deleting the agent row removes its agent_usual_shift
     * rows, matching agent_day_hours' own V29 ON DELETE CASCADE behaviour.
     */
    @Test
    void deletingAgent_cascadesToUsualShiftRows() {
        Desk desk = saveDesk();
        Agent agent = saveAgent(desk.getId());
        ShiftTemplate template = saveTemplate(desk.getId(), "Early");
        saveUsualShift(agent, DayOfWeek.MONDAY, template);
        entityManager.flush();

        entityManager.getEntityManager()
                .createNativeQuery("DELETE FROM agent WHERE id = ?1")
                .setParameter(1, agent.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(agentUsualShiftRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    /**
     * Shift-template cascade proof (assertion 4, P-02, T-16-03): deleting the desk row succeeds
     * and leaves zero agent_usual_shift rows, because shift_template.desk_id's own ON DELETE
     * CASCADE (V39) cascades further into agent_usual_shift.shift_template_id. Without the second
     * cascade this assertion would fail with a foreign-key violation, and every
     * DeskService.deleteDesk call on a desk with stored usual shifts would 500 in production.
     */
    @Test
    void deletingDesk_cascadesThroughShiftTemplateToUsualShiftRows() {
        Desk desk = saveDesk();
        Agent agent = saveAgent(desk.getId());
        ShiftTemplate template = saveTemplate(desk.getId(), "Early");
        AgentUsualShift row = saveUsualShift(agent, DayOfWeek.MONDAY, template);
        entityManager.flush();

        // Native delete, not deskRepository.delete(...): DeskService.deleteDesk performs its own
        // higher-level cleanup first (agent unassignment, schedule/timeslot deletes, etc.) that is
        // out of scope here -- this test isolates the DB-level cascade chain the delete relies on.
        entityManager.getEntityManager()
                .createNativeQuery("DELETE FROM desk WHERE id = ?1")
                .setParameter(1, desk.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(agentUsualShiftRepository.findById(row.getId())).isEmpty();
        assertThat(agentUsualShiftRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    /**
     * Column-type proof (assertion 5): the direct, database-side form of the G-14-1 check --
     * {@code day_of_week} is a variable-length 9-character column, never a fixed-length blank-
     * padded declaration.
     */
    @Test
    void dayOfWeekColumn_isVariableLengthNine() {
        List<?> rows = entityManager.getEntityManager()
                .createNativeQuery("SELECT data_type, character_maximum_length FROM information_schema.columns "
                        + "WHERE table_name = 'agent_usual_shift' AND column_name = 'day_of_week'")
                .getResultList();
        assertThat(rows).hasSize(1);
        Object[] row = (Object[]) rows.get(0);
        assertThat(row[0]).isEqualTo("character varying");
        assertThat(((Number) row[1]).intValue()).isEqualTo(9);
    }
}
