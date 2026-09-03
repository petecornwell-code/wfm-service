package com.wfm.repository;

import com.wfm.model.AgentUsualShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentUsualShiftRepository extends JpaRepository<AgentUsualShift, UUID> {

    List<AgentUsualShift> findByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    // Single-weekday finder for the per-cell upsert (UsualShiftService.setUsualShift). Not
    // tenant-scoped, mirroring deleteByAgent_Id below -- callers must resolve tenant scope
    // through AgentRepository before calling this (T-13-05/T-16-01).
    Optional<AgentUsualShift> findByAgent_IdAndDayOfWeek(UUID agentId, DayOfWeek dayOfWeek);

    // Bulk fetch for the roster read -- mirrors AgentDayHoursRepository's join-through-agent
    // style. AgentUsualShift has no desk_id column of its own; desk scoping goes through
    // u.agent.deskId.
    @Query("SELECT u FROM AgentUsualShift u WHERE u.tenantId = :tenantId AND u.agent.deskId = :deskId")
    List<AgentUsualShift> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    // Agent-scoped delete for DeskAssignmentUploadService.clearDesk (D-11) and
    // DeskAgentService.removeDeskAgent (D-12), both via UsualShiftService.clearUsualShifts.
    void deleteByAgent_Id(UUID agentId);

    // Template-usage guard for ShiftTemplateService.deleteShiftTemplate (T-16-09/P-10). Not
    // tenant-scoped, mirroring findByAgent_IdAndDayOfWeek/deleteByAgent_Id above -- the only
    // caller resolves the template through findByIdAndTenantIdAndDeskId first, so the id is
    // already proven in tenant+desk scope before this count runs.
    long countByShiftTemplate_Id(UUID shiftTemplateId);
}
