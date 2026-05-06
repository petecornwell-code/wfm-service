package com.wfm.repository;

import com.wfm.model.StaffingRequirement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StaffingRequirementRepository extends JpaRepository<StaffingRequirement, UUID> {

    // --- Paginated list (no date filter, no cursor) ---
    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "ORDER BY t.date, t.startTime, s.name, sr.id")
    List<StaffingRequirement> findLiveByDesk(long tenantId, UUID deskId, Pageable pageable);

    // --- Paginated list (no date filter, with cursor) ---
    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "AND (t.date > :cursorDate " +
           "  OR (t.date = :cursorDate AND t.startTime > :cursorStartTime) " +
           "  OR (t.date = :cursorDate AND t.startTime = :cursorStartTime AND s.name > :cursorSpecName) " +
           "  OR (t.date = :cursorDate AND t.startTime = :cursorStartTime AND s.name = :cursorSpecName AND sr.id > :cursorId)) " +
           "ORDER BY t.date, t.startTime, s.name, sr.id")
    List<StaffingRequirement> findLiveByDeskAfterCursor(
            long tenantId, UUID deskId,
            LocalDate cursorDate, LocalTime cursorStartTime, String cursorSpecName, UUID cursorId,
            Pageable pageable);

    // --- Paginated list (with date filter, no cursor) ---
    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "AND t.date BETWEEN :from AND :to " +
           "ORDER BY t.date, t.startTime, s.name, sr.id")
    List<StaffingRequirement> findLiveByDeskAndDateRange(
            long tenantId, UUID deskId, LocalDate from, LocalDate to, Pageable pageable);

    // --- Paginated list (with date filter, with cursor) ---
    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "AND t.date BETWEEN :from AND :to " +
           "AND (t.date > :cursorDate " +
           "  OR (t.date = :cursorDate AND t.startTime > :cursorStartTime) " +
           "  OR (t.date = :cursorDate AND t.startTime = :cursorStartTime AND s.name > :cursorSpecName) " +
           "  OR (t.date = :cursorDate AND t.startTime = :cursorStartTime AND s.name = :cursorSpecName AND sr.id > :cursorId)) " +
           "ORDER BY t.date, t.startTime, s.name, sr.id")
    List<StaffingRequirement> findLiveByDeskAndDateRangeAfterCursor(
            long tenantId, UUID deskId, LocalDate from, LocalDate to,
            LocalDate cursorDate, LocalTime cursorStartTime, String cursorSpecName, UUID cursorId,
            Pageable pageable);

    // --- Unpaginated date range (used by save/delete operations) ---
    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "AND t.date BETWEEN :from AND :to")
    List<StaffingRequirement> findLiveByDeskAndDateRange(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM StaffingRequirement sr WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId " +
           "AND sr.scheduleId IS NULL AND sr.timeslot.id IN " +
           "(SELECT t.id FROM Timeslot t WHERE t.tenantId = :tenantId AND t.deskId = :deskId " +
           "AND t.scheduleId IS NULL AND t.date BETWEEN :from AND :to)")
    void deleteLiveByDeskAndDateRange(long tenantId, UUID deskId, LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM StaffingRequirement sr WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId " +
           "AND sr.scheduleId IS NULL AND sr.timeslot.id IN :timeslotIds")
    void deleteLiveByDeskAndTimeslotIds(long tenantId, UUID deskId, Collection<UUID> timeslotIds);

    void deleteByTenantIdAndDeskIdAndScheduleIdIsNull(long tenantId, UUID deskId);

    void deleteByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);

    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId = :scheduleId " +
           "ORDER BY t.date, t.startTime, s.name")
    List<StaffingRequirement> findByTenantIdAndDeskIdAndScheduleId(
            long tenantId, UUID deskId, UUID scheduleId);

    boolean existsBySpecialization_Id(UUID specializationId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
