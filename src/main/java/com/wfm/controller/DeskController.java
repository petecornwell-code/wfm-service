package com.wfm.controller;

import com.wfm.dto.DayStartRequest;
import com.wfm.dto.DeskRequest;
import com.wfm.dto.DeskResponse;
import com.wfm.dto.MinimumRestRequest;
import com.wfm.dto.SchedulingModeRequest;
import com.wfm.model.Desk;
import com.wfm.model.Schedule;
import com.wfm.service.DeskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks")
public class DeskController {

    private final DeskService deskService;

    public DeskController(DeskService deskService) {
        this.deskService = deskService;
    }

    @GetMapping
    public List<DeskResponse> listDesks() {
        Map<UUID, Schedule> locksByDeskId = deskService.dayStartLocksByDeskId();
        return deskService.listDesks().stream()
                .map(desk -> toResponse(desk, locksByDeskId.get(desk.getId()), null))
                .toList();
    }

    @PostMapping
    public ResponseEntity<DeskResponse> createDesk(@RequestBody DeskRequest request) {
        Desk created = deskService.createDesk(request.name(), request.description(),
                request.defaultContractedHoursPerDay());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created, lockFor(created.getId()), null));
    }

    @GetMapping("/{deskId}")
    public DeskResponse getDesk(@PathVariable UUID deskId) {
        return toResponse(deskService.getDesk(deskId), lockFor(deskId), null);
    }

    @PutMapping("/{deskId}")
    public DeskResponse updateDesk(@PathVariable UUID deskId, @RequestBody DeskRequest request) {
        Desk updated = deskService.updateDesk(deskId, request.name(), request.description(),
                request.defaultContractedHoursPerDay());
        return toResponse(updated, lockFor(deskId), null);
    }

    @DeleteMapping("/{deskId}")
    public ResponseEntity<Void> deleteDesk(@PathVariable UUID deskId) {
        deskService.deleteDesk(deskId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{deskId}/scheduling-mode")
    public DeskResponse switchSchedulingMode(@PathVariable UUID deskId, @RequestBody SchedulingModeRequest request) {
        Desk updated = deskService.switchSchedulingMode(deskId, request.mode());
        return toResponse(updated, lockFor(deskId), null);
    }

    @PutMapping("/{deskId}/day-start")
    public DeskResponse setDayStart(@PathVariable UUID deskId, @RequestBody DayStartRequest request) {
        Desk updated = deskService.setDayStart(deskId, request.dayStart());
        // lockFor(deskId) is necessarily null on this success path: setDayStart's own
        // unconditional ACCEPTED-schedule refusal would already have thrown otherwise.
        String tilingWarning = deskService.dayStartTilingWarning(deskId, request.dayStart()).orElse(null);
        return toResponse(updated, lockFor(deskId), tilingWarning);
    }

    @PutMapping("/{deskId}/minimum-rest")
    public DeskResponse setMinimumRest(@PathVariable UUID deskId, @RequestBody MinimumRestRequest request) {
        Desk updated = deskService.setMinimumRest(deskId, request.minimumRestMinutes());
        // No tiling-warning equivalent: D-04 names no such concern for this endpoint, and the
        // third toResponse argument is the day-start tiling advisory specifically.
        return toResponse(updated, lockFor(deskId), null);
    }

    /** One per-desk lookup through the same batch finder {@link #listDesks} uses -- never a second finder shape. */
    private Schedule lockFor(UUID deskId) {
        return deskService.dayStartLocksByDeskId().get(deskId);
    }

    private DeskResponse toResponse(Desk desk, Schedule lock, String tilingWarning) {
        return new DeskResponse(desk.getId(), desk.getName(), desk.getDescription(),
                desk.getDefaultContractedHoursPerDay(), desk.getSchedulingMode(), desk.getDayStart(),
                lock != null ? lock.getId() : null,
                lock != null ? lock.getPeriodStartDate() : null,
                lock != null ? lock.getPeriodEndDate() : null,
                tilingWarning,
                desk.getMinimumRestMinutes());
    }
}
