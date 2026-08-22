package com.wfm.controller;

import com.wfm.dto.*;
import com.wfm.integration.BambooRefreshService;
import com.wfm.service.AgentExceptionService;
import com.wfm.service.AgentPreferenceService;
import com.wfm.service.DeskAgentExportService;
import com.wfm.service.DeskAgentService;
import com.wfm.service.PreferenceUploadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/agents")
public class DeskAgentController {

    private final DeskAgentService deskAgentService;
    private final DeskAgentExportService deskAgentExportService;
    private final AgentPreferenceService agentPreferenceService;
    private final AgentExceptionService agentExceptionService;
    private final BambooRefreshService bambooRefreshService;
    private final PreferenceUploadService preferenceUploadService;

    public DeskAgentController(DeskAgentService deskAgentService,
                               DeskAgentExportService deskAgentExportService,
                               AgentPreferenceService agentPreferenceService,
                               AgentExceptionService agentExceptionService,
                               BambooRefreshService bambooRefreshService,
                               PreferenceUploadService preferenceUploadService) {
        this.deskAgentService = deskAgentService;
        this.deskAgentExportService = deskAgentExportService;
        this.agentPreferenceService = agentPreferenceService;
        this.agentExceptionService = agentExceptionService;
        this.bambooRefreshService = bambooRefreshService;
        this.preferenceUploadService = preferenceUploadService;
    }

    @GetMapping
    public PaginatedResponse<DeskAgentResponse> listDeskAgents(@PathVariable UUID deskId,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false, defaultValue = "50") int limit) {
        List<DeskAgentResponse> agents = deskAgentService.listDeskAgentResponses(deskId, search, cursor, limit);
        return new PaginatedResponse<>(agents, null, false, agents.size());
    }

    @PostMapping
    public ResponseEntity<List<DeskAgentResponse>> assignAgents(@PathVariable UUID deskId,
                                                                 @RequestBody AssignAgentsRequest request) {
        List<DeskAgentResponse> assigned = deskAgentService.assignAgents(deskId, request.agentIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(assigned);
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> removeDeskAgent(@PathVariable UUID deskId, @PathVariable UUID agentId) {
        deskAgentService.removeDeskAgent(deskId, agentId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{agentId}/specializations")
    public DeskAgentResponse setSpecializations(@PathVariable UUID deskId,
                                                 @PathVariable UUID agentId,
                                                 @RequestBody SetSpecializationsRequest request) {
        return deskAgentService.setSpecializations(deskId, agentId,
                request.primarySpecializationId(), request.secondarySpecializationIds());
    }

    @PutMapping("/{agentId}/contracted-hours")
    public DeskAgentResponse setContractedHours(@PathVariable UUID deskId,
                                                 @PathVariable UUID agentId,
                                                 @RequestBody SetContractedHoursRequest request) {
        return deskAgentService.setContractedHours(deskId, agentId, request.contractedHoursPerDay());
    }

    @PutMapping("/{agentId}/day-hours/{day}")
    public DeskAgentResponse setDayHours(@PathVariable UUID deskId,
                                          @PathVariable UUID agentId,
                                          @PathVariable DayOfWeek day,
                                          @RequestBody SetDayHoursRequest request) {
        return deskAgentService.setDayHours(deskId, agentId, day,
                request.hours(), request.dayOffType(), Boolean.TRUE.equals(request.clearRow()));
    }

    @PostMapping("/refresh")
    public List<DeskAgentResponse> refreshFromBamboo(@PathVariable UUID deskId) {
        bambooRefreshService.refreshDeskAgents(deskId);
        return deskAgentService.listDeskAgentResponses(deskId, null, null, 500);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDeskAgents(@PathVariable UUID deskId) {
        List<DeskAgentResponse> agents = deskAgentService.listDeskAgentResponses(deskId, null, null, Integer.MAX_VALUE);
        byte[] xlsx = deskAgentExportService.exportDeskAgentsToExcel(agents);

        String filename = "desk-agents-" + deskId + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    // --- Preferences ---

    @GetMapping("/{agentId}/preferences")
    public List<PreferenceResponse> listPreferences(@PathVariable UUID deskId,
                                                     @PathVariable UUID agentId,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        return agentPreferenceService.listPreferences(deskId, agentId, from, to);
    }

    @PutMapping("/{agentId}/preferences")
    public List<PreferenceResponse> savePreferences(@PathVariable UUID deskId,
                                                     @PathVariable UUID agentId,
                                                     @RequestBody List<PreferenceResponse> preferences) {
        return agentPreferenceService.savePreferences(deskId, agentId, preferences);
    }

    @DeleteMapping("/{agentId}/preferences/{preferenceId}")
    public ResponseEntity<Void> deletePreference(@PathVariable UUID deskId,
                                                  @PathVariable UUID agentId,
                                                  @PathVariable UUID preferenceId) {
        agentPreferenceService.deletePreference(deskId, agentId, preferenceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/preferences/upload")
    public PreferenceUploadService.PreferenceUploadResult uploadPreferences(
            @PathVariable UUID deskId,
            @RequestParam("file") MultipartFile file) throws java.io.IOException {
        return preferenceUploadService.uploadPreferences(deskId, file);
    }

    // --- Exceptions ---

    @GetMapping("/{agentId}/exceptions")
    public List<ExceptionResponse> listExceptions(@PathVariable UUID deskId,
                                                   @PathVariable UUID agentId,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to) {
        return agentExceptionService.listExceptions(deskId, agentId, from, to);
    }

    @PutMapping("/{agentId}/exceptions")
    public List<ExceptionResponse> saveExceptions(@PathVariable UUID deskId,
                                                   @PathVariable UUID agentId,
                                                   @RequestBody List<ExceptionResponse> exceptions) {
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
