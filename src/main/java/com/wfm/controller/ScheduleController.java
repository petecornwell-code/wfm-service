package com.wfm.controller;

import com.wfm.config.TenantContext;
import com.wfm.dto.PaginatedResponse;
import com.wfm.dto.ScheduleDetailResponse;
import com.wfm.dto.ScheduleSummary;
import com.wfm.dto.SolveRequest;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.repository.DeskRepository;
import com.wfm.service.ScheduleExportService;
import com.wfm.service.ScheduleService;
import com.wfm.service.SolverService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final SolverService solverService;
    private final ScheduleExportService scheduleExportService;
    private final DeskRepository deskRepository;

    public ScheduleController(ScheduleService scheduleService,
                              SolverService solverService,
                              ScheduleExportService scheduleExportService,
                              DeskRepository deskRepository) {
        this.scheduleService = scheduleService;
        this.solverService = solverService;
        this.scheduleExportService = scheduleExportService;
        this.deskRepository = deskRepository;
    }

    @PostMapping("/solve")
    public ResponseEntity<ScheduleSummary> startSolve(@PathVariable UUID deskId,
                                                       @RequestBody SolveRequest solveRequest) {
        Schedule schedule = solverService.startSolve(deskId, solveRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toSummary(schedule));
    }

    @GetMapping
    public PaginatedResponse<ScheduleSummary> listSchedules(
            @PathVariable UUID deskId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return scheduleService.listSchedules(deskId, cursor, limit);
    }

    @GetMapping("/{id}")
    public ScheduleDetailResponse getScheduleDetail(@PathVariable UUID deskId,
                                                     @PathVariable UUID id,
                                                     @RequestParam(required = false) String date) {
        return scheduleService.getScheduleDetail(deskId, id, date);
    }

    @PutMapping("/{id}/stop")
    public ResponseEntity<ScheduleSummary> stopSolve(@PathVariable UUID deskId, @PathVariable UUID id) {
        Schedule schedule = solverService.stopSolve(deskId, id);
        return ResponseEntity.ok(toSummary(schedule));
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<ScheduleSummary> acceptSchedule(@PathVariable UUID deskId,
                                                           @PathVariable UUID id,
                                                           @RequestParam int version) {
        Schedule schedule = scheduleService.acceptSchedule(deskId, id, version);
        return ResponseEntity.ok(toSummary(schedule));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Void> rejectSchedule(@PathVariable UUID deskId, @PathVariable UUID id) {
        scheduleService.rejectSchedule(deskId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable UUID deskId, @PathVariable UUID id) {
        scheduleService.deleteSchedule(deskId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportToExcel(@PathVariable UUID deskId, @PathVariable UUID id) {
        ScheduleDetailResponse detail = scheduleService.getScheduleDetail(deskId, id, null);
        byte[] xlsx = scheduleExportService.exportToExcel(detail);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=schedule-" + id + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    private ScheduleSummary toSummary(Schedule s) {
        ScheduleSummary.ScoreDto scoreDto = null;
        Boolean feasible = null;
        if (s.getScore() != null) {
            scoreDto = new ScheduleSummary.ScoreDto(s.getScore().hardScore(), s.getScore().softScore());
            feasible = s.getScore().hardScore() >= 0;
        }
        String deskName = deskRepository.findByIdAndTenantId(s.getDeskId(), TenantContext.getTenantId())
                .map(Desk::getName).orElse(null);
        return new ScheduleSummary(
                s.getId(), s.getDeskId(), deskName, s.getStatus().name(),
                s.getPeriodStartDate(), s.getPeriodEndDate(),
                s.getStartTime(), s.getEndTime(), s.getIncrementMinutes(),
                scoreDto, feasible, s.getFeasibleAt(), s.getCreatedAt(), s.getVersion());
    }
}
