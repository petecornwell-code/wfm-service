package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftLibraryValidationResponse;
import com.wfm.dto.ShiftLibraryValidationResponse.BreakConcentrationAdvisory;
import com.wfm.dto.ShiftLibraryValidationResponse.CapacityAdvisory;
import com.wfm.dto.ShiftLibraryValidationResponse.HoursAdvisory;
import com.wfm.dto.ShiftLibraryValidationResponse.PeakShortfallAdvisory;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.StaffingRequirement;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateBreakBandRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.repository.StaffingRequirementRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
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
    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;
    private final TimeslotGeneratorService timeslotGeneratorService;

    public ShiftLibraryValidationService(ShiftTemplateRepository shiftTemplateRepository,
                                          ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository,
                                          StaffingRequirementRepository staffingRequirementRepository,
                                          AgentDayHoursRepository agentDayHoursRepository,
                                          AgentRepository agentRepository,
                                          DeskRepository deskRepository,
                                          TimeslotGeneratorService timeslotGeneratorService) {
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.shiftTemplateBreakBandRepository = shiftTemplateBreakBandRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentDayHoursRepository = agentDayHoursRepository;
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
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
        List<BreakConcentrationAdvisory> breakConcentrationAdvisories =
                findBreakConcentrationAdvisories(templates, bandsByTemplateId, hoursByWeekday);
        List<PeakShortfallAdvisory> peakShortfallAdvisories =
                findPeakShortfalls(templates, bandsByTemplateId, demand, hoursByWeekday);

        return new ShiftLibraryValidationResponse(hasLiveDemand, uncoveredWindows, misalignedTemplates,
                hoursAdvisories, unsatisfiableWeekdays, capacityAdvisories, breakConcentrationAdvisories,
                peakShortfallAdvisories);
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
        if (!template.isEffectiveOn(window.date())) {
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

    /**
     * The contracted-hours values in play on each weekday, one entry per agent on the desk.
     *
     * <p>Resolution mirrors {@code SolverService.resolveEffectiveHours} — an
     * {@code agent_day_hours} row wins where one exists, otherwise the DESK DEFAULT applies. The
     * per-date exception tier that method also consults has no analogue here, because this check
     * is per WEEKDAY rather than per date.
     *
     * <p>The fallback is the fix for UAT 17b. Reading only the rows meant an agent whose per-day
     * hours had never been edited contributed NOTHING, so a newly created desk — where no agent
     * has rows yet — reported every weekday unsatisfiable and could not be switched into SHIFT
     * mode at all, while {@code DeskAgentResponse.dayHours.X.effectiveHours} showed the desk
     * default on screen and the solver would happily have scheduled against it. The validator was
     * stricter than the solver about the same fact, and the validator is what gates mode entry.
     *
     * <p>An agent with NO rows is therefore assumed available on every weekday at the desk
     * default, which is exactly what the solver assumes: {@code computeAgentDayConfigs} walks
     * every date in the period and falls through to the schedule default for any weekday the
     * agent has no row for. A desk with no agents at all still yields an empty map and so still
     * reports every demanded weekday unsatisfiable — no agents means no hours, which is a
     * different statement from "hours not recorded yet".
     */
    private Map<DayOfWeek, List<BigDecimal>> loadHoursByWeekday(long tenantId, UUID deskId) {
        Map<UUID, Map<DayOfWeek, BigDecimal>> rowsByAgent =
                agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId).stream()
                        .collect(Collectors.groupingBy(
                                adh -> adh.getAgent().getId(),
                                Collectors.toMap(AgentDayHours::getDayOfWeek, AgentDayHours::getHours,
                                        (first, second) -> first)));

        BigDecimal deskDefaultHours = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(Desk::getDefaultContractedHoursPerDay)
                .orElse(null);

        Map<DayOfWeek, List<BigDecimal>> hoursByWeekday = new EnumMap<>(DayOfWeek.class);
        for (Agent agent : agentRepository.findByTenantIdAndDeskId(tenantId, deskId)) {
            Map<DayOfWeek, BigDecimal> rows = rowsByAgent.getOrDefault(agent.getId(), Map.of());
            for (DayOfWeek weekday : DayOfWeek.values()) {
                BigDecimal hours = rows.containsKey(weekday) ? rows.get(weekday) : deskDefaultHours;
                if (hours == null) {
                    continue;
                }
                hoursByWeekday.computeIfAbsent(weekday, w -> new ArrayList<>()).add(hours);
            }
        }
        return hoursByWeekday;
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
                if (demandedDates.stream().noneMatch(t::isEffectiveOn)) {
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

    // --- Peak-hour shortfall (the blind spot every PER-DATE aggregate shares) ---

    /**
     * Finds hours whose demand exceeds every agent who could possibly work them.
     *
     * <p>Every other supply check on a shift desk aggregates over a DATE.
     * {@code SolverService.requireShiftEnvelopeSeatSupply} compares a day's contracted slots
     * against its library-covered seat supply; the staffing summary reports daily coverage. Both
     * can report comfortable surplus while one hour inside that day is unmeetable, because a
     * daily total says nothing about its distribution. Observed live: 143 demand-hours against 200
     * staffed (140% coverage, every aggregate check clean) while Saturday 11:00 needed 44 FTE
     * against 25 agents on the whole desk.
     *
     * <p>{@code reachableAgents} is computed as a deliberate UPPER bound — every agent rostered
     * that weekday whose contracted hours match at least one {@code (template, band)} pair
     * covering the hour, ignoring that those same agents must also staff every other hour of their
     * shift. A real schedule can only do worse. Reporting only PROVABLE shortfalls is what makes
     * this worth an operator's attention: if it fires, no library edit and no amount of solve time
     * can close it, so it is a staffing conversation rather than a tuning one.
     *
     * <p>Coverage is decided by {@link #covers} with a SINGLE band, not the template's whole band
     * list. {@code covers} is any-band, so passing the full list would ask "could someone on this
     * template work this hour on some band", whereas the question here is per-agent: the agent
     * holds ONE band, and their net hours must match THAT band's. Passing one band at a time keeps
     * the coverage predicate and the hours match describing the same pair.
     */
    private List<PeakShortfallAdvisory> findPeakShortfalls(List<ShiftTemplate> templates,
                                                             Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId,
                                                             List<StaffingRequirement> demand,
                                                             Map<DayOfWeek, List<BigDecimal>> hoursByWeekday) {
        List<PeakShortfallAdvisory> advisories = new ArrayList<>();
        for (StaffingRequirement sr : demand) {
            LocalDate date = sr.getTimeslot().getDate();
            Window window = new Window(date, sr.getTimeslot().getStartTime(), sr.getTimeslot().getEndTime());

            // Net-hours values an agent could hold and still be working THIS hour.
            List<BigDecimal> coveringNetHours = new ArrayList<>();
            for (ShiftTemplate template : templates) {
                List<ShiftTemplateBreakBand> bands = bandsByTemplateId.getOrDefault(template.getId(), List.of());
                if (bands.isEmpty()) {
                    if (covers(template, List.of(), window)) {
                        coveringNetHours.add(template.getNetHours(0));
                    }
                    continue;
                }
                for (ShiftTemplateBreakBand band : bands) {
                    if (covers(template, List.of(band), window)) {
                        coveringNetHours.add(template.getNetHours(band.getDurationMinutes()));
                    }
                }
            }
            if (coveringNetHours.isEmpty()) {
                continue; // no envelope reaches this hour at all — that is uncoveredWindows' job
            }

            long reachable = hoursByWeekday.getOrDefault(date.getDayOfWeek(), List.of()).stream()
                    .filter(h -> {
                        BigDecimal normalized = BigDecimals.normalize(h);
                        return normalized != null
                                && coveringNetHours.stream().anyMatch(n -> normalized.compareTo(n) == 0);
                    })
                    .count();

            if (sr.getRequiredFTEs() > reachable) {
                long shortfall = sr.getRequiredFTEs() - reachable;
                advisories.add(new PeakShortfallAdvisory(date, window.startTime(), window.endTime(),
                        sr.getRequiredFTEs(), reachable, shortfall,
                        peakShortfallMessage(date, window, sr.getRequiredFTEs(), reachable, shortfall)));
            }
        }
        advisories.sort(Comparator.comparingLong(PeakShortfallAdvisory::shortfall).reversed()
                .thenComparing(PeakShortfallAdvisory::date)
                .thenComparing(PeakShortfallAdvisory::startTime));
        return advisories;
    }

    private static String peakShortfallMessage(LocalDate date, Window window, int required,
                                                 long reachable, long shortfall) {
        return date + " " + window.startTime() + "-" + window.endTime() + " needs " + required
                + " agent(s), but only " + reachable + " rostered agent(s) that "
                + date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " could be working it at all — short by " + shortfall + ". This counts every "
                + "agent whose contracted hours match a shift covering that hour, ignoring that "
                + "they must also staff the rest of their shift, so the real figure can only be "
                + "lower. No library change or longer solve can close this: it needs more rostered "
                + "agents that day, or a lower forecast for that hour.";
    }

    // --- Break concentration advisory (the inverse blind spot of the shortfall check) ---

    /**
     * A shift below this headcount cannot concentrate a break in any way an operator needs warning
     * about — three people breaking together is a normal shift, not a coverage risk.
     */
    private static final int MIN_HEADCOUNT_FOR_CONCENTRATION_ADVISORY = 4;

    /**
     * Flags a template whose bands permit MORE THAN HALF its admissible headcount to break in the
     * same hour. Deliberately complements {@link #findCapacityAdvisories}, which fires only on
     * capacity being too LOW and skips blank-capacity templates entirely — leaving the default
     * configuration (one band, blank capacity, everybody breaks together) inspected by nothing.
     *
     * <p>The "more than half" threshold is chosen so a reasonably-split library stays quiet: three
     * bands capped at 9 against a headcount of 18 permits exactly half, and does NOT warn; a single
     * blank band against the same 18 permits all of them, and does. Strictly greater-than avoids
     * flagging the two-band even split, which is a legitimate shape.
     *
     * <p>Zero bands is skipped rather than flagged: P-01 makes zero bands mean "no break at all",
     * so there is no break to concentrate. That is a different concern ({@code hoursAdvisories}
     * covers whether such a template's net hours are usable).
     *
     * <p>Advisory only, never blocking. A concentrated library is legal and can be entirely correct
     * on a shift whose break hour carries little demand — this reports what the library PERMITS so
     * an operator can judge it against their own demand curve, rather than discovering it as a hard
     * score after a solve.
     */
    private List<BreakConcentrationAdvisory> findBreakConcentrationAdvisories(
            List<ShiftTemplate> templates,
            Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId,
            Map<DayOfWeek, List<BigDecimal>> hoursByWeekday) {
        List<BreakConcentrationAdvisory> advisories = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (ShiftTemplate template : templates) {
            if (template.getEffectiveTo() != null && template.getEffectiveTo().isBefore(today)) {
                continue; // retired
            }
            List<ShiftTemplateBreakBand> bands = bandsByTemplateId.getOrDefault(template.getId(), List.of());
            if (bands.isEmpty()) {
                continue; // zero bands = no break (P-01), nothing to concentrate
            }
            boolean anyUnlimited = bands.stream().anyMatch(b -> b.getCapacity() == null);
            int largestBand = anyUnlimited ? Integer.MAX_VALUE
                    : bands.stream().mapToInt(ShiftTemplateBreakBand::getCapacity).max().orElse(0);
            List<BigDecimal> bandNetHours = netHoursForBands(template, bands);

            for (DayOfWeek weekday : template.getValidWeekdays()) {
                List<BigDecimal> candidates = hoursByWeekday.getOrDefault(weekday, List.of());
                long admissibleHeadcount = candidates.stream()
                        .filter(h -> hourMatchesAnyBand(h, bandNetHours))
                        .count();
                if (admissibleHeadcount < MIN_HEADCOUNT_FOR_CONCENTRATION_ADVISORY) {
                    continue;
                }
                long worstCase = Math.min(admissibleHeadcount, (long) largestBand);
                if (worstCase * 2 > admissibleHeadcount) {
                    advisories.add(new BreakConcentrationAdvisory(template.getId(), template.getName(),
                            weekday, bands.size(), admissibleHeadcount, worstCase,
                            breakConcentrationMessage(template.getName(), weekday, bands.size(),
                                    admissibleHeadcount, worstCase, anyUnlimited)));
                }
            }
        }
        return advisories;
    }

    private static String breakConcentrationMessage(String templateName, DayOfWeek weekday, int bandCount,
                                                      long admissibleHeadcount, long worstCase,
                                                      boolean anyUnlimited) {
        String cause = anyUnlimited
                ? "at least one band has a blank (unlimited) capacity, so nothing caps how many take it"
                : "the largest band admits " + worstCase + " of them";
        return "On " + weekday.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", '" + templateName
                + "' has " + bandCount + " break band(s) for " + admissibleHeadcount
                + " admissible agent(s), and " + cause + ". Up to " + worstCase + " of "
                + admissibleHeadcount + " could break in the SAME hour, leaving that hour staffed by "
                + (admissibleHeadcount - worstCase) + " of them. If that hour carries demand, the "
                + "solver must either leave it short or seat agents through their own break. To "
                + "spread the load: add more bands, or set each band's capacity to roughly "
                + Math.max(1, (admissibleHeadcount + bandCount - 1) / bandCount) + ". Keep the bands' "
                + "TOTAL capacity above " + admissibleHeadcount
                + " — band capacity is a hard constraint, so under-sizing it makes the desk unsolvable.";
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
