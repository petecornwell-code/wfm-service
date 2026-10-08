package com.wfm.controller;

import com.wfm.dto.*;
import com.wfm.service.FteUploadService;
import com.wfm.service.StaffingRequirementService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/staffing-requirements")
public class StaffingRequirementController {

    private final StaffingRequirementService staffingRequirementService;
    private final FteUploadService fteUploadService;

    public StaffingRequirementController(StaffingRequirementService staffingRequirementService,
                                         FteUploadService fteUploadService) {
        this.staffingRequirementService = staffingRequirementService;
        this.fteUploadService = fteUploadService;
    }

    @GetMapping
    public PaginatedResponse<StaffingRequirementResponse.Item> listRequirements(
            @PathVariable UUID deskId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String businessFrom,
            @RequestParam(required = false) String businessTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return staffingRequirementService.listRequirements(deskId, from, to, businessFrom, businessTo, cursor, limit);
    }

    @PostMapping
    public StaffingRequirementResponse saveRequirements(@PathVariable UUID deskId,
                                                         @RequestBody StaffingRequirementRequest request) {
        return staffingRequirementService.saveRequirements(deskId, request);
    }

    /**
     * Erlang C, the conservative baseline. Like {@code /erlang-x} below it REPLACES live
     * requirements — but only those of the request's business date and of each {@code copyTo}
     * date; no other date changes. It is not the read-only calculator, which lives at
     * {@code /api/v1/calc/erlang-c} and writes nothing.
     */
    @PostMapping("/erlang-c")
    public StaffingRequirementResponse calculateErlangC(@PathVariable UUID deskId,
                                                        @RequestBody ErlangCRequest request) {
        return staffingRequirementService.calculateErlangC(deskId, request);
    }

    /**
     * Erlang X. Replaces the live requirements of the request's business date and of each
     * {@code copyTo} date, and no others. A request that cannot be honoured in full is refused
     * with 400 before anything is deleted.
     */
    @PostMapping("/erlang-x")
    public StaffingRequirementResponse calculateErlangX(@PathVariable UUID deskId,
                                                         @RequestBody ErlangXRequest request) {
        return staffingRequirementService.calculateErlangX(deskId, request);
    }

    @PostMapping("/upload")
    public FteUploadResult uploadFtes(@PathVariable UUID deskId,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        return fteUploadService.uploadFtes(deskId, file);
    }
}
