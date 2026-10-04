package com.wfm.repository;

import com.wfm.model.AgentRestWaiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant- and desk-scoped reads and deletes of {@link AgentRestWaiver} rows (ASVS V4) — mirrors
 * {@link AgentExceptionRepository}'s method set exactly, with {@link AgentRestWaiver} substituted.
 * Every method takes {@code tenantId} and {@code deskId} explicitly and filters on both; there is
 * no database row-level security, so this is the mitigation. There is no bare {@code findById}
 * method, so no finder can reach another tenant's or another desk's rows.
 */
@Repository
public interface AgentRestWaiverRepository extends JpaRepository<AgentRestWaiver, UUID> {

    List<AgentRestWaiver> findByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    List<AgentRestWaiver> findByTenantIdAndDeskIdAndAgent_IdAndDateBetween(
            long tenantId, UUID deskId, UUID agentId, LocalDate from, LocalDate to);

    Optional<AgentRestWaiver> findByTenantIdAndDeskIdAndAgent_IdAndDate(
            long tenantId, UUID deskId, UUID agentId, LocalDate date);

    /**
     * Desk-wide, no agent parameter — the finder plan 22-05's problem-fact population will call to
     * load every waiver for a schedule's period. Named here so a later reader does not mistake it
     * for dead surface.
     *
     * <p>REST-07/P-03 (22-12): superseded as a waiver-loading call site by
     * {@link #findWithAgentByTenantIdAndDeskIdAndDateBetween} on every production path (including
     * this method's own former caller, {@code ScheduleService.loadSnapshotData}) — the agent
     * association is {@code @ManyToOne(fetch = FetchType.LAZY)} and
     * {@code spring.jpa.open-in-view} is {@code false}, so this non-fetching finder yields a lazy
     * proxy that throws on initialisation outside a transaction. Left declared (not removed) as a
     * directly-testable repository method in its own right.
     */
    List<AgentRestWaiver> findByTenantIdAndDeskIdAndDateBetween(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    /**
     * REST-07/P-03 (22-VERIFICATION.md gap (a)): a relations-fetching sibling of
     * {@link #findByTenantIdAndDeskIdAndDateBetween} above — a correctness requirement, not an
     * optimisation. {@code spring.jpa.open-in-view} is {@code false} and
     * {@link com.wfm.model.AgentRestWaiver#getAgent()} is {@code @ManyToOne(fetch =
     * FetchType.LAZY)}, while {@code ScheduleOutputService.buildRestWaiverDisclosure} reads the
     * waiver's agent's name — so on the non-transactional {@code ScheduleService.listSchedules}
     * and {@code getScheduleSummary} paths, the non-fetching finder above would yield a lazy proxy
     * that throws {@code LazyInitializationException} on initialisation, not merely run a slower
     * query. {@code loadSnapshotData} is re-pointed at THIS finder too, so the disclosure's waiver
     * input is loaded exactly one way on every path. Tenant- and desk-scoped in its own
     * {@code WHERE} clause, per this class's own javadoc — there is no database row-level
     * security, so this is the mitigation (T-22-33). Ordered by date then the fetched agent's
     * name for determinism.
     */
    @Query("SELECT w FROM AgentRestWaiver w "
            + "JOIN FETCH w.agent a "
            + "WHERE w.tenantId = :tenantId AND w.deskId = :deskId "
            + "AND w.date BETWEEN :from AND :to "
            + "ORDER BY w.date, a.name")
    List<AgentRestWaiver> findWithAgentByTenantIdAndDeskIdAndDateBetween(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    void deleteByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
