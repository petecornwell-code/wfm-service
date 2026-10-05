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
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
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
    /**
     * Applies what the template WOULD contain straight to the desk, with no spreadsheet in between.
     *
     * <p>Same inputs as the download, same result shape as an upload — including the per-row skip
     * reasons — because it builds the workbook in memory and hands it to the very same parser. There
     * is no second writer to drift from the real upload path.
     *
     * <p><b>Destructive, exactly as the upload is.</b> The parser clears the desk before reimporting:
     * assignments, desk-scoped preferences, exceptions and per-day hours all go, and only what this
     * request supplies comes back. The caller confirms before calling.
     */
    @PostMapping("/desk-assignments/apply-to-desk")
    public DeskAssignmentUploadService.DeskAssignmentUploadResult applyTemplateToDesk(
            @RequestParam UUID deskId,
            @RequestParam LocalDate weekStart,
            @RequestParam(required = false) BigDecimal workingDayHours) throws IOException {
        byte[] xlsx = deskAssignmentTemplateService
                .generateTemplateForDesk(deskId, weekStart, workingDayHours);
        return deskAssignmentUploadService.uploadDeskAssignments(new ByteArrayInputStream(xlsx));
    }

    /**
     * The same direct apply, for the staged selection rather than the desk's current roster — this is
     * how a desk gets built from several department searches without a spreadsheet round trip.
     *
     * <p>{@code weekStart} is required here, unlike on the download. A selection applied with blank
     * day cells would clear the desk and then skip every row, leaving it empty — the exact failure
     * the day-cell population exists to prevent, and not something to offer behind a one-click button.
     */
    @PostMapping("/desk-assignments/apply-selection-to-desk")
    public DeskAssignmentUploadService.DeskAssignmentUploadResult applySelectionToDesk(
            @RequestBody DeskAssignmentSelectionRequest request) throws IOException {
        if (request.weekStart() == null) {
            throw new IllegalArgumentException(
                    "Choose the week to apply: without it every day cell would be blank, which clears "
                            + "the desk and re-adds nobody");
        }
        byte[] xlsx = deskAssignmentTemplateService.generateTemplateForSelection(request);
        return deskAssignmentUploadService.uploadDeskAssignments(new ByteArrayInputStream(xlsx));
    }

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

    /**
     * The current roster of ONE desk, as a single-sheet workbook.
     *
     * <p>{@code weekStart} pre-populates the seven day-hour columns for that week: approved
     * BambooHR time off as {@code PTO}/{@code MANDATORY}, every other day {@code workingDayHours}
     * (defaulting to the desk's own contracted hours). Omitting it leaves those cells blank, which
     * the upload parser skips row-by-row AFTER clearing the desk — so a populated week is what makes
     * a download-edit-reupload round trip actually work.
     *
     * <p>{@code deskId} is required. This endpoint used to return every desk as one workbook with a
     * sheet each, which is unsafe to hand back: the upload parser walks every sheet and clears each
     * desk it matches, so re-uploading that file rewrote desks the operator never opened. One desk
     * per file makes the blast radius the desk they picked.
     */
    @GetMapping("/desk-assignments/template")
    public ResponseEntity<byte[]> downloadDeskAssignmentTemplate(
            @RequestParam UUID deskId,
            @RequestParam(required = false) LocalDate weekStart,
            @RequestParam(required = false) BigDecimal workingDayHours) {
        byte[] xlsx = deskAssignmentTemplateService
                .generateTemplateForDesk(deskId, weekStart, workingDayHours);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + deskAssignmentTemplateService.templateFilenameForDesk(deskId) + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

}
