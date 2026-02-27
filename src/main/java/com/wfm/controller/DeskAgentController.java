package com.wfm.controller;

import com.wfm.dto.DeskAgentResponse;
import com.wfm.dto.PaginatedResponse;
import com.wfm.model.AgentPreference;
import com.wfm.model.DeskAgent;
import com.wfm.integration.BambooRefreshService;
import com.wfm.service.AgentExceptionService;
import com.wfm.service.AgentPreferenceService;
import com.wfm.service.DeskAgentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/agents")
public class DeskAgentController {

    private final DeskAgentService deskAgentService;
    private final AgentPreferenceService agentPreferenceService;
    private final AgentExceptionService agentExceptionService;
    private final BambooRefreshService bambooRefreshService;

    public DeskAgentController(DeskAgentService deskAgentService,
                               AgentPreferenceService agentPreferenceService,
                               AgentExceptionService agentExceptionService,
                               BambooRefreshService bambooRefreshService) {
        this.deskAgentService = deskAgentService;
        this.agentPreferenceService = agentPreferenceService;
        this.agentExceptionService = agentExceptionService;
        this.bambooRefreshService = bambooRefreshService;
    }

    @GetMapping
    public PaginatedResponse<DeskAgentResponse> listDeskAgents(@PathVariable UUID deskId,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false, defaultValue = "50") int limit) {
        List<DeskAgentResponse> agents = deskAgentService.listDeskAgentResponses(deskId, search, cursor, limit);
        return new PaginatedResponse<>(agents, null, false);
    }

    @PostMapping
    public ResponseEntity<List<DeskAgent>> assignAgents(@PathVariable UUID deskId,
                                                        @RequestBody Map<String, List<UUID>> body) {
        List<DeskAgent> assigned = deskAgentService.assignAgents(deskId, body.get("agentIds"));
        return ResponseEntity.status(HttpStatus.CREATED).body(assigned);
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> removeDeskAgent(@PathVariable UUID deskId, @PathVariable UUID agentId) {
        deskAgentService.removeDeskAgent(deskId, agentId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{agentId}/specializations")
    public ResponseEntity<DeskAgent> setSpecializations(@PathVariable UUID deskId,
                                                        @PathVariable UUID agentId,
                                                        @RequestBody Map<String, Object> body) {
        // TODO: parse primarySpecializationId and secondarySpecializationIds from body
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{agentId}/contracted-hours")
    public ResponseEntity<DeskAgent> setContractedHours(@PathVariable UUID deskId,
                                                        @PathVariable UUID agentId,
                                                        @RequestBody Map<String, Object> body) {
        // TODO: parse contractedHoursPerDay from body
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public List<DeskAgentResponse> refreshFromBamboo(@PathVariable UUID deskId) {
        bambooRefreshService.refreshDeskAgents(deskId);
        return deskAgentService.listDeskAgentResponses(deskId, null, null, 500);
    }

    // --- Preferences ---

    @GetMapping("/{agentId}/preferences")
    public List<AgentPreference> listPreferences(@PathVariable UUID deskId,
                                                 @PathVariable UUID agentId,
                                                 @RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return agentPreferenceService.listPreferences(deskId, agentId, from, to);
    }

    @PutMapping("/{agentId}/preferences")
    public List<AgentPreference> savePreferences(@PathVariable UUID deskId,
                                                 @PathVariable UUID agentId,
                                                 @RequestBody List<AgentPreference> preferences) {
        return agentPreferenceService.savePreferences(deskId, agentId, preferences);
    }

    @DeleteMapping("/{agentId}/preferences/{preferenceId}")
    public ResponseEntity<Void> deletePreference(@PathVariable UUID deskId,
                                                  @PathVariable UUID agentId,
                                                  @PathVariable UUID preferenceId) {
        agentPreferenceService.deletePreference(deskId, agentId, preferenceId);
        return ResponseEntity.noContent().build();
    }

    // --- Exceptions ---

    @GetMapping("/{agentId}/exceptions")
    public List<?> listExceptions(@PathVariable UUID deskId,
                                  @PathVariable UUID agentId,
                                  @RequestParam(required = false) String from,
                                  @RequestParam(required = false) String to) {
        return agentExceptionService.listExceptions(deskId, agentId, from, to);
    }

    @PutMapping("/{agentId}/exceptions")
    public List<?> saveExceptions(@PathVariable UUID deskId,
                                  @PathVariable UUID agentId,
                                  @RequestBody List<com.wfm.model.AgentException> exceptions) {
        return agentExceptionService.saveExceptions(deskId, agentId, exceptions);
    }

    @DeleteMapping("/{agentId}/exceptions/{date}")
    public ResponseEntity<Void> deleteException(@PathVariable UUID deskId,
                                                 @PathVariable UUID agentId,
                                                 @PathVariable LocalDate date) {
        agentExceptionService.deleteException(deskId, agentId, date);
        return ResponseEntity.noContent().build();
    }
}
