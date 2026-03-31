package com.wfm.controller;

import com.wfm.dto.GenerateTimeslotsRequest;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.dto.TimeslotResponse;
import com.wfm.model.Timeslot;
import com.wfm.service.TimeslotGeneratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/timeslots")
public class TimeslotController {

    private final TimeslotGeneratorService timeslotGeneratorService;

    public TimeslotController(TimeslotGeneratorService timeslotGeneratorService) {
        this.timeslotGeneratorService = timeslotGeneratorService;
    }

    @GetMapping
    public List<TimeslotResponse> listTimeslots(@PathVariable UUID deskId,
                                                 @RequestParam String from,
                                                 @RequestParam String to) {
        return timeslotGeneratorService.listTimeslots(deskId, LocalDate.parse(from), LocalDate.parse(to))
                .stream().map(this::toResponse).toList();
    }

    @GetMapping("/bounds")
    public ResponseEntity<TimeslotBoundsResponse> getTimeslotBounds(@PathVariable UUID deskId) {
        return timeslotGeneratorService.getLiveBounds(deskId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/generate")
    public ResponseEntity<List<TimeslotResponse>> generateTimeslots(@PathVariable UUID deskId,
                                                                      @RequestBody GenerateTimeslotsRequest request) {
        List<Timeslot> generated = timeslotGeneratorService.generateTimeslots(
                deskId,
                request.periodStartDate(),
                request.periodEndDate(),
                request.startTime(),
                request.endTime(),
                request.incrementMinutes()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(generated.stream().map(this::toResponse).toList());
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteTimeslots(@PathVariable UUID deskId,
                                                 @RequestParam String from,
                                                 @RequestParam String to) {
        timeslotGeneratorService.deleteTimeslots(deskId, LocalDate.parse(from), LocalDate.parse(to));
        return ResponseEntity.noContent().build();
    }

    private TimeslotResponse toResponse(Timeslot ts) {
        return new TimeslotResponse(ts.getId(), ts.getDate(), ts.getStartTime(), ts.getEndTime());
    }
}
