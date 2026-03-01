package com.wfm.controller;

import com.wfm.dto.ConstraintWeightsDto;
import com.wfm.service.ConstraintWeightsService;
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
    public ConstraintWeightsDto getWeights(@PathVariable UUID deskId) {
        return constraintWeightsService.getWeights(deskId);
    }

    @PutMapping
    public ConstraintWeightsDto updateWeights(@PathVariable UUID deskId,
                                               @RequestBody ConstraintWeightsDto updates) {
        return constraintWeightsService.updateWeights(deskId, updates);
    }
}
