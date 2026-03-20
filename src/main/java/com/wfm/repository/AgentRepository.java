package com.wfm.repository;

import com.wfm.model.Agent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
           "AND (:unassigned = false OR a.deskId IS NULL) " +
           "ORDER BY a.name, a.id")
    List<Agent> findFiltered(long tenantId, String search, boolean unassigned, Pageable pageable);

    @Query("SELECT a FROM Agent a WHERE a.tenantId = :tenantId " +
           "AND (:search IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:unassigned = false OR a.deskId IS NULL) " +
           "AND (a.name > :cursorName OR (a.name = :cursorName AND a.id > :cursorId)) " +
           "ORDER BY a.name, a.id")
    List<Agent> findFilteredAfterCursor(long tenantId, String search, boolean unassigned,
                                         String cursorName, UUID cursorId, Pageable pageable);

    List<Agent> findAllByIdInAndTenantId(List<UUID> ids, long tenantId);

    Optional<Agent> findByTenantIdAndEmailIgnoreCase(long tenantId, String email);

    // --- Desk-scoped queries (formerly on DeskAgentRepository) ---

    @EntityGraph(attributePaths = {"primarySpecialization", "secondarySpecializations"})
    List<Agent> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    @EntityGraph(attributePaths = {"primarySpecialization", "secondarySpecializations"})
    Optional<Agent> findByIdAndTenantIdAndDeskId(UUID id, long tenantId, UUID deskId);

    long countByTenantIdAndDeskId(long tenantId, UUID deskId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Agent a " +
           "WHERE a.primarySpecialization.id = :specId")
    boolean existsByPrimarySpecialization_Id(UUID specId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Agent a " +
           "JOIN a.secondarySpecializations s WHERE s.id = :specId")
    boolean existsBySecondarySpecializationsContaining(UUID specId);
}
