package com.wfm.repository;

import com.wfm.model.Desk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeskRepository extends JpaRepository<Desk, UUID> {

    List<Desk> findByTenantId(long tenantId);

    Optional<Desk> findByIdAndTenantId(UUID id, long tenantId);

    boolean existsByTenantIdAndName(long tenantId, String name);
}
