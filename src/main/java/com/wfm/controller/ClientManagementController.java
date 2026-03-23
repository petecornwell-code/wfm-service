package com.wfm.controller;

import com.wfm.config.TenantContext;
import com.wfm.dto.AgentResponse;
import com.wfm.dto.AssignEmployeesToDeskRequest;
import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.dto.PaginatedResponse;
import com.wfm.service.ClientManagementService;
import com.wfm.service.DeskAgentService;
import com.wfm.service.DeskAssignmentUploadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/client-management")
public class ClientManagementController {

    private final ClientManagementService clientManagementService;
    private final DeskAgentService deskAgentService;
    private final DeskAssignmentUploadService deskAssignmentUploadService;

    public ClientManagementController(ClientManagementService clientManagementService,
                                       DeskAgentService deskAgentService,
                                       DeskAssignmentUploadService deskAssignmentUploadService) {
        this.clientManagementService = clientManagementService;
        this.deskAgentService = deskAgentService;
        this.deskAssignmentUploadService = deskAssignmentUploadService;
    }

    @GetMapping("/employees")
    public PaginatedResponse<BambooEmployeeResponse> listEmployees(
            @RequestParam String department,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {

        String tenantId = String.valueOf(TenantContext.getTenantId());
        List<BambooEmployeeResponse> all = clientManagementService.listEmployeesByDepartment(tenantId, department, refresh);

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, all.size());
        List<BambooEmployeeResponse> pageData = start < all.size() ? all.subList(start, end) : List.of();
        boolean hasMore = end < all.size();

        return new PaginatedResponse<>(pageData, hasMore ? String.valueOf(page + 1) : null, hasMore, all.size());
    }

    @PostMapping("/assign-to-desk")
    public ResponseEntity<List<AgentResponse>> assignEmployeesToDesk(
            @RequestBody AssignEmployeesToDeskRequest request) {
        long tenantId = TenantContext.getTenantId();
        List<AgentResponse> assigned = clientManagementService.assignEmployeesToDesk(
                tenantId, request.deskId(), request.bambooEmployeeIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(assigned);
    }

    @DeleteMapping("/desks/{deskId}/agents/{agentId}")
    public ResponseEntity<Void> removeAgentFromDesk(@PathVariable UUID deskId,
                                                     @PathVariable UUID agentId) {
        deskAgentService.removeDeskAgent(deskId, agentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload-desk-assignments")
    public DeskAssignmentUploadService.DeskAssignmentUploadResult uploadDeskAssignments(
            @RequestParam("file") MultipartFile file) throws IOException {
        return deskAssignmentUploadService.uploadDeskAssignments(file);
    }

    @GetMapping("/export-desk-assignments")
    public ResponseEntity<byte[]> exportDeskAssignments(
            @RequestParam(required = false) String department) {
        long tenantId = TenantContext.getTenantId();
        byte[] xlsx = deskAssignmentUploadService.exportDeskAssignments(
                tenantId, department, clientManagementService);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "desk_assignments.xlsx");
        return new ResponseEntity<>(xlsx, headers, HttpStatus.OK);
    }
}
