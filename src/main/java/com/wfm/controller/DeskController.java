package com.wfm.controller;

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
    public List<Desk> listDesks() {
        return deskService.listDesks();
    }

    @PostMapping
    public ResponseEntity<Desk> createDesk(@RequestBody Desk desk) {
        Desk created = deskService.createDesk(desk);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{deskId}")
    public ResponseEntity<Desk> getDesk(@PathVariable UUID deskId) {
        Desk desk = deskService.getDesk(deskId);
        return desk != null ? ResponseEntity.ok(desk) : ResponseEntity.notFound().build();
    }

    @PutMapping("/{deskId}")
    public ResponseEntity<Desk> updateDesk(@PathVariable UUID deskId, @RequestBody Desk updates) {
        Desk updated = deskService.updateDesk(deskId, updates);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{deskId}")
    public ResponseEntity<Void> deleteDesk(@PathVariable UUID deskId) {
        deskService.deleteDesk(deskId);
        return ResponseEntity.noContent().build();
    }
}
