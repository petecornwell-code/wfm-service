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

    // No deleteByTenantIdAndDeskId here (unlike this repository's desk-scoped siblings):
    // shift_template's FK is ON DELETE CASCADE at the DB level, so DeskService.deleteDesk
    // relies on the cascade rather than an explicit repository call. See the comment there.
}
