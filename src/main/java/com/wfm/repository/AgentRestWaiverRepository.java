package com.wfm.repository;

import com.wfm.model.AgentRestWaiver;
import org.springframework.data.jpa.repository.JpaRepository;
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
     */
    List<AgentRestWaiver> findByTenantIdAndDeskIdAndDateBetween(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    void deleteByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
