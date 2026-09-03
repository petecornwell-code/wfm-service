package com.wfm.service;

import com.wfm.model.AgentUsualShift;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.ShiftTemplateRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The ONE era-resolution implementation for a stored usual-shift row (D-01/D-02). Called by the
 * roster read ({@code DeskAgentService.toResponse}), the export, and -- Phase 17 -- the drift
 * report.
 *
 * <p>D-01: the stored row carries a real FK, but resolution follows the template's NAME across
 * eras -- reading {@code stored.getShiftTemplate().getName()} and returning whichever era of that
 * name is effective on the date being asked about. This is what makes "Ana's usual shift is
 * Early" follow Early even after the operator edits it into a new era.
 *
 * <p>An empty result here is D-02's "identical to unset" at this layer -- the never-set vs.
 * stored-but-not-in-effect distinction is a display concern the caller computes, not this
 * service. This is deliberately the ONLY implementation of this precedence shape in the codebase
 * -- {@code resolvePreferences} exists twice ({@code SolverService.java}, {@code
 * ScheduleService.java}) as this project's own cautionary, not aspirational, example. Do not
 * create a second copy of this method.
 */
@Service
public class UsualShiftResolutionService {

    private final ShiftTemplateRepository shiftTemplateRepository;

    public UsualShiftResolutionService(ShiftTemplateRepository shiftTemplateRepository) {
        this.shiftTemplateRepository = shiftTemplateRepository;
    }

    public Optional<ShiftTemplate> resolve(AgentUsualShift stored, LocalDate date) {
        if (stored == null) {
            return Optional.empty();
        }
        ShiftTemplate storedTemplate = stored.getShiftTemplate();
        long tenantId = storedTemplate.getTenantId();
        UUID deskId = storedTemplate.getDeskId();
        String name = storedTemplate.getName();

        List<ShiftTemplate> eras =
                shiftTemplateRepository.findByTenantIdAndDeskIdAndName(tenantId, deskId, name);
        return eras.stream()
                .filter(t -> t.isEffectiveOn(date))
                .sorted(Comparator.comparing(ShiftTemplate::getEffectiveFrom).reversed()
                        .thenComparing(ShiftTemplate::getId))
                .findFirst();
    }
}
