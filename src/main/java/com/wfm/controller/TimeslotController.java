package com.wfm.controller;

import com.wfm.model.Timeslot;
import com.wfm.service.TimeslotGeneratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/timeslots")
public class TimeslotController {

    private final TimeslotGeneratorService timeslotGeneratorService;

    public TimeslotController(TimeslotGeneratorService timeslotGeneratorService) {
        this.timeslotGeneratorService = timeslotGeneratorService;
    }

    @GetMapping
    public List<Timeslot> listTimeslots(@PathVariable UUID deskId,
                                        @RequestParam String from,
                                        @RequestParam String to) {
        return timeslotGeneratorService.listTimeslots(deskId, LocalDate.parse(from), LocalDate.parse(to));
    }

    @PostMapping("/generate")
    public ResponseEntity<List<Timeslot>> generateTimeslots(@PathVariable UUID deskId,
                                                             @RequestBody Map<String, Object> body) {
        List<Timeslot> generated = timeslotGeneratorService.generateTimeslots(
                deskId,
                LocalDate.parse((String) body.get("periodStartDate")),
                LocalDate.parse((String) body.get("periodEndDate")),
                LocalTime.parse((String) body.get("startTime")),
                LocalTime.parse((String) body.get("endTime")),
                (Integer) body.get("incrementMinutes")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(generated);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteTimeslots(@PathVariable UUID deskId,
                                                 @RequestParam String from,
                                                 @RequestParam String to) {
        timeslotGeneratorService.deleteTimeslots(deskId, LocalDate.parse(from), LocalDate.parse(to));
        return ResponseEntity.noContent().build();
    }
}
