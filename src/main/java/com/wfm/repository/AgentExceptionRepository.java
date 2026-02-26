package com.wfm.repository;

import com.wfm.model.AgentException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentExceptionRepository extends JpaRepository<AgentException, UUID> {

    List<AgentException> findByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    List<AgentException> findByTenantIdAndDeskIdAndAgent_IdAndDateBetween(
            long tenantId, UUID deskId, UUID agentId, LocalDate from, LocalDate to);

    Optional<AgentException> findByTenantIdAndDeskIdAndAgent_IdAndDate(
            long tenantId, UUID deskId, UUID agentId, LocalDate date);

    List<AgentException> findByTenantIdAndDeskIdAndDateBetween(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    void deleteByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);
}
