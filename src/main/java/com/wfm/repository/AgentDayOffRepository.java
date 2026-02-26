package com.wfm.repository;

import com.wfm.model.AgentDayOff;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentDayOffRepository extends JpaRepository<AgentDayOff, UUID> {

    List<AgentDayOff> findByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    List<AgentDayOff> findByTenantIdAndAgent_IdAndDateBetween(
            long tenantId, UUID agentId, LocalDate from, LocalDate to);

    List<AgentDayOff> findByTenantId(long tenantId, Pageable pageable);

    List<AgentDayOff> findByTenantIdAndDateBetween(long tenantId, LocalDate from, LocalDate to, Pageable pageable);

    void deleteByAgent_IdAndDateBetween(UUID agentId, LocalDate from, LocalDate to);
}
