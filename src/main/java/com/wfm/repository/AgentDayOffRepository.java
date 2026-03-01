package com.wfm.repository;

import com.wfm.model.AgentDayOff;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgentDayOffRepository extends JpaRepository<AgentDayOff, UUID> {

    List<AgentDayOff> findByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    List<AgentDayOff> findByTenantIdAndAgent_IdAndDateBetween(
            long tenantId, UUID agentId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"agent"})
    List<AgentDayOff> findByTenantId(long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"agent"})
    List<AgentDayOff> findByTenantIdAndDateBetween(long tenantId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT d FROM AgentDayOff d JOIN FETCH d.agent WHERE d.tenantId = :tenantId " +
           "AND (d.date > :cursorDate OR (d.date = :cursorDate AND d.id > :cursorId)) " +
           "ORDER BY d.date, d.id")
    List<AgentDayOff> findByTenantIdAfterCursor(long tenantId, LocalDate cursorDate, UUID cursorId, Pageable pageable);

    @Query("SELECT d FROM AgentDayOff d JOIN FETCH d.agent WHERE d.tenantId = :tenantId " +
           "AND d.date BETWEEN :from AND :to " +
           "AND (d.date > :cursorDate OR (d.date = :cursorDate AND d.id > :cursorId)) " +
           "ORDER BY d.date, d.id")
    List<AgentDayOff> findByTenantIdAndDateBetweenAfterCursor(long tenantId, LocalDate from, LocalDate to,
                                                                LocalDate cursorDate, UUID cursorId, Pageable pageable);

    void deleteByAgent_IdAndDateBetween(UUID agentId, LocalDate from, LocalDate to);
}
