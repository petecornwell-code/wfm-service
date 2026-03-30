package com.wfm.repository;

import com.wfm.model.AcceptedScheduleDate;
import com.wfm.model.AcceptedScheduleDateId;
import com.wfm.model.AcceptedScheduleDateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AcceptedScheduleDateRepository extends JpaRepository<AcceptedScheduleDate, AcceptedScheduleDateId> {

    List<AcceptedScheduleDate> findByTenantIdAndDeskIdAndDateInAndStatus(
            long tenantId, UUID deskId, Collection<LocalDate> dates, AcceptedScheduleDateStatus status);

    @Modifying
    @Query("UPDATE AcceptedScheduleDate a SET a.status = :newStatus " +
           "WHERE a.tenantId = :tenantId AND a.deskId = :deskId " +
           "AND a.date IN :dates AND a.status = :currentStatus")
    int updateStatusByTenantIdAndDeskIdAndDateIn(
            long tenantId, UUID deskId, Collection<LocalDate> dates,
            AcceptedScheduleDateStatus currentStatus, AcceptedScheduleDateStatus newStatus);
}
