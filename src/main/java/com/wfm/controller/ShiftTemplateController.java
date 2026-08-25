package com.wfm.controller;

import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateResponse;
import com.wfm.model.ShiftTemplate;
import com.wfm.service.ShiftTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/desks/{deskId}/shift-templates")
public class ShiftTemplateController {

    private final ShiftTemplateService shiftTemplateService;

    public ShiftTemplateController(ShiftTemplateService shiftTemplateService) {
        this.shiftTemplateService = shiftTemplateService;
    }

    @GetMapping
    public List<ShiftTemplateResponse> listShiftTemplates(@PathVariable UUID deskId) {
        return shiftTemplateService.listShiftTemplates(deskId).stream()
                .map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<ShiftTemplateResponse> createShiftTemplate(@PathVariable UUID deskId,
                                                                       @RequestBody ShiftTemplateRequest request) {
        ShiftTemplate created = shiftTemplateService.createShiftTemplate(deskId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    private ShiftTemplateResponse toResponse(ShiftTemplate template) {
        return new ShiftTemplateResponse(
                template.getId(),
                template.getName(),
                template.getStartTime(),
                template.getEndTime(),
                template.getBreakOffsetMinutes(),
                template.getBreakDurationMinutes(),
                template.getBreakStartTime(),
                template.getBreakEndTime(),
                template.getNetHours(),
                List.copyOf(template.getValidWeekdays()),
                template.getEffectiveFrom(),
                template.getEffectiveTo()
        );
    }
}
