package com.wfm.controller;

import com.wfm.model.Schedule;
import com.wfm.service.ScheduleExportService;
import com.wfm.service.ScheduleService;
import com.wfm.service.SolverService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final SolverService solverService;
    private final ScheduleExportService scheduleExportService;

    public ScheduleController(ScheduleService scheduleService,
                              SolverService solverService,
                              ScheduleExportService scheduleExportService) {
        this.scheduleService = scheduleService;
        this.solverService = solverService;
        this.scheduleExportService = scheduleExportService;
    }

    @PostMapping("/solve")
    public ResponseEntity<Schedule> startSolve(@PathVariable UUID deskId,
                                                @RequestBody Schedule solveRequest) {
        Schedule schedule = solverService.startSolve(deskId, solveRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(schedule);
    }

    @GetMapping
    public List<Schedule> listSchedules(@PathVariable UUID deskId,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(required = false, defaultValue = "50") int limit) {
        return scheduleService.listSchedules(deskId, cursor, limit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Schedule> getScheduleDetail(@PathVariable UUID deskId,
                                                       @PathVariable UUID id,
                                                       @RequestParam(required = false) String date) {
        Schedule schedule = scheduleService.getScheduleDetail(deskId, id, date);
        return schedule != null ? ResponseEntity.ok(schedule) : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/stop")
    public ResponseEntity<Schedule> stopSolve(@PathVariable UUID deskId, @PathVariable UUID id) {
        Schedule schedule = solverService.stopSolve(deskId, id);
        return schedule != null ? ResponseEntity.ok(schedule) : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<Schedule> acceptSchedule(@PathVariable UUID deskId, @PathVariable UUID id) {
        Schedule schedule = scheduleService.acceptSchedule(deskId, id);
        return schedule != null ? ResponseEntity.ok(schedule) : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Void> rejectSchedule(@PathVariable UUID deskId, @PathVariable UUID id) {
        scheduleService.rejectSchedule(deskId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportToExcel(@PathVariable UUID deskId, @PathVariable UUID id) {
        Schedule schedule = scheduleService.getScheduleDetail(deskId, id, null);
        if (schedule == null) return ResponseEntity.notFound().build();

        byte[] xlsx = scheduleExportService.exportToExcel(schedule);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=schedule-" + id + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}
