package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Agent;
import com.wfm.model.AgentUsualShift;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.repository.ShiftTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The single choke-point write for an agent's usual shift on one weekday (ROADMAP success
 * criterion 3). Mirrors {@code DeskAgentService.setDayHours}'s shape: tenant+desk-scoped agent
 * resolution before any repository call (T-13-05/T-16-01), reject-not-clamp validation, a
 * reuse-or-create upsert, and an explicit {@code flush()} so the write is correct in isolation.
 *
 * <p>Per P-06 this service injects no other service -- {@code DeskAgentController} composes the
 * write with a subsequent read via {@code DeskAgentService.getDeskAgentResponse}, so the
 * dependency graph stays acyclic once plan 16-02 adds {@code DeskAgentService ->
 * UsualShiftService}.
 */
@Service
public class UsualShiftService {

    private final AgentRepository agentRepository;
    private final AgentUsualShiftRepository agentUsualShiftRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;

    public UsualShiftService(AgentRepository agentRepository,
                              AgentUsualShiftRepository agentUsualShiftRepository,
                              ShiftTemplateRepository shiftTemplateRepository) {
        this.agentRepository = agentRepository;
        this.agentUsualShiftRepository = agentUsualShiftRepository;
        this.shiftTemplateRepository = shiftTemplateRepository;
    }

    @Transactional
    public void setUsualShift(UUID deskId, UUID agentId, DayOfWeek day, UUID shiftTemplateId, boolean clearRow) {
        long tenantId = TenantContext.getTenantId();

        // Mandatory access-control step (T-13-05/T-16-01): resolve the agent within tenant+desk
        // scope BEFORE any AgentUsualShiftRepository call -- findByAgent_IdAndDayOfWeek accepts a
        // raw agent id and would otherwise be an IDOR.
        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        if (clearRow) {
            agentUsualShiftRepository.findByAgent_IdAndDayOfWeek(agentId, day)
                    .ifPresent(agentUsualShiftRepository::delete);
        } else if (shiftTemplateId == null) {
            throw new IllegalArgumentException("Must provide shiftTemplateId or clearRow");
        } else {
            // T-16-02: cross-tenant/cross-desk template-reference guard -- never findById.
            ShiftTemplate template = shiftTemplateRepository
                    .findByIdAndTenantIdAndDeskId(shiftTemplateId, tenantId, deskId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Shift template not found for desk: " + shiftTemplateId));

            // P-03: a dead template cannot be stored through the choke point. D-02 governs a row
            // that was live when written and was retired later; it does not license deliberately
            // writing an already-dead target. The D-17 picker only offers CURRENT eras, so this
            // is the defensive server-side half of the same rule.
            if (!template.isEffectiveOn(LocalDate.now())) {
                throw new IllegalArgumentException(
                        "Shift template is not currently effective: " + template.getName());
            }

            // D-03 deliberately diverges from Phase 14's D-06 advisory-on-save precedent: a
            // weekday-mask violation is a flat contradiction with a single-field fix that is
            // knowable at pick time, whereas a contracted-hours mismatch (D-06) is a moving
            // judgement about a library the operator is still shaping. Reject, do not clamp --
            // same posture DeskAgentService.setDayHours takes on out-of-range hours.
            if (!template.getValidWeekdays().contains(day)) {
                throw new IllegalArgumentException(
                        "Shift template '" + template.getName() + "' is not valid on " + day);
            }

            upsertUsualShiftRow(agent, day, template);
        }

        // Flush so the write is correct in isolation even though the controller reads in a later
        // transaction (P-06) -- keep this even without an in-method re-read, matching
        // setDayHours's own discipline.
        agentUsualShiftRepository.flush();
    }

    /**
     * Reuses the existing row for (agent, day) if present, otherwise constructs a fresh one --
     * never a second row for a weekday that already has one (the unique constraint on
     * (agent_id, day_of_week) is the backstop).
     */
    private void upsertUsualShiftRow(Agent agent, DayOfWeek day, ShiftTemplate template) {
        AgentUsualShift row = agentUsualShiftRepository.findByAgent_IdAndDayOfWeek(agent.getId(), day)
                .orElseGet(AgentUsualShift::new);
        row.setTenantId(agent.getTenantId());
        row.setAgent(agent);
        row.setDayOfWeek(day);
        row.setShiftTemplate(template);
        agentUsualShiftRepository.save(row);
    }

    /**
     * The ONE clear-usual-shifts implementation (D-11/D-12, Phase 14's D-08 discipline). Plan
     * 16-03 calls this from {@code DeskAssignmentUploadService.clearDesk} and plan 16-02 calls
     * this from {@code DeskAgentService.removeDeskAgent}. A no-op when the agent has no rows.
     */
    @Transactional
    public void clearUsualShifts(UUID agentId) {
        agentUsualShiftRepository.deleteByAgent_Id(agentId);
    }
}
