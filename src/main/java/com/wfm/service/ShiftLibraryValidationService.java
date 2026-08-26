package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftLibraryValidationResponse;
import com.wfm.dto.ShiftLibraryValidationResponse.HoursAdvisory;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.AgentDayHours;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.StaffingRequirement;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.ShiftTemplateBreakBandRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.repository.StaffingRequirementRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wfm.util.BigDecimals;

/**
 * The single coverage-and-hours validator SHLB-05 and MODE-03 share (D-08): {@link #validate} is
 * a non-throwing report the shift-library editor reads on every save; {@link
 * #requireShiftModeReady} runs the identical computation and converts blocking findings into a
 * {@link PreSolveValidationException}, so the report and the refusal can never disagree.
 *
 * <p>Every read is scoped through {@link TenantContext} — no method here accepts a caller-supplied
 * tenant (T-14-15).
 *
 * <p><b>Task 1 note:</b> this class is adapted here mechanically to read bands through {@link
 * ShiftTemplateBreakBandRepository} instead of Phase 14's scalar break fields, taking only the
 * first (lowest-offset) band per template so this task's own behaviour is unchanged. The any-band
 * generalisation (D-02) is Task 2's own reviewable change.
 */
@Service
public class ShiftLibraryValidationService {

    static final String NO_DEMAND_MESSAGE =
            "This desk has no staffing demand loaded. Upload staffing requirements before "
                    + "switching to shift-scheduled mode.";

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final AgentDayHoursRepository agentDayHoursRepository;
    private final TimeslotGeneratorService timeslotGeneratorService;

    public ShiftLibraryValidationService(ShiftTemplateRepository shiftTemplateRepository,
                                          ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository,
                                          StaffingRequirementRepository staffingRequirementRepository,
                                          AgentDayHoursRepository agentDayHoursRepository,
                                          TimeslotGeneratorService timeslotGeneratorService) {
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.shiftTemplateBreakBandRepository = shiftTemplateBreakBandRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentDayHoursRepository = agentDayHoursRepository;
        this.timeslotGeneratorService = timeslotGeneratorService;
    }

    /**
     * The report — never throws for a validation finding (D-08). Composes structural coverage
     * (D-04) over live demand only (D-05), the D-02 grid re-check, and the D-06/D-07 hours match.
     */
    public ShiftLibraryValidationResponse validate(UUID deskId) {
        long tenantId = TenantContext.getTenantId();

        List<ShiftTemplate> templates = shiftTemplateRepository.findByTenantIdAndDeskId(tenantId, deskId);

        // Only rows with requiredFTEs > 0 count as demand — a zero-FTE row is not demand.
        List<StaffingRequirement> demand = staffingRequirementRepository.findAllLiveByDesk(tenantId, deskId)
                .stream()
                .filter(sr -> sr.getRequiredFTEs() > 0)
                .toList();
        boolean hasLiveDemand = !demand.isEmpty();

        List<String> uncoveredWindows = findUncoveredWindows(templates, demand);
        List<String> misalignedTemplates = findMisalignedTemplates(deskId, templates);

        Map<DayOfWeek, List<BigDecimal>> hoursByWeekday = loadHoursByWeekday(tenantId, deskId);
        List<HoursAdvisory> hoursAdvisories = findHoursAdvisories(templates, hoursByWeekday);
        List<String> unsatisfiableWeekdays = findUnsatisfiableWeekdays(templates, demand, hoursByWeekday);

        return new ShiftLibraryValidationResponse(
                hasLiveDemand, uncoveredWindows, misalignedTemplates, hoursAdvisories, unsatisfiableWeekdays);
    }

    /**
     * The refusal — calls {@link #validate}, accumulates {@link ErrorDetail}s and throws once
     * (mirroring {@code SolverService.runPreSolveValidation}'s accumulate-then-throw-once shape),
     * or does nothing when the report carries no blocking finding.
     */
    public void requireShiftModeReady(UUID deskId) {
        ShiftLibraryValidationResponse response = validate(deskId);
        List<ErrorDetail> errors = new ArrayList<>();

        if (!response.hasLiveDemand()) {
            errors.add(new ErrorDetail("demand", NO_DEMAND_MESSAGE, null));
        } else {
            for (String window : response.uncoveredWindows()) {
                errors.add(new ErrorDetail("coverage", window, null));
            }
            addGridDetails(deskId, response, errors);
            if (!response.unsatisfiableWeekdays().isEmpty()) {
                errors.add(new ErrorDetail("contractedHours", contractedHoursMessage(response), null));
            }
        }

        if (!errors.isEmpty()) {
            String message = !response.uncoveredWindows().isEmpty()
                    ? response.uncoveredWindows().size() + " demand window(s) have no covering shift template"
                    : errors.get(0).message();
            throw new PreSolveValidationException(message, errors);
        }
    }

