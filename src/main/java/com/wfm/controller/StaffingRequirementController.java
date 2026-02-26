package com.wfm.controller;

import com.wfm.model.StaffingRequirement;
import com.wfm.service.StaffingRequirementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/staffing-requirements")
public class StaffingRequirementController {

    private final StaffingRequirementService staffingRequirementService;

    public StaffingRequirementController(StaffingRequirementService staffingRequirementService) {
        this.staffingRequirementService = staffingRequirementService;
    }

    @GetMapping
    public List<StaffingRequirement> listRequirements(@PathVariable UUID deskId,
                                                       @RequestParam(required = false) String from,
                                                       @RequestParam(required = false) String to,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false, defaultValue = "50") int limit) {
        return staffingRequirementService.listRequirements(deskId, from, to, cursor, limit);
    }

    @PostMapping
    public ResponseEntity<?> saveRequirements(@PathVariable UUID deskId,
                                              @RequestBody Object body) {
        // TODO: parse StaffingRequirementRequest, delegate to service
        return ResponseEntity.ok().build();
    }

    @PostMapping("/erlang-x")
    public ResponseEntity<?> calculateErlangX(@PathVariable UUID deskId,
                                              @RequestBody Object body) {
        // TODO: parse ErlangXRequest, delegate to service
        return ResponseEntity.ok().build();
    }
}
