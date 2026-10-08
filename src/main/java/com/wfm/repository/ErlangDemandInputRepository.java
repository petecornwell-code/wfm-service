package com.wfm.repository;

import com.wfm.model.ErlangDemandInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ErlangDemandInputRepository extends JpaRepository<ErlangDemandInput, UUID> {

    List<ErlangDemandInput> findByTenantIdAndDeskIdAndTimeslotIdIn(
            long tenantId, UUID deskId, Collection<UUID> timeslotIds);

    @Modifying
    @Query("DELETE FROM ErlangDemandInput e WHERE e.tenantId = :tenantId AND e.deskId = :deskId " +
           "AND e.timeslotId IN :timeslotIds")
    void deleteByDeskAndTimeslotIds(long tenantId, UUID deskId, Collection<UUID> timeslotIds);
}
