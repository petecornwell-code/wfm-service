package com.wfm.repository;

import com.wfm.model.Specialization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpecializationRepository extends JpaRepository<Specialization, UUID> {

    List<Specialization> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    Optional<Specialization> findByIdAndTenantIdAndDeskId(UUID id, long tenantId, UUID deskId);

    boolean existsByTenantIdAndDeskIdAndName(long tenantId, UUID deskId, String name);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
