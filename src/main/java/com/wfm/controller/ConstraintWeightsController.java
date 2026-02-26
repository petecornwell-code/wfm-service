package com.wfm.controller;

import com.wfm.model.ConstraintWeights;
import com.wfm.service.ConstraintWeightsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/constraint-weights")
public class ConstraintWeightsController {

    private final ConstraintWeightsService constraintWeightsService;

    public ConstraintWeightsController(ConstraintWeightsService constraintWeightsService) {
        this.constraintWeightsService = constraintWeightsService;
    }

    @GetMapping
    public ConstraintWeights getWeights(@PathVariable UUID deskId) {
        return constraintWeightsService.getWeights(deskId);
    }

    @PutMapping
    public ResponseEntity<ConstraintWeights> updateWeights(@PathVariable UUID deskId,
                                                            @RequestBody ConstraintWeights updates) {
        ConstraintWeights saved = constraintWeightsService.updateWeights(deskId, updates);
        return saved != null ? ResponseEntity.ok(saved) : ResponseEntity.notFound().build();
    }
}
