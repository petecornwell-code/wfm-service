package com.wfm.controller;

import com.wfm.dto.DeskRequest;
import com.wfm.dto.DeskResponse;
import com.wfm.model.Desk;
import com.wfm.service.DeskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
        return deskService.listDesks().stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<DeskResponse> createDesk(@RequestBody DeskRequest request) {
        Desk created = deskService.createDesk(request.name(), request.description(),
                request.defaultContractedHoursPerDay());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{deskId}")
    public DeskResponse getDesk(@PathVariable UUID deskId) {
        return toResponse(deskService.getDesk(deskId));
    }

    @PutMapping("/{deskId}")
    public DeskResponse updateDesk(@PathVariable UUID deskId, @RequestBody DeskRequest request) {
        Desk updated = deskService.updateDesk(deskId, request.name(), request.description(),
                request.defaultContractedHoursPerDay());
        return toResponse(updated);
    }

    @DeleteMapping("/{deskId}")
    public ResponseEntity<Void> deleteDesk(@PathVariable UUID deskId) {
        deskService.deleteDesk(deskId);
        return ResponseEntity.noContent().build();
    }

    private DeskResponse toResponse(Desk desk) {
        return new DeskResponse(desk.getId(), desk.getName(), desk.getDescription(),
                desk.getDefaultContractedHoursPerDay());
    }
}
