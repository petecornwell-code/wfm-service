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

    // --- Business-date twins of the two paginated date-range queries above (phase 24 D-04, audit
    // N-2). They differ from their calendar twins in exactly one place: the range predicate
    // filters the timeslot's STORED business date, so a caller must pass bounds already in the
    // business-date system. The tenant/desk/live predicates, the keyset cursor predicate and the
    // sort are copied verbatim, so a cursor minted by either twin resumes correctly in the other.
    // Filtering the stored column (the value the single BDAY-08 derivation wrote) rather than
    // widening the calendar range and re-deriving in memory keeps hasMore and the cursor exact on
    // a paginated endpoint.
    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "AND t.businessDate BETWEEN :from AND :to " +
           "ORDER BY t.date, t.startTime, s.name, sr.id")
    List<StaffingRequirement> findLiveByDeskAndBusinessDateRange(
            long tenantId, UUID deskId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "AND t.businessDate BETWEEN :from AND :to " +
           "AND (t.date > :cursorDate " +
           "  OR (t.date = :cursorDate AND t.startTime > :cursorStartTime) " +
           "  OR (t.date = :cursorDate AND t.startTime = :cursorStartTime AND s.name > :cursorSpecName) " +
           "  OR (t.date = :cursorDate AND t.startTime = :cursorStartTime AND s.name = :cursorSpecName AND sr.id > :cursorId)) " +
           "ORDER BY t.date, t.startTime, s.name, sr.id")
    List<StaffingRequirement> findLiveByDeskAndBusinessDateRangeAfterCursor(
            long tenantId, UUID deskId, LocalDate from, LocalDate to,
            LocalDate cursorDate, LocalTime cursorStartTime, String cursorSpecName, UUID cursorId,
            Pageable pageable);

    // --- Unpaginated date range (used by save/delete operations) ---
    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
           "AND t.date BETWEEN :from AND :to")
    List<StaffingRequirement> findLiveByDeskAndDateRange(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    // --- Unpaginated, unfiltered live read (P-16) — a template's effective_to may be null
    // (open-ended), so the union of every template's effective range is unbounded and the
    // date-ranged variant above cannot express it. Does not fetch sr.specialization: the shift
    // library validator only reads sr.timeslot and sr.requiredFTEs.
    @Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t " +
           "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL")
    List<StaffingRequirement> findAllLiveByDesk(long tenantId, UUID deskId);

    // Calendar-date twin. OVNT-02 (phase 21, migrate-now decision): calculateErlangC and
    // calculateErlangX were migrated off this method onto deleteLiveByDeskAndBusinessDateRange
    // below, because their inserts are keyed by explicit timeslotId while the clear is by range,
    // and the two disagree on a desk whose day start is not midnight. This method's two remaining
    // callers are TimeslotGeneratorService's generation path and FteUploadService's FTE upload
    // path, both of which keep calendar-date semantics deliberately: neither is keyed by explicit
    // timeslot id against a payload the way the Erlang calculators are, and both operate on
    // operator-facing calendar dates where a calendar-scoped clear is the semantics the operator
    // expects (the same SOLV-07/D-10 "a label/date answers a calendar question" reasoning this
    // codebase already applies elsewhere). Re-pointing either of these two remaining callers is a
    // new decision, not an extension of this one.
    @Modifying
    @Query("DELETE FROM StaffingRequirement sr WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId " +
           "AND sr.scheduleId IS NULL AND sr.timeslot.id IN " +
           "(SELECT t.id FROM Timeslot t WHERE t.tenantId = :tenantId AND t.deskId = :deskId " +
           "AND t.scheduleId IS NULL AND t.date BETWEEN :from AND :to)")
    void deleteLiveByDeskAndDateRange(long tenantId, UUID deskId, LocalDate from, LocalDate to);

    // Business-date twin of deleteLiveByDeskAndDateRange (SOLV-07/D-15). The two methods differ
    // in exactly one place -- this one filters the timeslot's business date, the other its
    // calendar date -- so a caller must pass bounds already in the matching date system: passing
    // calendar-date bounds here, or business-date bounds to the sibling method, silently deletes
    // the wrong span of live operator demand. The demand-upload replace path
    // (StaffingRequirementService.saveRequirements) is this method's only caller.
    @Modifying
    @Query("DELETE FROM StaffingRequirement sr WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId " +
           "AND sr.scheduleId IS NULL AND sr.timeslot.id IN " +
           "(SELECT t.id FROM Timeslot t WHERE t.tenantId = :tenantId AND t.deskId = :deskId " +
           "AND t.scheduleId IS NULL AND t.businessDate BETWEEN :from AND :to)")
    void deleteLiveByDeskAndBusinessDateRange(long tenantId, UUID deskId, LocalDate from, LocalDate to);

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
