package com.wfm.repository;

import com.wfm.model.DeskAgent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeskAgentRepository extends JpaRepository<DeskAgent, UUID> {

    List<DeskAgent> findByTenantIdAndDeskId(long tenantId, UUID deskId, Pageable pageable);

    List<DeskAgent> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    Optional<DeskAgent> findByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    Optional<DeskAgent> findByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    boolean existsByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);

    long countByTenantIdAndDeskId(long tenantId, UUID deskId);
}
