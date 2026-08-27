package com.wfm.repository;

import com.wfm.model.AgentShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Tenant- and desk-scoped reads of accepted {@link AgentShiftAssignment} rows (T-15-26) —
 * mirrors {@link AgentAssignmentRepository}'s naming convention exactly. Every method takes
 * {@code tenantId} and {@code deskId} explicitly and filters on both; there is no database
 * row-level security, so this is the mitigation.
 */
@Repository
public interface AgentShiftAssignmentRepository extends JpaRepository<AgentShiftAssignment, UUID> {

    List<AgentShiftAssignment> findByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

    /**
     * Relations-fetching variant for the output path (Plan 07 Task 2) — eagerly loads the agent
     * association, mirroring {@code AgentAssignmentRepository
     * .findWithRelationsByTenantIdAndDeskIdAndScheduleId}. Ordered by date then agent name so a
     * reloaded accepted schedule's shift rows arrive in a deterministic, display-friendly order.
     */
    @Query("SELECT sa FROM AgentShiftAssignment sa " +
           "JOIN FETCH sa.agent a " +
           "WHERE sa.tenantId = :tenantId AND sa.deskId = :deskId AND sa.scheduleId = :scheduleId " +
           "ORDER BY sa.date, a.name")
    List<AgentShiftAssignment> findWithRelationsByTenantIdAndDeskIdAndScheduleId(
            long tenantId, UUID deskId, UUID scheduleId);
}