    private void addGridDetails(UUID deskId, ShiftLibraryValidationResponse response, List<ErrorDetail> errors) {
        if (response.misalignedTemplates().isEmpty()) {
            return;
        }
        int incrementMinutes = timeslotGeneratorService.getLiveBounds(deskId)
                .map(TimeslotBoundsResponse::incrementMinutes)
                .orElse(0);
        String gridMessage = "Start, end, and break times must align to this desk's "
                + incrementMinutes + "-minute schedule grid.";
        for (String misaligned : response.misalignedTemplates()) {
            errors.add(new ErrorDetail("grid", gridMessage, misaligned));
        }
    }

    private String contractedHoursMessage(ShiftLibraryValidationResponse response) {
        String weekdayList = response.unsatisfiableWeekdays().stream()
                .map(ShiftLibraryValidationService::formatWeekday)
                .collect(Collectors.joining(", "));
        return response.unsatisfiableWeekdays().size()
                + " weekday(s) have no shift template any agent's contracted hours can satisfy: "
                + weekdayList + ". Add or adjust a template, or update contracted hours, before switching modes.";
    }

    // --- Structural coverage (D-04) ---

    private List<String> findUncoveredWindows(List<ShiftTemplate> templates, List<StaffingRequirement> demand) {
        List<Window> windows = demand.stream()
                .map(sr -> new Window(sr.getTimeslot().getDate(),
                        sr.getTimeslot().getStartTime(), sr.getTimeslot().getEndTime()))
                .distinct()
                .sorted(Comparator.comparing(Window::date).thenComparing(Window::startTime))
                .toList();

        List<String> uncovered = new ArrayList<>();
        for (Window window : windows) {
            boolean covered = templates.stream().anyMatch(t -> covers(t, window));
            if (!covered) {
                uncovered.add(window.date() + " " + window.startTime() + "-" + window.endTime());
            }
        }
        return uncovered;
    }

    private boolean covers(ShiftTemplate template, Window window) {
        if (!template.getValidWeekdays().contains(window.date().getDayOfWeek())) {
            return false;
        }
        if (!withinEffectiveRange(template, window.date())) {
            return false;
        }
        if (window.startTime().isBefore(template.getStartTime()) || window.endTime().isAfter(template.getEndTime())) {
            return false;
        }
        ShiftTemplateBreakBand band = firstBand(template);
        if (band != null && band.getDurationMinutes() > 0) {
            LocalTime breakStart = band.getBreakStartTime(template);
            LocalTime breakEnd = band.getBreakEndTime(template);
            boolean overlapsBreak = window.startTime().isBefore(breakEnd) && window.endTime().isAfter(breakStart);
            if (overlapsBreak) {
                return false;
            }
        }
        return true;
    }

    private static boolean withinEffectiveRange(ShiftTemplate template, LocalDate date) {
        if (template.getEffectiveFrom().isAfter(date)) {
            return false;
        }
        return template.getEffectiveTo() == null || !template.getEffectiveTo().isBefore(date);
    }

    // --- Grid re-check (D-02) ---

    private List<String> findMisalignedTemplates(UUID deskId, List<ShiftTemplate> templates) {
        List<String> misaligned = new ArrayList<>();
        Optional<TimeslotBoundsResponse> bounds = timeslotGeneratorService.getLiveBounds(deskId);
        if (bounds.isEmpty()) {
            return misaligned;
        }
        LocalDate today = LocalDate.now();
        for (ShiftTemplate template : templates) {
            if (template.getEffectiveTo() != null && template.getEffectiveTo().isBefore(today)) {
                continue; // retired — cannot be scheduled again, its alignment is moot
            }
            if (!isTemplateAligned(template, bounds.get())) {
                misaligned.add(template.getName() + " (" + template.getEffectiveFrom() + ")");
            }
        }
        return misaligned;
    }

