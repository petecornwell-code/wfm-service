package com.wfm.repository;

import com.wfm.model.DeskAgent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeskAgentRepository extends JpaRepository<DeskAgent, UUID> {

    @EntityGraph(attributePaths = {"agent", "primarySpecialization", "secondarySpecializations"})
    List<DeskAgent> findByTenantIdAndDeskId(long tenantId, UUID deskId, Pageable pageable);

    @EntityGraph(attributePaths = {"agent", "primarySpecialization", "secondarySpecializations"})
    List<DeskAgent> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    Optional<DeskAgent> findByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    Optional<DeskAgent> findByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    boolean existsByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);

    long countByTenantIdAndDeskId(long tenantId, UUID deskId);

    @Query("SELECT CASE WHEN COUNT(da) > 0 THEN true ELSE false END FROM DeskAgent da " +
           "WHERE da.primarySpecialization.id = :specId")
    boolean existsByPrimarySpecialization_Id(UUID specId);

    @Query("SELECT CASE WHEN COUNT(da) > 0 THEN true ELSE false END FROM DeskAgent da " +
           "JOIN da.secondarySpecializations s WHERE s.id = :specId")
    boolean existsBySecondarySpecializationsContaining(UUID specId);
}
