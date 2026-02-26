package com.wfm.repository;

import com.wfm.model.StaffingRequirement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface StaffingRequirementRepository extends JpaRepository<StaffingRequirement, UUID> {

    @Query("SELECT sr FROM StaffingRequirement sr JOIN sr.timeslot t " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "AND t.date BETWEEN :from AND :to " +
           "ORDER BY t.date, t.startTime")
    List<StaffingRequirement> findLiveByDeskAndDateRange(
            long tenantId, UUID deskId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT sr FROM StaffingRequirement sr JOIN sr.timeslot t " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "AND t.date BETWEEN :from AND :to")
    List<StaffingRequirement> findLiveByDeskAndDateRange(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    void deleteByTenantIdAndDeskIdAndScheduleIdIsNull(long tenantId, UUID deskId);

    void deleteByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);
}
