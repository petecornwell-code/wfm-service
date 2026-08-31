package com.wfm.controller;

import com.wfm.dto.ShiftTemplateRequest;
import com.wfm.dto.ShiftTemplateResponse;
import com.wfm.dto.ShiftTemplateResponse.BreakBandResponse;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.repository.ShiftTemplateBreakBandRepository;
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
    private final ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;

    public ShiftTemplateController(ShiftTemplateService shiftTemplateService,
                                    ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository) {
        this.shiftTemplateService = shiftTemplateService;
        this.shiftTemplateBreakBandRepository = shiftTemplateBreakBandRepository;
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
     * template's current values with a new {@code effectiveTo}. Retiring remains the correct
     * action for a template that WAS used; {@link #deleteShiftTemplate} covers the case D-10
     * left with no exit — a template that never should have existed, which retiring would
     * strand in the library list permanently.
     */
    @PutMapping("/{id}")
    public ShiftTemplateResponse updateShiftTemplate(@PathVariable UUID deskId,
                                                       @PathVariable UUID id,
                                                       @RequestBody ShiftTemplateRequest request) {
        return toResponse(shiftTemplateService.updateShiftTemplate(deskId, id, request));
    }

    /**
     * Deletes a template that has never been used. A template a real schedule already assigned is
     * refused with a 409 directing the caller to retire it instead (set {@code effectiveTo}), so
     * an existing roster stays explicable. See {@code ShiftTemplateService.deleteShiftTemplate}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShiftTemplate(@PathVariable UUID deskId, @PathVariable UUID id) {
        shiftTemplateService.deleteShiftTemplate(deskId, id);
        return ResponseEntity.noContent().build();
    }

    private ShiftTemplateResponse toResponse(ShiftTemplate template) {
        List<ShiftTemplateBreakBand> bands = shiftTemplateBreakBandRepository
                .findByTenantIdAndShiftTemplateIdOrderByOffsetMinutesAsc(template.getTenantId(), template.getId());
        List<BreakBandResponse> bandResponses = bands.stream()
                .map(band -> new BreakBandResponse(
                        band.getId(),
                        band.getOffsetMinutes(),
                        band.getDurationMinutes(),
                        band.getBreakStartTime(template),
                        band.getBreakEndTime(template),
                        band.getCapacity(),
                        template.getNetHours(band.getDurationMinutes())))
                .toList();
        return new ShiftTemplateResponse(
                template.getId(),
                template.getName(),
                template.getStartTime(),
                template.getEndTime(),
                bandResponses,
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
