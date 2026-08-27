package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftLibraryValidationResponse;
import com.wfm.dto.ShiftLibraryValidationResponse.CapacityAdvisory;
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
 * <p><b>D-02 (Task 2):</b> {@link #covers} is any-band coverage — a demand window is covered when
 * at least one of the template's bands leaves it worked. A template with zero bands covers every
 * window inside its envelope (Phase 14's zero-duration behaviour); a template with exactly one
 * band produces the byte-identical verdict Phase 14's single offset produced, because the
 * any-band quantifier over a one-element set is the element itself. {@code covers} and the
 * package-visible {@link Window} record are public/package-visible respectively (P-03) so plan
 * 15-02's {@code ShiftLibraryGenerationService} reuses this predicate instead of reimplementing
 * it. Bands are loaded once per {@link #validate} call through the bulk repository method rather
 * than per template inside a loop.
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
        Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId = loadBandsByTemplateId(tenantId, templates);

        // Only rows with requiredFTEs > 0 count as demand — a zero-FTE row is not demand.
        List<StaffingRequirement> demand = staffingRequirementRepository.findAllLiveByDesk(tenantId, deskId)
                .stream()
                .filter(sr -> sr.getRequiredFTEs() > 0)
                .toList();
        boolean hasLiveDemand = !demand.isEmpty();

        List<String> uncoveredWindows = findUncoveredWindows(templates, bandsByTemplateId, demand);
        List<String> misalignedTemplates = findMisalignedTemplates(deskId, templates, bandsByTemplateId);

        Map<DayOfWeek, List<BigDecimal>> hoursByWeekday = loadHoursByWeekday(tenantId, deskId);
        List<HoursAdvisory> hoursAdvisories = findHoursAdvisories(templates, bandsByTemplateId, hoursByWeekday);
        List<String> unsatisfiableWeekdays =
                findUnsatisfiableWeekdays(templates, bandsByTemplateId, demand, hoursByWeekday);
        List<CapacityAdvisory> capacityAdvisories =
                findCapacityAdvisories(templates, bandsByTemplateId, hoursByWeekday);

        return new ShiftLibraryValidationResponse(hasLiveDemand, uncoveredWindows, misalignedTemplates,
                hoursAdvisories, unsatisfiableWeekdays, capacityAdvisories);
    }

    /** D-08 bulk load: one query for every template's bands per {@link #validate} call. */
    private Map<UUID, List<ShiftTemplateBreakBand>> loadBandsByTemplateId(long tenantId, List<ShiftTemplate> templates) {
        if (templates.isEmpty()) {
            return Map.of();
        }
        List<UUID> templateIds = templates.stream().map(ShiftTemplate::getId).toList();
        return shiftTemplateBreakBandRepository
                .findByTenantIdAndShiftTemplateIdInOrderByOffsetMinutesAsc(tenantId, templateIds)
                .stream()
                .collect(Collectors.groupingBy(band -> band.getShiftTemplate().getId()));
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

    private List<String> findUncoveredWindows(List<ShiftTemplate> templates,
                                               Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId,
                                               List<StaffingRequirement> demand) {
        List<Window> windows = demand.stream()
                .map(sr -> new Window(sr.getTimeslot().getDate(),
                        sr.getTimeslot().getStartTime(), sr.getTimeslot().getEndTime()))
                .distinct()
                .sorted(Comparator.comparing(Window::date).thenComparing(Window::startTime))
                .toList();

        List<String> uncovered = new ArrayList<>();
        for (Window window : windows) {
            boolean covered = templates.stream().anyMatch(t ->
                    covers(t, bandsByTemplateId.getOrDefault(t.getId(), List.of()), window));
            if (!covered) {
                uncovered.add(window.date() + " " + window.startTime() + "-" + window.endTime());
            }
        }
        return uncovered;
    }

    /**
     * D-02: any-band coverage. A window is covered when the template's weekday set contains the
     * window's day, the window falls inside the template's effective range, the window sits
     * inside the envelope, and at least one band leaves that window worked (a band whose duration
     * is zero, or a template with no bands at all, never blocks a window — "zero bands = no
     * break"). Public + package-visible {@link Window} (P-03) so plan 15-02's generation service
     * reuses this predicate rather than reimplementing it.
     */
    public static boolean covers(ShiftTemplate template, List<ShiftTemplateBreakBand> bands, Window window) {
        if (!template.getValidWeekdays().contains(window.date().getDayOfWeek())) {
            return false;
        }
        if (!withinEffectiveRange(template, window.date())) {
            return false;
        }
        if (window.startTime().isBefore(template.getStartTime()) || window.endTime().isAfter(template.getEndTime())) {
            return false;
        }
        if (bands == null || bands.isEmpty()) {
            return true;
        }
        return bands.stream().anyMatch(band -> {
            if (band.getDurationMinutes() <= 0) {
                return true;
            }
            LocalTime breakStart = band.getBreakStartTime(template);
            LocalTime breakEnd = band.getBreakEndTime(template);
            boolean overlapsBreak = window.startTime().isBefore(breakEnd) && window.endTime().isAfter(breakStart);
            return !overlapsBreak;
        });
    }

    private static boolean withinEffectiveRange(ShiftTemplate template, LocalDate date) {
        if (template.getEffectiveFrom().isAfter(date)) {
            return false;
        }
        return template.getEffectiveTo() == null || !template.getEffectiveTo().isBefore(date);
    }

    // --- Grid re-check (D-02) ---

    private List<String> findMisalignedTemplates(UUID deskId, List<ShiftTemplate> templates,
                                                  Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId) {
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
            List<ShiftTemplateBreakBand> bands = bandsByTemplateId.getOrDefault(template.getId(), List.of());
            if (!isTemplateAligned(template, bands, bounds.get())) {
                misaligned.add(template.getName() + " (" + template.getEffectiveFrom() + ")");
            }
        }
        return misaligned;
    }

    /** D-02: every band whose duration is non-zero must also have an aligned break start/end. */
    private static boolean isTemplateAligned(ShiftTemplate template, List<ShiftTemplateBreakBand> bands,
                                              TimeslotBoundsResponse bounds) {
        boolean aligned = ShiftTemplateService.isAligned(bounds.startTime(), bounds.incrementMinutes(), template.getStartTime())
                && ShiftTemplateService.isAligned(bounds.startTime(), bounds.incrementMinutes(), template.getEndTime());
        if (!aligned || bands == null) {
            return aligned;
        }
        for (ShiftTemplateBreakBand band : bands) {
            if (band.getDurationMinutes() <= 0) {
                continue;
            }
            boolean bandAligned = ShiftTemplateService.isAligned(
                    bounds.startTime(), bounds.incrementMinutes(), band.getBreakStartTime(template))
                    && ShiftTemplateService.isAligned(
                            bounds.startTime(), bounds.incrementMinutes(), band.getBreakEndTime(template));
            if (!bandAligned) {
                return false;
            }
        }
        return true;
    }

    // --- Hours match (D-06/D-07) ---

    private Map<DayOfWeek, List<BigDecimal>> loadHoursByWeekday(long tenantId, UUID deskId) {
        List<AgentDayHours> agentDayHours = agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId);
        return agentDayHours.stream().collect(Collectors.groupingBy(
                AgentDayHours::getDayOfWeek, Collectors.mapping(AgentDayHours::getHours, Collectors.toList())));
    }

    /**
     * D-02: a template is hours-satisfiable/advisory-free on a weekday when AT LEAST ONE of its
     * bands yields a net duration some agent's contracted hours on that weekday matches exactly
     * (the any-band quantifier mirrors {@link #covers}). A one-band (or zero-band) template's
     * {@code bandNetHours} list has exactly one element, so this is byte-identical to Phase 14's
     * single-offset verdict for that case.
     */
    private List<HoursAdvisory> findHoursAdvisories(List<ShiftTemplate> templates,
                                                      Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId,
                                                      Map<DayOfWeek, List<BigDecimal>> hoursByWeekday) {
        List<HoursAdvisory> advisories = new ArrayList<>();
        for (ShiftTemplate template : templates) {
            List<ShiftTemplateBreakBand> bands = bandsByTemplateId.getOrDefault(template.getId(), List.of());
            List<BigDecimal> bandNetHours = netHoursForBands(template, bands);
            for (DayOfWeek weekday : template.getValidWeekdays()) {
                List<BigDecimal> candidates = hoursByWeekday.getOrDefault(weekday, List.of());
                boolean anyBandMatches = bandNetHours.stream().anyMatch(net -> anyHoursMatch(candidates, net));
                if (!anyBandMatches) {
                    BigDecimal reportedNetHours = bandNetHours.get(0);
                    advisories.add(new HoursAdvisory(template.getId(), template.getName(), weekday, reportedNetHours,
                            advisoryMessage(reportedNetHours, weekday)));
                }
            }
        }
        return advisories;
    }

    private List<String> findUnsatisfiableWeekdays(List<ShiftTemplate> templates,
                                                     Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId,
                                                     List<StaffingRequirement> demand,
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
            List<BigDecimal> candidates = hoursByWeekday.getOrDefault(weekday, List.of());
            boolean satisfiable = templates.stream().anyMatch(t -> {
                if (!t.getValidWeekdays().contains(weekday)) {
                    return false;
                }
                if (demandedDates.stream().noneMatch(d -> withinEffectiveRange(t, d))) {
                    return false;
                }
                List<ShiftTemplateBreakBand> bands = bandsByTemplateId.getOrDefault(t.getId(), List.of());
                return netHoursForBands(t, bands).stream().anyMatch(net -> anyHoursMatch(candidates, net));
            });
            if (!satisfiable) {
                unsatisfiable.add(weekday.name());
            }
        }
        return unsatisfiable;
    }

    /**
     * One net-hours value per band, or a single full-envelope value when the template has no
     * bands (P-02: "zero bands = no break"). A one-band template's list always has exactly one
     * element, matching Phase 14's single-scalar shape exactly.
     */
    private static List<BigDecimal> netHoursForBands(ShiftTemplate template, List<ShiftTemplateBreakBand> bands) {
        if (bands.isEmpty()) {
            return List.of(template.getNetHours(0));
        }
        return bands.stream().map(b -> template.getNetHours(b.getDurationMinutes())).toList();
    }

    // --- Capacity shortfall advisory (D-03 residual risk, Task 3, P-06) ---

    /**
     * Blank/null capacity on ANY band of a template means that band alone admits an unlimited
     * number of agents, so the template as a whole has no shortfall by construction — "every
     * band blank" and "some bands blank" both clear. Only when every band on the template
     * carries an explicit capacity does the sum become a real cap, compared against the count of
     * agents whose {@code AgentDayHours} value for that weekday matches at least one band's net
     * duration exactly — the same admissibility filter the solver's value range will apply
     * (D-04), so this counts exactly the agent-days that could land on this template.
     */
    private List<CapacityAdvisory> findCapacityAdvisories(List<ShiftTemplate> templates,
                                                            Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId,
                                                            Map<DayOfWeek, List<BigDecimal>> hoursByWeekday) {
        List<CapacityAdvisory> advisories = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (ShiftTemplate template : templates) {
            if (template.getEffectiveTo() != null && template.getEffectiveTo().isBefore(today)) {
                continue; // retired
            }
            List<ShiftTemplateBreakBand> bands = bandsByTemplateId.getOrDefault(template.getId(), List.of());
            if (bands.isEmpty() || bands.stream().anyMatch(b -> b.getCapacity() == null)) {
                continue; // unlimited by construction
            }
            int capacityTotal = bands.stream().mapToInt(ShiftTemplateBreakBand::getCapacity).sum();
            List<BigDecimal> bandNetHours = netHoursForBands(template, bands);
            for (DayOfWeek weekday : template.getValidWeekdays()) {
                List<BigDecimal> candidates = hoursByWeekday.getOrDefault(weekday, List.of());
                long admissibleHeadcount = candidates.stream()
                        .filter(h -> hourMatchesAnyBand(h, bandNetHours))
                        .count();
                if (capacityTotal < admissibleHeadcount) {
                    advisories.add(new CapacityAdvisory(template.getId(), template.getName(), weekday,
                            capacityTotal, admissibleHeadcount,
                            capacityShortfallMessage(template.getName(), weekday, capacityTotal, admissibleHeadcount)));
                }
            }
        }
        return advisories;
    }

    private static boolean hourMatchesAnyBand(BigDecimal hours, List<BigDecimal> bandNetHours) {
        BigDecimal normalized = BigDecimals.normalize(hours);
        return bandNetHours.stream().anyMatch(net -> BigDecimals.normalize(net).compareTo(normalized) == 0);
    }

    private static String capacityShortfallMessage(String templateName, DayOfWeek weekday, int capacityTotal,
                                                     long admissibleHeadcount) {
        return "Shift template '" + templateName + "' has total break-band capacity " + capacityTotal
                + " on " + formatWeekday(weekday.name()) + ", but " + admissibleHeadcount
                + " agent(s) could be scheduled on it that day. Increase capacity or add a band before solving.";
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

    /** Package-visible (not private) so plan 15-02's generation service can call {@link #covers}. */
    record Window(LocalDate date, LocalTime startTime, LocalTime endTime) {}
}
