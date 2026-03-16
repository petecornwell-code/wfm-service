package com.wfm.repository;

import com.wfm.model.Agent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    List<Agent> findByTenantId(long tenantId, Pageable pageable);

    Optional<Agent> findByIdAndTenantId(UUID id, long tenantId);

    Optional<Agent> findByTenantIdAndBamboohrId(long tenantId, String bamboohrId);

    List<Agent> findByTenantIdAndNameContainingIgnoreCase(long tenantId, String name, Pageable pageable);

    @Query("SELECT a FROM Agent a WHERE a.tenantId = :tenantId " +
           "AND (:search IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:unassigned = false OR NOT EXISTS (SELECT 1 FROM DeskAgent da WHERE da.agent = a)) " +
           "ORDER BY a.name, a.id")
    List<Agent> findFiltered(long tenantId, String search, boolean unassigned, Pageable pageable);

    @Query("SELECT a FROM Agent a WHERE a.tenantId = :tenantId " +
           "AND (:search IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:unassigned = false OR NOT EXISTS (SELECT 1 FROM DeskAgent da WHERE da.agent = a)) " +
           "AND (a.name > :cursorName OR (a.name = :cursorName AND a.id > :cursorId)) " +
           "ORDER BY a.name, a.id")
    List<Agent> findFilteredAfterCursor(long tenantId, String search, boolean unassigned,
                                         String cursorName, UUID cursorId, Pageable pageable);

    List<Agent> findAllByIdInAndTenantId(List<UUID> ids, long tenantId);

    Optional<Agent> findByTenantIdAndEmailIgnoreCase(long tenantId, String email);
}
