package com.wfm.controller;

import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateResponse;
import com.wfm.model.ShiftTemplate;
import com.wfm.service.ShiftTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    /**
     * Single endpoint for both editing and retiring (P-11): the Retire flow submits the
     * template's current values with a new {@code effectiveTo}. There is no delete endpoint —
     * no destructive action exists in this phase (D-10, T-14-14).
     */
    @PutMapping("/{id}")
    public ShiftTemplateResponse updateShiftTemplate(@PathVariable UUID deskId,
                                                       @PathVariable UUID id,
                                                       @RequestBody ShiftTemplateRequest request) {
        return toResponse(shiftTemplateService.updateShiftTemplate(deskId, id, request));
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
                template.getEffectiveTo(),
                eraStatus(template)
        );
    }

    /**
     * CURRENT / UPCOMING / PAST from the row's effective range against today (P-13). Both range
     * ends are inclusive, matching the same predicate ShiftTemplateService's non-overlap check
     * uses, so the two can never disagree about which era owns a date.
     */
    private String eraStatus(ShiftTemplate template) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(template.getEffectiveFrom())) {
            return "UPCOMING";
        }
        if (template.getEffectiveTo() != null && today.isAfter(template.getEffectiveTo())) {
            return "PAST";
        }
        return "CURRENT";
    }
}
