package com.wfm.controller;

import com.wfm.model.Specialization;
import com.wfm.service.SpecializationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/specializations")
public class SpecializationController {

    private final SpecializationService specializationService;

    public SpecializationController(SpecializationService specializationService) {
        this.specializationService = specializationService;
    }

    @GetMapping
    public List<Specialization> listSpecializations(@PathVariable UUID deskId) {
        return specializationService.listSpecializations(deskId);
    }

    @PostMapping
    public ResponseEntity<Specialization> createSpecialization(@PathVariable UUID deskId,
                                                                @RequestBody Map<String, String> body) {
        Specialization created = specializationService.createSpecialization(deskId, body.get("name"));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Specialization> updateSpecialization(@PathVariable UUID deskId,
                                                                @PathVariable UUID id,
                                                                @RequestBody Map<String, String> body) {
        Specialization updated = specializationService.updateSpecialization(deskId, id, body.get("name"));
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSpecialization(@PathVariable UUID deskId, @PathVariable UUID id) {
        specializationService.deleteSpecialization(deskId, id);
        return ResponseEntity.noContent().build();
    }
}
