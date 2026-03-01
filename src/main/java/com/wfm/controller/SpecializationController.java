package com.wfm.controller;

import com.wfm.dto.SpecializationResponse;
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
    public List<SpecializationResponse> listSpecializations(@PathVariable UUID deskId) {
        return specializationService.listSpecializations(deskId).stream()
                .map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<SpecializationResponse> createSpecialization(@PathVariable UUID deskId,
                                                                        @RequestBody Map<String, String> body) {
        Specialization created = specializationService.createSpecialization(deskId, body.get("name"));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PutMapping("/{id}")
    public SpecializationResponse updateSpecialization(@PathVariable UUID deskId,
                                                        @PathVariable UUID id,
                                                        @RequestBody Map<String, String> body) {
        return toResponse(specializationService.updateSpecialization(deskId, id, body.get("name")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSpecialization(@PathVariable UUID deskId, @PathVariable UUID id) {
        specializationService.deleteSpecialization(deskId, id);
        return ResponseEntity.noContent().build();
    }

    private SpecializationResponse toResponse(Specialization spec) {
        return new SpecializationResponse(spec.getId(), spec.getName());
    }
}
