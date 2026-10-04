package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.RestWaiverResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Agent;
import com.wfm.model.AgentRestWaiver;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.AgentRestWaiverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * List, upsert and delete for {@link AgentRestWaiver} rows (REST-06, D-07). Copies
 * {@link AgentExceptionService}'s tenant read, three-key agent resolution, required-field checks
 * and upsert-by-natural-key block.
 *
 * <p>Two deliberate omissions from the {@code AgentExceptionService} shape this class otherwise
 * mirrors:
 *
 * <ol>
 *   <li>There is no {@code contractedHoursOverride} check, because {@link AgentRestWaiver} has no
 *       such field (D-07).
 *   <li>There is no day-off coincidence refusal. {@code AgentExceptionService} refuses an exception
 *       on a date the agent has off; a rest waiver on a day off is harmless, because there is no
 *       shift to rest from, and inheriting that refusal is one of the two measured reasons D-07
 *       chose a new table over widening {@code agent_exception}. D-09 already decided an inert
 *       waiver is reported as unused rather than refused at save time, so refusing the
 *       knowably-inert day-off case here would contradict it. This is why this service has no
 *       dependency on any day-off-reading collaborator at all.
 * </ol>
 */
@Service
public class RestWaiverService {

    private final AgentRestWaiverRepository agentRestWaiverRepository;
    private final AgentRepository agentRepository;

    public RestWaiverService(AgentRestWaiverRepository agentRestWaiverRepository,
                              AgentRepository agentRepository) {
        this.agentRestWaiverRepository = agentRestWaiverRepository;
        this.agentRepository = agentRepository;
    }

    public List<RestWaiverResponse> listWaivers(UUID deskId, UUID agentId, String from, String to) {
        long tenantId = TenantContext.getTenantId();

        List<AgentRestWaiver> waivers;
        if (from != null && to != null) {
            waivers = agentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_IdAndDateBetween(
                    tenantId, deskId, agentId, LocalDate.parse(from), LocalDate.parse(to));
        } else {
            waivers = agentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_Id(
                    tenantId, deskId, agentId);
        }

        return waivers.stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<RestWaiverResponse> saveWaivers(UUID deskId, UUID agentId,
                                                 List<RestWaiverResponse> waivers) {
        long tenantId = TenantContext.getTenantId();

        Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

        List<AgentRestWaiver> saved = new ArrayList<>();
        for (RestWaiverResponse waiver : waivers) {
            if (waiver.date() == null) {
                throw new IllegalArgumentException("date is required for each rest waiver");
            }
            if (waiver.reason() == null || waiver.reason().isBlank()) {
                throw new IllegalArgumentException("reason is required");
            }

            // No day-off coincidence refusal here, deliberately — see class javadoc. A rest
            // waiver on a date the agent has off is harmless because there is no shift to rest
            // from, and that refusal is one of the two measured reasons D-07 chose a new table
            // over widening agent_exception.

            Optional<AgentRestWaiver> existing = agentRestWaiverRepository
                    .findByTenantIdAndDeskIdAndAgent_IdAndDate(tenantId, deskId, agentId, waiver.date());

            AgentRestWaiver entity;
            if (existing.isPresent()) {
                entity = existing.get();
            } else {
                entity = new AgentRestWaiver();
                entity.setTenantId(tenantId);
                entity.setDeskId(deskId);
                entity.setAgent(agent);
                entity.setDate(waiver.date());
            }

            entity.setReason(waiver.reason());
            saved.add(agentRestWaiverRepository.save(entity));
        }

        return saved.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteWaiver(UUID deskId, UUID agentId, LocalDate date) {
        long tenantId = TenantContext.getTenantId();

        AgentRestWaiver waiver = agentRestWaiverRepository
                .findByTenantIdAndDeskIdAndAgent_IdAndDate(tenantId, deskId, agentId, date)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Rest waiver not found for agent " + agentId + " on " + date));

        agentRestWaiverRepository.delete(waiver);
    }

    private RestWaiverResponse toResponse(AgentRestWaiver w) {
        return new RestWaiverResponse(w.getId(), w.getDate(), w.getReason());
    }
}
