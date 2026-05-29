package com.wfm.controller;

import com.wfm.dto.JobTitleConfigResponse;
import com.wfm.service.JobTitleConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/job-titles")
public class JobTitleConfigController {

    private final JobTitleConfigService service;

    public JobTitleConfigController(JobTitleConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<JobTitleConfigResponse> list() {
        return service.list();
    }

    @PatchMapping("/{id}")
    public JobTitleConfigResponse setNonSchedulable(
            @PathVariable UUID id,
            @RequestBody SetNonSchedulableRequest body) {
        return service.setNonSchedulable(id, body.nonSchedulable());
    }

    record SetNonSchedulableRequest(boolean nonSchedulable) {}
}
