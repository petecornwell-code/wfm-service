package com.wfm.controller;

import com.wfm.config.TenantContext;
import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.dto.PaginatedResponse;
import com.wfm.service.ClientManagementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/client-management")
public class ClientManagementController {

    private final ClientManagementService clientManagementService;

    public ClientManagementController(ClientManagementService clientManagementService) {
        this.clientManagementService = clientManagementService;
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
}
