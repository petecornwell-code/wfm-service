package com.wfm.repository;

import com.wfm.model.ShiftTemplateBreakBand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Every method takes {@code tenantId} explicitly and filters on it -- the universal convention
 * across every repository in this codebase, and the mitigation for T-15-01. There is no method
 * here that reads bands without a tenant parameter.
 */
@Repository
public interface ShiftTemplateBreakBandRepository extends JpaRepository<ShiftTemplateBreakBand, UUID> {

    List<ShiftTemplateBreakBand> findByTenantIdAndShiftTemplateIdOrderByOffsetMinutesAsc(
            long tenantId, UUID shiftTemplateId);

    // Bulk list rendering / bulk validation reads (D-08's single-pass load per validate() call).
    List<ShiftTemplateBreakBand> findByTenantIdAndShiftTemplateIdInOrderByOffsetMinutesAsc(
            long tenantId, Collection<UUID> shiftTemplateIds);

    // Not tenant-scoped, mirroring AgentDayHoursRepository.deleteByAgent_Id -- callers must
    // resolve tenant scope (via ShiftTemplateRepository) before calling this.
    void deleteByShiftTemplate_Id(UUID shiftTemplateId);
}
