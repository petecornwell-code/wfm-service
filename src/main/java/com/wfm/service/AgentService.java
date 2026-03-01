package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.AgentResponse;
import com.wfm.dto.PaginatedResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Agent;
import com.wfm.repository.AgentRepository;
import com.wfm.util.CursorPagination;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentService {

    private final AgentRepository agentRepository;

    public AgentService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public PaginatedResponse<AgentResponse> listAgents(String search, boolean unassigned,
                                                        String cursor, int limit) {
        long tenantId = TenantContext.getTenantId();
        int clamped = CursorPagination.clampLimit(limit);
        Pageable pageable = PageRequest.of(0, clamped + 1);

        List<Agent> agents;
        Map<String, String> cursorValues = CursorPagination.decode(cursor);

        if (cursorValues.isEmpty()) {
            agents = agentRepository.findFiltered(tenantId, search, unassigned, pageable);
        } else {
            String cursorName = cursorValues.get("name");
            UUID cursorId = UUID.fromString(cursorValues.get("id"));
            agents = agentRepository.findFilteredAfterCursor(tenantId, search, unassigned,
                    cursorName, cursorId, pageable);
        }

        List<AgentResponse> responses = agents.stream().map(this::toResponse).toList();
        return CursorPagination.buildPage(responses, clamped,
                a -> Map.of("name", a.name(), "id", a.id().toString()));
    }

    public AgentResponse getAgent(UUID agentId) {
        Agent agent = agentRepository.findByIdAndTenantId(agentId, TenantContext.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("Agent", agentId));
        return toResponse(agent);
    }

    private AgentResponse toResponse(Agent a) {
        return new AgentResponse(a.getId(), a.getName(), a.getEmail(),
                a.getDepartment(), a.getJobTitle(), a.isActive(), a.getLastRefreshedAt());
    }
}