    private boolean isTemplateAligned(ShiftTemplate template, TimeslotBoundsResponse bounds) {
        boolean aligned = ShiftTemplateService.isAligned(bounds.startTime(), bounds.incrementMinutes(), template.getStartTime())
                && ShiftTemplateService.isAligned(bounds.startTime(), bounds.incrementMinutes(), template.getEndTime());
        ShiftTemplateBreakBand band = firstBand(template);
        if (aligned && band != null && band.getDurationMinutes() > 0) {
            aligned = ShiftTemplateService.isAligned(bounds.startTime(), bounds.incrementMinutes(), band.getBreakStartTime(template))
                    && ShiftTemplateService.isAligned(bounds.startTime(), bounds.incrementMinutes(), band.getBreakEndTime(template));
        }
        return aligned;
    }

    // --- Hours match (D-06/D-07) ---

    private Map<DayOfWeek, List<BigDecimal>> loadHoursByWeekday(long tenantId, UUID deskId) {
        List<AgentDayHours> agentDayHours = agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId);
        return agentDayHours.stream().collect(Collectors.groupingBy(
                AgentDayHours::getDayOfWeek, Collectors.mapping(AgentDayHours::getHours, Collectors.toList())));
    }

    private List<HoursAdvisory> findHoursAdvisories(List<ShiftTemplate> templates,
                                                      Map<DayOfWeek, List<BigDecimal>> hoursByWeekday) {
        List<HoursAdvisory> advisories = new ArrayList<>();
        for (ShiftTemplate template : templates) {
            BigDecimal netHours = template.getNetHours(firstBandDurationMinutes(template));
            for (DayOfWeek weekday : template.getValidWeekdays()) {
                List<BigDecimal> candidates = hoursByWeekday.getOrDefault(weekday, List.of());
                if (!anyHoursMatch(candidates, netHours)) {
                    advisories.add(new HoursAdvisory(template.getId(), template.getName(), weekday, netHours,
                            advisoryMessage(netHours, weekday)));
                }
            }
        }
        return advisories;
    }

    private List<String> findUnsatisfiableWeekdays(List<ShiftTemplate> templates, List<StaffingRequirement> demand,
                                                     Map<DayOfWeek, List<BigDecimal>> hoursByWeekday) {
        Map<DayOfWeek, List<LocalDate>> demandDatesByWeekday = demand.stream()
                .map(sr -> sr.getTimeslot().getDate())
                .distinct()
                .collect(Collectors.groupingBy(LocalDate::getDayOfWeek));

        List<String> unsatisfiable = new ArrayList<>();
        for (DayOfWeek weekday : DayOfWeek.values()) {
            List<LocalDate> demandedDates = demandDatesByWeekday.get(weekday);
            if (demandedDates == null) {
                continue;
            }
            boolean satisfiable = templates.stream().anyMatch(t ->
                    t.getValidWeekdays().contains(weekday)
                            && demandedDates.stream().anyMatch(d -> withinEffectiveRange(t, d))
                            && anyHoursMatch(hoursByWeekday.getOrDefault(weekday, List.of()),
                                    t.getNetHours(firstBandDurationMinutes(t))));
            if (!satisfiable) {
                unsatisfiable.add(weekday.name());
            }
        }
        return unsatisfiable;
    }

    /** The template's lowest-offset band, or {@code null} when it has none ("no break"). */
    private ShiftTemplateBreakBand firstBand(ShiftTemplate template) {
        List<ShiftTemplateBreakBand> bands = shiftTemplateBreakBandRepository
                .findByTenantIdAndShiftTemplateIdOrderByOffsetMinutesAsc(template.getTenantId(), template.getId());
        return bands.isEmpty() ? null : bands.get(0);
    }

    private int firstBandDurationMinutes(ShiftTemplate template) {
        ShiftTemplateBreakBand band = firstBand(template);
        return band == null ? 0 : band.getDurationMinutes();
    }

    private static boolean anyHoursMatch(List<BigDecimal> candidateHours, BigDecimal netHours) {
        if (netHours == null) {
            return false;
        }
        BigDecimal normalizedNet = BigDecimals.normalize(netHours);
        return candidateHours.stream()
                .anyMatch(h -> BigDecimals.normalize(h).compareTo(normalizedNet) == 0);
    }

    private static String advisoryMessage(BigDecimal netHours, DayOfWeek weekday) {
        String hoursText = BigDecimals.normalize(netHours).toPlainString();
        return "This shift's net duration (" + hoursText + "h) doesn't match any agent's contracted hours on "
                + formatWeekday(weekday.name())
                + ". It will still save — update contracted hours or this template later if needed.";
    }

    private static String formatWeekday(String weekdayName) {
        return weekdayName.charAt(0) + weekdayName.substring(1).toLowerCase();
    }

    private record Window(LocalDate date, LocalTime startTime, LocalTime endTime) {}
}
