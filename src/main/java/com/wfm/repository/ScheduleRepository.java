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

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
