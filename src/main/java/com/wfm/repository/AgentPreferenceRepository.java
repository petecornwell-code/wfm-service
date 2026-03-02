package com.wfm.repository;

import com.wfm.model.AgentPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentPreferenceRepository extends JpaRepository<AgentPreference, UUID> {

    List<AgentPreference> findByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    List<AgentPreference> findByTenantIdAndDeskIdAndAgent_IdAndDateBetween(
            long tenantId, UUID deskId, UUID agentId, LocalDate from, LocalDate to);

    List<AgentPreference> findByTenantIdAndDeskIdAndAgent_IdAndIsStandingTrue(
            long tenantId, UUID deskId, UUID agentId);

    List<AgentPreference> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    void deleteByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);

    List<AgentPreference> findByTenantIdAndDeskIdAndAgent_IdAndIsStandingTrueAndDayOfWeek(
            long tenantId, UUID deskId, UUID agentId, java.time.DayOfWeek dayOfWeek);

    java.util.Optional<AgentPreference> findByTenantIdAndDeskIdAndAgent_IdAndIsStandingFalseAndDate(
            long tenantId, UUID deskId, UUID agentId, LocalDate date);
}
