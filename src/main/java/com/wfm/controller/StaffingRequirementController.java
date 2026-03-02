package com.wfm.controller;

import com.wfm.dto.*;
import com.wfm.service.StaffingRequirementService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/staffing-requirements")
public class StaffingRequirementController {

    private final StaffingRequirementService staffingRequirementService;

    public StaffingRequirementController(StaffingRequirementService staffingRequirementService) {
        this.staffingRequirementService = staffingRequirementService;
    }

    @GetMapping
    public PaginatedResponse<StaffingRequirementResponse.Item> listRequirements(
            @PathVariable UUID deskId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return staffingRequirementService.listRequirements(deskId, from, to, cursor, limit);
    }

    @PostMapping
    public StaffingRequirementResponse saveRequirements(@PathVariable UUID deskId,
                                                         @RequestBody StaffingRequirementRequest request) {
        return staffingRequirementService.saveRequirements(deskId, request);
    }

    @PostMapping("/erlang-x")
    public StaffingRequirementResponse calculateErlangX(@PathVariable UUID deskId,
                                                         @RequestBody ErlangXRequest request) {
        return staffingRequirementService.calculateErlangX(deskId, request);
    }
}
