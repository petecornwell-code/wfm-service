package com.wfm.controller;

import com.wfm.dto.GenerateTimeslotsRequest;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.dto.TimeslotResponse;
import com.wfm.model.Desk;
import com.wfm.model.Timeslot;
import com.wfm.service.DeskService;
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
    private final DeskService deskService;

    public TimeslotController(TimeslotGeneratorService timeslotGeneratorService, DeskService deskService) {
        this.timeslotGeneratorService = timeslotGeneratorService;
        this.deskService = deskService;
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
        // SOLV-01: the endpoint anchors on the desk's OWN stored day start, resolved tenant-scoped
        // BEFORE generation -- never a hardcoded literal -- which is what makes the generation-time
        // tiling refusal (TimeslotGeneratorService.requireDayStartTiles) reachable from this live
        // REST entry point for the first time.
        Desk desk = deskService.getDesk(deskId);
        List<Timeslot> generated = timeslotGeneratorService.generateTimeslots(
                deskId,
                request.periodStartDate(),
                request.periodEndDate(),
                desk.getDayStart(),
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
