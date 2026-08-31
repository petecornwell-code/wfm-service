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
     * Backs {@code ShiftTemplateService.deleteShiftTemplate}'s use guard. Counts rather than
     * fetches: the caller needs only whether a template has ever been part of a real schedule, and
     * the message quotes the figure. Tenant-scoped like every other method here — a cross-tenant
     * id must never influence a delete decision.
     */
    long countByTenantIdAndSourceTemplateId(long tenantId, UUID sourceTemplateId);

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

    /**
     * CR-03 gap closure: {@code ScheduleService.deleteSchedule} deletes {@code agent_assignment},
     * {@code staffing_requirement} and {@code timeslot} rows for a deleted accepted schedule but,
     * before this method existed, never touched {@code agent_shift_assignment} — this table has
     * no {@code deleteBy*} method at all and {@code agent_shift_assignment.schedule_id} carries no
     * FK (V41), so deleting the schedule silently orphaned every shift-envelope row it had. Mirrors
     * {@link AgentAssignmentRepository#deleteByTenantIdAndDeskIdAndScheduleId} exactly.
     */
    void deleteByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);
}
