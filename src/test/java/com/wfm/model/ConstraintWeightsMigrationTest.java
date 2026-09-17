package com.wfm.model;

import com.wfm.support.PostgresBackedTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves V49 ({@code set_consistency_weight_defaults}) against a real Postgres with real Flyway
 * migrations applied -- not H2's entity-derived schema, which never executes a migration at all
 * (G-14-1). Two things V49 must get right:
 *
 * <p><b>1. Column defaults.</b> The DB-level defaults for NEW rows must be the chosen pair
 * ({@code consistent_start_weight = 0hard/2soft}, {@code preferred_start_shift_mode_weight =
 * 0hard/1soft}) -- checked directly against {@code information_schema.columns}, not through the
 * JPA entity, since the entity's own field initialisers would pass even if V49's
 * {@code ALTER COLUMN ... SET DEFAULT} were missing or wrong.
 *
 * <p><b>2. The predicated UPDATE (T-17-06) leaves an operator-tuned row untouched.</b> V49 itself
 * runs once, at container bootstrap, before this test's fixtures exist -- there is no way inside
 * a single test run to insert a "pre-V49" row and then trigger the migration against it. This
 * test instead re-executes the SAME literal {@code UPDATE} statements V49 issues (copied verbatim
 * from the migration file) against a fixture built to represent both cases the {@code WHERE}
 * predicate must distinguish: a row still holding the shipped default (confirmed, value
 * unchanged) and a row an operator has already tuned to something else (left alone). Proving the
 * predicate's own logic here is the closest a single-boot migration-chain test can get to proving
 * the migration is safe against live tenant rows.
 */
class ConstraintWeightsMigrationTest extends PostgresBackedTest {

    @Autowired
    private TestEntityManager entityManager;

    private static final long TENANT_ID = 1L;

    private UUID saveDesk(EntityManager em, String name) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("INSERT INTO desk (id, tenant_id, name, scheduling_mode) "
                        + "VALUES (?1, ?2, ?3, 'SLOT')")
                .setParameter(1, id)
                .setParameter(2, TENANT_ID)
                .setParameter(3, name)
                .executeUpdate();
        return id;
    }

    private void insertConstraintWeightsRow(
            EntityManager em, UUID deskId, String consistentStart, String preferredStart) {
        em.createNativeQuery("INSERT INTO constraint_weights "
                        + "(id, tenant_id, desk_id, consistent_start_weight, preferred_start_shift_mode_weight) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, TENANT_ID)
                .setParameter(3, deskId)
                .setParameter(4, consistentStart)
                .setParameter(5, preferredStart)
                .executeUpdate();
    }

    private String readWeight(EntityManager em, UUID deskId, String column) {
        List<?> rows = em.createNativeQuery(
                        "SELECT " + column + " FROM constraint_weights WHERE desk_id = ?1")
                .setParameter(1, deskId)
                .getResultList();
        assertThat(rows).hasSize(1);
        return (String) rows.get(0);
    }

    @Test
    void columnDefaults_matchTheChosenPair() {
        EntityManager em = entityManager.getEntityManager();
        List<?> rows = em.createNativeQuery(
                        "SELECT column_name, column_default FROM information_schema.columns "
                                + "WHERE table_name = 'constraint_weights' "
                                + "AND column_name IN ('consistent_start_weight', 'preferred_start_shift_mode_weight') "
                                + "ORDER BY column_name")
                .getResultList();
        assertThat(rows).hasSize(2);

        // Alphabetical order: consistent_start_weight, then preferred_start_shift_mode_weight.
        Object[] consistentStartRow = (Object[]) rows.get(0);
        Object[] preferredStartRow = (Object[]) rows.get(1);

        assertThat((String) consistentStartRow[0]).isEqualTo("consistent_start_weight");
        assertThat((String) consistentStartRow[1]).contains("0hard/2soft");

        assertThat((String) preferredStartRow[0]).isEqualTo("preferred_start_shift_mode_weight");
        assertThat((String) preferredStartRow[1]).contains("0hard/1soft");
    }

    @Test
    void predicatedUpdate_confirmsShippedDefaultRow_leavesOperatorTunedRowUntouched() {
        EntityManager em = entityManager.getEntityManager();

        UUID deskHoldingDefault = saveDesk(em, "Default-Desk-" + UUID.randomUUID());
        UUID deskOperatorTuned = saveDesk(em, "Tuned-Desk-" + UUID.randomUUID());

        // Row still holding V38/V48's shipped defaults -- the case T-17-06's predicate must match.
        insertConstraintWeightsRow(em, deskHoldingDefault, "0hard/2soft", "0hard/1soft");
        // Row an operator has already tuned away from the shipped default on both columns -- the
        // case T-17-06's predicate must NOT match.
        insertConstraintWeightsRow(em, deskOperatorTuned, "0hard/5soft", "0hard/3soft");
        em.flush();

        // Verbatim copy of V49__set_consistency_weight_defaults.sql's two UPDATE statements.
        em.createNativeQuery("UPDATE constraint_weights "
                        + "SET consistent_start_weight = '0hard/2soft' "
                        + "WHERE consistent_start_weight = '0hard/2soft'")
                .executeUpdate();
        em.createNativeQuery("UPDATE constraint_weights "
                        + "SET preferred_start_shift_mode_weight = '0hard/1soft' "
                        + "WHERE preferred_start_shift_mode_weight = '0hard/1soft'")
                .executeUpdate();
        em.flush();
        em.clear();

        assertThat(readWeight(em, deskHoldingDefault, "consistent_start_weight"))
                .isEqualTo("0hard/2soft");
        assertThat(readWeight(em, deskHoldingDefault, "preferred_start_shift_mode_weight"))
                .isEqualTo("0hard/1soft");

        assertThat(readWeight(em, deskOperatorTuned, "consistent_start_weight"))
                .isEqualTo("0hard/5soft");
        assertThat(readWeight(em, deskOperatorTuned, "preferred_start_shift_mode_weight"))
                .isEqualTo("0hard/3soft");
    }
}
