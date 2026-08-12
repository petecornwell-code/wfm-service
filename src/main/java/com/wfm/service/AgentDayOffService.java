package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.AgentDayOffResponse;
import com.wfm.dto.PaginatedResponse;
import com.wfm.model.AgentDayHours;
import com.wfm.model.AgentDayOff;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentDayOffRepository;
import com.wfm.util.CursorPagination;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentDayOffService {

    private final AgentDayOffRepository agentDayOffRepository;
    private final AgentDayHoursRepository agentDayHoursRepository;

    public AgentDayOffService(AgentDayOffRepository agentDayOffRepository,
                              AgentDayHoursRepository agentDayHoursRepository) {
        this.agentDayOffRepository = agentDayOffRepository;
        this.agentDayHoursRepository = agentDayHoursRepository;
    }

    public List<AgentDayOffResponse> listDaysOffForAgent(UUID agentId, String from, String to) {
        long tenantId = TenantContext.getTenantId();

        List<AgentDayOff> daysOff;
        if (from != null && to != null) {
            daysOff = agentDayOffRepository.findByTenantIdAndAgent_IdAndDateBetweenOrderByDateAsc(
                    tenantId, agentId, LocalDate.parse(from), LocalDate.parse(to));
        } else {
            daysOff = agentDayOffRepository.findByTenantIdAndAgent_IdOrderByDateAsc(tenantId, agentId);
        }

        return daysOff.stream()
                .map(d -> AgentDayOffResponse.forAgent(d.getId(), d.getDate(), d.getType().name(), d.getStatus().name()))
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
                .map(d -> AgentDayOffResponse.withAgent(d.getId(), d.getDate(), d.getType().name(), d.getStatus().name(),
                        d.getAgent().getId(), d.getAgent().getName()))
                .toList();

        return CursorPagination.buildPage(responses, clamped,
                r -> Map.of("date", r.date().toString(), "id", r.id().toString()));
    }

    public List<AgentDayOffResponse> listDaysOffForDesk(UUID deskId, String from, String to) {
        long tenantId = TenantContext.getTenantId();
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);

        List<AgentDayOff> daysOff = agentDayOffRepository.findByTenantIdAndDeskIdAndDateBetween(
                tenantId, deskId, fromDate, toDate);

        List<AgentDayOffResponse> result = new ArrayList<>(daysOff.stream()
                .map(d -> AgentDayOffResponse.withAgent(d.getId(), d.getDate(), d.getType().name(), d.getStatus().name(),
                        d.getAgent().getId(), d.getAgent().getName()))
                .toList());

        result.addAll(recurringDaysOff(tenantId, deskId, fromDate, toDate, daysOff));
        result.sort(Comparator.comparing(AgentDayOffResponse::date)
                .thenComparing(r -> r.agent() == null ? "" : r.agent().name()));
        return result;
    }

    /**
     * Expands the recurring MANDATORY/PTO labels captured by the desk-assignment upload
     * (agent_day_hours.day_off_type, keyed by day-of-week) into concrete dates across the
     * requested range.
     *
     * Without this, days off entered on the upload spreadsheet never appeared on the PTO tab:
     * the upload writes only agent_day_hours, while this endpoint read only agent_day_off, which
     * is populated exclusively from BambooHR. The two stores never met (found in UAT 2026-08-12).
     *
     * Expanded on read rather than materialised into agent_day_off rows, so there is no second
     * copy of the same fact to drift, and re-uploading cannot leave orphaned dates behind.
     *
     * A real agent_day_off row always wins for the same (agent, date): it carries an approval
     * status and a genuine id, whereas these are derived.
     */
    private List<AgentDayOffResponse> recurringDaysOff(long tenantId, UUID deskId,
                                                       LocalDate fromDate, LocalDate toDate,
                                                       List<AgentDayOff> existing) {
        List<AgentDayHours> dayHours = agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId).stream()
                .filter(h -> h.getDayOffType() != null)
                .toList();
        if (dayHours.isEmpty()) {
            return List.of();
        }

        Set<String> taken = existing.stream()
                .map(d -> d.getAgent().getId() + "|" + d.getDate())
                .collect(Collectors.toSet());

        List<AgentDayOffResponse> expanded = new ArrayList<>();
        for (AgentDayHours h : dayHours) {
            for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != h.getDayOfWeek()) {
                    continue;
                }
                if (taken.contains(h.getAgent().getId() + "|" + date)) {
                    continue; // a real BambooHR day-off already covers this date
                }
                expanded.add(AgentDayOffResponse.withAgent(
                        h.getId(), date, h.getDayOffType().name(), "APPROVED",
                        h.getAgent().getId(), h.getAgent().getName()));
            }
        }
        return expanded;
    }
}
