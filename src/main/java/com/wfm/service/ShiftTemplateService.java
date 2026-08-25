package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.exception.ConflictException;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.ShiftTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Create/list slice for the shift template library (SHLB-01). Full field validation, the grid
 * check (D-02) and the effective-range non-overlap rule (D-11 — checkpoint decision: app-level,
 * recorded in 14-01-SUMMARY.md) are 14-03's work; this service does only the minimum this
 * tracer slice needs — reject a missing name and reject a duplicate identity key.
 */
@Service
public class ShiftTemplateService {

    private final ShiftTemplateRepository shiftTemplateRepository;

    public ShiftTemplateService(ShiftTemplateRepository shiftTemplateRepository) {
        this.shiftTemplateRepository = shiftTemplateRepository;
    }

    public List<ShiftTemplate> listShiftTemplates(UUID deskId) {
        return shiftTemplateRepository.findByTenantIdAndDeskId(TenantContext.getTenantId(), deskId);
    }

    @Transactional
    public ShiftTemplate createShiftTemplate(UUID deskId, ShiftTemplateRequest request) {
        long tenantId = TenantContext.getTenantId();

        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Shift template name is required");
        }
        if (shiftTemplateRepository.existsByTenantIdAndDeskIdAndNameAndEffectiveFrom(
                tenantId, deskId, request.name(), request.effectiveFrom())) {
            throw new ConflictException("A shift template with name '" + request.name()
                    + "' already exists for this desk starting " + request.effectiveFrom());
        }

        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(tenantId);
        template.setDeskId(deskId);
        template.setName(request.name());
        template.setStartTime(request.startTime());
        template.setEndTime(request.endTime());
        template.setBreakOffsetMinutes(request.breakOffsetMinutes() != null ? request.breakOffsetMinutes() : 0);
        template.setBreakDurationMinutes(request.breakDurationMinutes() != null ? request.breakDurationMinutes() : 0);
        template.setValidWeekdays(request.validWeekdays());
        template.setEffectiveFrom(request.effectiveFrom());
        template.setEffectiveTo(request.effectiveTo());
        return shiftTemplateRepository.save(template);
    }
}
