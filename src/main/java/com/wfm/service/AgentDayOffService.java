package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.AgentDayOffResponse;
import com.wfm.dto.PaginatedResponse;
import com.wfm.model.AgentDayOff;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.util.CursorPagination;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentDayOffService {

    private final AgentDayOffRepository agentDayOffRepository;

    public AgentDayOffService(AgentDayOffRepository agentDayOffRepository) {
        this.agentDayOffRepository = agentDayOffRepository;
    }

    public List<AgentDayOffResponse> listDaysOffForAgent(UUID agentId, String from, String to) {
        long tenantId = TenantContext.getTenantId();

        List<AgentDayOff> daysOff;
        if (from != null && to != null) {
            daysOff = agentDayOffRepository.findByTenantIdAndAgent_IdAndDateBetween(
                    tenantId, agentId, LocalDate.parse(from), LocalDate.parse(to));
        } else {
            daysOff = agentDayOffRepository.findByTenantIdAndAgent_Id(tenantId, agentId);
        }

        return daysOff.stream()
                .map(d -> AgentDayOffResponse.forAgent(d.getId(), d.getDate(), d.getType().name()))
                .toList();
    }

    public PaginatedResponse<AgentDayOffResponse> listAllDaysOff(String from, String to,
                                                                   String cursor, int limit) {
        long tenantId = TenantContext.getTenantId();
        int clamped = CursorPagination.clampLimit(limit);
        Pageable pageable = PageRequest.of(0, clamped + 1, Sort.by("date", "id"));

        Map<String, String> cursorValues = CursorPagination.decode(cursor);
        boolean hasCursor = !cursorValues.isEmpty();

        List<AgentDayOff> daysOff;
        if (from != null && to != null) {
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);
            if (hasCursor) {
                LocalDate cursorDate = LocalDate.parse(cursorValues.get("date"));
                UUID cursorId = UUID.fromString(cursorValues.get("id"));
                daysOff = agentDayOffRepository.findByTenantIdAndDateBetweenAfterCursor(
                        tenantId, fromDate, toDate, cursorDate, cursorId, pageable);
            } else {
                daysOff = agentDayOffRepository.findByTenantIdAndDateBetween(
                        tenantId, fromDate, toDate, pageable);
            }
        } else {
            if (hasCursor) {
                LocalDate cursorDate = LocalDate.parse(cursorValues.get("date"));
                UUID cursorId = UUID.fromString(cursorValues.get("id"));
                daysOff = agentDayOffRepository.findByTenantIdAfterCursor(
                        tenantId, cursorDate, cursorId, pageable);
            } else {
                daysOff = agentDayOffRepository.findByTenantId(tenantId, pageable);
            }
        }

        List<AgentDayOffResponse> responses = daysOff.stream()
                .map(d -> AgentDayOffResponse.withAgent(d.getId(), d.getDate(), d.getType().name(),
                        d.getAgent().getId(), d.getAgent().getName()))
                .toList();

        return CursorPagination.buildPage(responses, clamped,
                r -> Map.of("date", r.date().toString(), "id", r.id().toString()));
    }
}
