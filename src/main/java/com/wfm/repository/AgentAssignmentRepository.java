package com.wfm.repository;

import com.wfm.model.AgentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentAssignmentRepository extends JpaRepository<AgentAssignment, UUID> {

    List<AgentAssignment> findByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

    @Query("SELECT aa FROM AgentAssignment aa " +
           "JOIN FETCH aa.timeslot t " +
           "JOIN FETCH aa.requiredSpecialization rs " +
           "LEFT JOIN FETCH aa.deskAgent da " +
           "LEFT JOIN FETCH da.agent a " +
           "LEFT JOIN FETCH da.primarySpecialization ps " +
           "WHERE aa.tenantId = :tenantId AND aa.deskId = :deskId AND aa.scheduleId = :scheduleId " +
           "ORDER BY t.date, t.startTime")
    List<AgentAssignment> findWithRelationsByTenantIdAndDeskIdAndScheduleId(
            long tenantId, UUID deskId, UUID scheduleId);

    void deleteByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
