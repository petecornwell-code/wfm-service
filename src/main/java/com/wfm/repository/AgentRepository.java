package com.wfm.repository;

import com.wfm.model.Agent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
