package com.wfm.repository;

import com.wfm.model.Schedule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findByTenantIdAndDeskIdOrderByCreatedAtDesc(long tenantId, UUID deskId, Pageable pageable);

    Optional<Schedule> findByIdAndTenantIdAndDeskId(UUID id, long tenantId, UUID deskId);

    @Query("SELECT s FROM Schedule s WHERE s.tenantId = :tenantId AND s.deskId = :deskId " +
           "AND s.periodStartDate <= :endDate AND s.periodEndDate >= :startDate")
    List<Schedule> findOverlapping(long tenantId, UUID deskId, LocalDate startDate, LocalDate endDate);

    boolean existsByTenantIdAndDeskIdAndStatus(long tenantId, UUID deskId,
                                                com.wfm.model.ScheduleStatus status);

    // BDAY-01: the explicit ordering here is load-bearing, not cosmetic -- it is the only thing
    // that makes DeskService.setDayStart's accepted-schedule refusal message deterministic when
    // a desk holds more than one ACCEPTED schedule. Without it, which schedule's id/period the
    // refusal names would depend on incidental row order rather than being reproducible.
    List<Schedule> findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc(long tenantId, UUID deskId,
                                                com.wfm.model.ScheduleStatus status);

    // OVNT-01/D-04: the batch twin of the finder above -- one query for every desk's lock
    // disclosure rather than the per-desk finder called in a loop. Both must keep createdAt
    // descending so the disclosed row (DeskService.dayStartLocksByDeskId's first-per-deskId pick)
    // and the row the setDayStart refusal names are always the same schedule.
    List<Schedule> findByTenantIdAndStatusOrderByCreatedAtDesc(long tenantId,
                                                com.wfm.model.ScheduleStatus status);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
