package com.wfm.controller;

import com.wfm.config.TenantContext;
import com.wfm.dto.AgentResponse;
import com.wfm.dto.DeskAssignmentSelectionRequest;
import com.wfm.dto.AssignEmployeesToDeskRequest;
import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.dto.DepartmentSummary;
import com.wfm.dto.DepartmentTimeOffResponse;
import com.wfm.dto.EmployeeSearchResponse;
import com.wfm.dto.PaginatedResponse;
import com.wfm.service.ClientManagementExportService;
import com.wfm.service.ClientManagementService;
import com.wfm.service.DeskAgentService;
import com.wfm.service.DeskAssignmentTemplateService;
import com.wfm.service.DeskAssignmentUploadService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/client-management")
public class ClientManagementController {

    private final ClientManagementService clientManagementService;
    private final ClientManagementExportService clientManagementExportService;
    private final DeskAgentService deskAgentService;
    private final DeskAssignmentUploadService deskAssignmentUploadService;
    private final DeskAssignmentTemplateService deskAssignmentTemplateService;

    public ClientManagementController(ClientManagementService clientManagementService,
                                       ClientManagementExportService clientManagementExportService,
                                       DeskAgentService deskAgentService,
                                       DeskAssignmentUploadService deskAssignmentUploadService,
                                       DeskAssignmentTemplateService deskAssignmentTemplateService) {
        this.clientManagementService = clientManagementService;
        this.clientManagementExportService = clientManagementExportService;
        this.deskAgentService = deskAgentService;
        this.deskAssignmentUploadService = deskAssignmentUploadService;
        this.deskAssignmentTemplateService = deskAssignmentTemplateService;
    }

    /**
     * The departments worth searching: those holding at least one active, allowlisted person.
     *
     * <p>{@code refresh=true} forces a fresh whole-tenant read from BambooHR. It is not the
     * default because that call is rate-limited and the answer changes rarely.
     */
    @GetMapping("/departments")
    public List<DepartmentSummary> listDepartments(
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        String tenantId = String.valueOf(TenantContext.getTenantId());
        return clientManagementService.listDepartmentsWithSchedulableEmployees(tenantId, refresh);
    }

    /**
     * Active employees in a department whose job title passes the tenant's allowlist — the people
     * this system can actually schedule, which is what every downstream step enforces anyway.
     * Whatever the allowlist removed is reported alongside, never silently dropped.
     */
    @GetMapping("/employees")
    public EmployeeSearchResponse listEmployees(
            @RequestParam String department,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {

        String tenantId = String.valueOf(TenantContext.getTenantId());
        ClientManagementService.EmployeeSearchResult result =
                clientManagementService.listSchedulableEmployeesByDepartment(tenantId, department, refresh);
        List<BambooEmployeeResponse> all = result.employees();

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, all.size());
        List<BambooEmployeeResponse> pageData = start < all.size() ? all.subList(start, end) : List.of();
        boolean hasMore = end < all.size();

        return new EmployeeSearchResponse(pageData, hasMore, all.size(),
                result.hiddenByJobTitle(), result.hiddenJobTitles(), result.allowlistActive());
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

    @GetMapping("/employees/export")
    public ResponseEntity<byte[]> exportEmployees(@RequestParam String department) {
        String tenantId = String.valueOf(TenantContext.getTenantId());
        List<BambooEmployeeResponse> employees = clientManagementService.listEmployeesByDepartment(tenantId, department, false);

        byte[] xlsx = clientManagementExportService.exportEmployeesToExcel(employees);

        String sanitizedDepartment = department.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String filename = sanitizedDepartment + "-employees.xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @GetMapping("/employees/time-off")
    public List<DepartmentTimeOffResponse> listTimeOffByDepartment(
            @RequestParam String department,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end) {
        String tenantId = String.valueOf(TenantContext.getTenantId());
        return clientManagementService.listTimeOffByDepartment(tenantId, department, start, end);
    }

    @PostMapping("/upload-desk-assignments")
    public DeskAssignmentUploadService.DeskAssignmentUploadResult uploadDeskAssignments(
            @RequestParam("file") MultipartFile file) throws IOException {
        return deskAssignmentUploadService.uploadDeskAssignments(file);
    }

    /**
     * A workbook for one desk, built from an explicitly chosen set of people — the staged selection
     * on the Client Management page, which accumulates across several department searches.
     *
     * <p>POST rather than GET because the selection is a body, not a query string: a few hundred
     * employees do not belong in a URL. It reads nothing and writes nothing; the people are not
     * assigned to the desk by downloading this, only by uploading the filled-in file.
     */
    @PostMapping("/desk-assignments/template")
    public ResponseEntity<byte[]> downloadSelectionTemplate(
            @RequestBody DeskAssignmentSelectionRequest request) {
        byte[] xlsx = deskAssignmentTemplateService.generateTemplateForSelection(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"desk-assignment-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @GetMapping("/desk-assignments/template")
    public ResponseEntity<byte[]> downloadDeskAssignmentTemplate() {
        byte[] xlsx = deskAssignmentTemplateService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"desk-assignment-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
