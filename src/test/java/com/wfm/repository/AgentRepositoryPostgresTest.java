package com.wfm.repository;

import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.support.PostgresBackedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The Postgres-only half of {@code AgentRepository.findFiltered}'s optional-{@code :search}
 * contract (UAT test 14b).
 *
 * <p>{@code AgentServiceListFilterTest} pins the same behaviour on H2 and is honest that it cannot
 * fail: H2 tolerates an untyped null parameter, so those assertions passed before the fix as well
 * as after. THIS class is the one that bites. On Postgres a null {@code :search} arrives as
 * {@code unknown}, is resolved to {@code bytea} inside {@code lower('%' || ? || '%')}, and the
 * statement dies with:
 *
 * <pre>ERROR: function lower(bytea) does not exist</pre>
 *
 * <p>Verified to fail against the pre-fix query before being accepted — remove either
 * {@code CAST(:search AS String)} from {@code findFiltered} and this class goes red with exactly
 * that message, which is the only reason it is worth its container start.
 *
 * <p>Both query methods are covered. {@code findFilteredAfterCursor} carries the identical
 * predicate and would have failed the same way on the second page; nobody reported it only because
 * the first page already 500'd.
 */
class AgentRepositoryPostgresTest extends PostgresBackedTest {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private DeskRepository deskRepository;

    private static final long TENANT_A = 1L;

    @Test
    void findFiltered_nullSearch_doesNotBlowUpOnPostgresAndReturnsEveryAgent() {
        saveAgent("Alice", null);
        saveAgent("Bob", saveDesk());

        List<Agent> agents = agentRepository.findFiltered(TENANT_A, null, false, PageRequest.of(0, 50));

        assertThat(agents).extracting(Agent::getName).containsExactly("Alice", "Bob");
    }

    @Test
    void findFiltered_nullSearchWithUnassigned_doesNotBlowUpAndStillFiltersByDesk() {
        saveAgent("Alice", null);
        saveAgent("Bob", saveDesk());

        List<Agent> agents = agentRepository.findFiltered(TENANT_A, null, true, PageRequest.of(0, 50));

        assertThat(agents).extracting(Agent::getName).containsExactly("Alice");
    }

    @Test
    void findFilteredAfterCursor_nullSearch_doesNotBlowUpOnThePagingPath() {
        saveAgent("Alice", null);
        Agent bob = saveAgent("Bob", null);
        saveAgent("Carol", null);

        assertThatCode(() -> agentRepository.findFilteredAfterCursor(
                TENANT_A, null, false, bob.getName(), bob.getId(), PageRequest.of(0, 50)))
                .doesNotThrowAnyException();

        List<Agent> afterBob = agentRepository.findFilteredAfterCursor(
                TENANT_A, null, false, bob.getName(), bob.getId(), PageRequest.of(0, 50));
        assertThat(afterBob).extracting(Agent::getName).containsExactly("Carol");
    }

    @Test
    void findFiltered_withSearchTerm_stillMatchesCaseInsensitiveSubstringsOnPostgres() {
        // The cast must not have turned LIKE into equality, and Postgres's LOWER must still be
        // applied to both sides — the H2 twin cannot prove that about Postgres collation.
        saveAgent("Alice Anderson", null);
        saveAgent("Bob Brown", null);

        assertThat(agentRepository.findFiltered(TENANT_A, "ANDERS", false, PageRequest.of(0, 50)))
                .extracting(Agent::getName).containsExactly("Alice Anderson");
        assertThat(agentRepository.findFiltered(TENANT_A, "zzz", false, PageRequest.of(0, 50)))
                .isEmpty();
    }

    /**
     * A REAL desk row, not a random UUID. The live schema carries {@code agent_desk_id_fkey};
     * H2's generated schema does not, because the entity maps {@code deskId} as a plain UUID with
     * no association. Inventing an id passes on H2 and violates the foreign key on Postgres — the
     * first thing this class caught when it was written.
     */
    private UUID saveDesk() {
        Desk desk = new Desk();
        desk.setTenantId(TENANT_A);
        desk.setName("Desk " + UUID.randomUUID());
        return deskRepository.save(desk).getId();
    }

    private Agent saveAgent(String name, UUID deskId) {
        Agent agent = new Agent();
        agent.setTenantId(TENANT_A);
        agent.setDeskId(deskId);
        agent.setName(name);
        agent.setBamboohrId("BHR-" + UUID.randomUUID());
        agent.setActive(true);
        return agentRepository.save(agent);
    }
}
