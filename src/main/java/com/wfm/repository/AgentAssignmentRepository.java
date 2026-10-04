package com.wfm.repository;

import com.wfm.model.AgentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentAssignmentRepository extends JpaRepository<AgentAssignment, UUID> {

    List<AgentAssignment> findByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

    @Query("SELECT aa FROM AgentAssignment aa " +
           "JOIN FETCH aa.timeslot t " +
           "JOIN FETCH aa.requiredSpecialization rs " +
           "LEFT JOIN FETCH aa.agent a " +
           "LEFT JOIN FETCH a.primarySpecialization ps " +
           "WHERE aa.tenantId = :tenantId AND aa.deskId = :deskId AND aa.scheduleId = :scheduleId " +
           "ORDER BY t.date, t.startTime")
    List<AgentAssignment> findWithRelationsByTenantIdAndDeskIdAndScheduleId(
            long tenantId, UUID deskId, UUID scheduleId);

    /**
     * REST-05/D-10: the date-filtered read the horizon-edge lookback needs — the two finders
     * above both fetch a WHOLE schedule's rows, and on a SLOT desk {@code agent_assignment} holds
     * one row per assigned seat per slot, so a month on a large desk is six figures of rows for a
     * one-day question. Filters on {@code tenantId}, {@code deskId} and {@code scheduleId} plus
     * the JOINED timeslot's {@code businessDate} — never its calendar {@code date} — so a
     * re-anchored desk's post-midnight rows are never silently dropped
     * ({@code BusinessDateJoinGuardTest} polices exactly this class of defect elsewhere in this
     * codebase). {@code JOIN FETCH}es {@code timeslot}, {@code LEFT JOIN FETCH}es {@code agent}.
     * Ordered by the timeslot's start time. Tenant- and desk-scoped like every other method here
     * — there is no database row-level security, so this is the mitigation.
     */
    @Query("SELECT aa FROM AgentAssignment aa " +
           "JOIN FETCH aa.timeslot t " +
           "LEFT JOIN FETCH aa.agent a " +
           "WHERE aa.tenantId = :tenantId AND aa.deskId = :deskId AND aa.scheduleId = :scheduleId " +
           "AND t.businessDate = :businessDate " +
           "ORDER BY t.startTime")
    List<AgentAssignment> findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate(
            long tenantId, UUID deskId, UUID scheduleId, LocalDate businessDate);

    void deleteByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
