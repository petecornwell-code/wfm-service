package com.wfm.controller;

import com.wfm.dto.JobTitleIncludePatternResponse;
import com.wfm.service.JobTitleIncludePatternService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/job-title-patterns")
public class JobTitleIncludePatternController {

    private final JobTitleIncludePatternService service;

    public JobTitleIncludePatternController(JobTitleIncludePatternService service) {
        this.service = service;
    }

    @GetMapping
    public List<JobTitleIncludePatternResponse> list() {
        return service.list();
    }

    @PostMapping
    public JobTitleIncludePatternResponse add(@RequestBody AddPatternRequest body) {
        return service.add(body.pattern());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    record AddPatternRequest(String pattern) {}
}
