package com.wfm.repository;

import com.wfm.model.ShiftTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, UUID> {

    List<ShiftTemplate> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    Optional<ShiftTemplate> findByIdAndTenantIdAndDeskId(UUID id, long tenantId, UUID deskId);

    boolean existsByTenantIdAndDeskIdAndNameAndEffectiveFrom(long tenantId, UUID deskId, String name, LocalDate effectiveFrom);

    List<ShiftTemplate> findByTenantIdAndDeskIdAndName(long tenantId, UUID deskId, String name);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
