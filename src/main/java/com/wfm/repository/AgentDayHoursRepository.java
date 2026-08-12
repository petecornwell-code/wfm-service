package com.wfm.repository;

import com.wfm.model.AgentDayHours;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentDayHoursRepository extends JpaRepository<AgentDayHours, UUID> {

    List<AgentDayHours> findByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    // Bulk fetch for SolverService — mirrors AgentDayOffRepository's join-through-agent style.
    // AgentDayHours has no desk_id column of its own; desk scoping goes through h.agent.deskId.
    @Query("SELECT h FROM AgentDayHours h WHERE h.tenantId = :tenantId AND h.agent.deskId = :deskId")
    List<AgentDayHours> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    // Recurring day-off labels for a desk, for AgentDayOffService's PTO expansion.
    //
    // @EntityGraph is REQUIRED, mirroring AgentDayOffRepository.findByTenantIdAndDeskIdAndDateBetween:
    // spring.jpa.open-in-view is false, so the caller reads agent.name outside any transaction and
    // a lazy association would throw LazyInitializationException (500). Referencing h.agent.deskId
    // in the WHERE clause joins the table but does NOT initialize the association.
    @EntityGraph(attributePaths = {"agent"})
    @Query("SELECT h FROM AgentDayHours h WHERE h.tenantId = :tenantId " +
           "AND h.agent.deskId = :deskId AND h.dayOffType IS NOT NULL")
    List<AgentDayHours> findDaysOffByTenantIdAndDeskId(long tenantId, UUID deskId);

    // Agent-scoped delete for DeskAgentService fan-out replace + DeskAssignmentUploadService clear.
    // Mirrors AgentDayOffRepository.deleteByAgent_IdAndDateBetween precedent; callers resolve the
    // agent within tenant scope before deleting (T-09-05).
    void deleteByAgent_Id(UUID agentId);
}
