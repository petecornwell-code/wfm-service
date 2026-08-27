package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErrorResponse.ErrorDetail;
import com.wfm.dto.ShiftLibrarySuggestionResponse;
import com.wfm.dto.ShiftLibrarySuggestionResponse.SuggestedBand;
import com.wfm.dto.ShiftLibrarySuggestionResponse.SuggestedTemplate;
import com.wfm.dto.TimeslotBoundsResponse;
import com.wfm.exception.PreSolveValidationException;
import com.wfm.model.AgentDayHours;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.StaffingRequirement;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.ScheduleRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.util.BigDecimals;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SHLB-07's stateless suggestion computation (D-11): candidate envelope enumeration, contracted-
 * hours filtering, and a greedy-then-verified minimum set cover over a desk's live staffing demand.
 * Resolves {@link TenantContext#getTenantId()} at the top of its one public method -- never a
 * caller-supplied tenant (T-14-15/T-15-06). Performs no write of any kind: no repository {@code
 * save}, no {@code EntityManager} use, anywhere in this class (T-15-07/D-11) -- a suggestion is
 * computed fresh on every request and nothing is persisted until the operator saves an edited draft
 * through the existing {@code ShiftTemplateService} create/validate path, unchanged.
 *
 * <p>Coverage and hours-matching both reuse {@link ShiftLibraryValidationService}'s existing
 * predicates verbatim ({@link ShiftLibraryValidationService#covers} and the same {@code
 * BigDecimals.normalize(...).compareTo(...)} exact-equality pattern {@code anyHoursMatch} uses) --
 * this class never re-derives coverage or hours-matching logic (D-08/RESEARCH.md Pitfall 4).
 *
 * <p>Candidate enumeration and the greedy cover are built stably ordered from the start (P-09/D-10):
 * distinct contracted-hours values are collected into a {@link TreeSet}, and every candidate list is
 * sorted (span start ascending, then span length ascending, then first band offset ascending) before
 * the cover loop runs -- no {@code HashSet}/{@code HashMap} iteration order anywhere in this path, so
 * the same demand produces the same draft on every JVM run.
 */
@Service
public class ShiftLibraryGenerationService {

    /** P-08: a hard cap on enumerated candidates -- exceeding it is a named refusal, never a silent truncation. */
    static final int MAX_CANDIDATE_COUNT = 200;

    private static final BigDecimal FALLBACK_BREAK_MIN_SHIFT_HOURS = new BigDecimal("4.00");
    private static final int FALLBACK_BREAK_DURATION_MINUTES = 60;

    private final StaffingRequirementRepository staffingRequirementRepository;
    private final AgentDayHoursRepository agentDayHoursRepository;
    private final TimeslotGeneratorService timeslotGeneratorService;
    private final ShiftLibraryValidationService shiftLibraryValidationService;
    private final ScheduleRepository scheduleRepository;

    public ShiftLibraryGenerationService(StaffingRequirementRepository staffingRequirementRepository,
                                          AgentDayHoursRepository agentDayHoursRepository,
                                          TimeslotGeneratorService timeslotGeneratorService,
                                          ShiftLibraryValidationService shiftLibraryValidationService,
                                          ScheduleRepository scheduleRepository) {
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.agentDayHoursRepository = agentDayHoursRepository;
        this.timeslotGeneratorService = timeslotGeneratorService;
        this.shiftLibraryValidationService = shiftLibraryValidationService;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * D-12's shared refusal message. Byte-identical diagnostic clause to {@link
     * ShiftLibraryValidationService#NO_DEMAND_MESSAGE} ("This desk has no staffing demand
     * loaded."), with only the trailing call-to-action clause adapted to the generation context
     * (UI-SPEC copywriting contract). Covers both trigger conditions -- zero live demand rows OR
     * zero contracted-hours agents -- without distinguishing which failed, mirroring that the
     * underlying guard is one guard, not two.
     */
    static final String NO_DEMAND_OR_HOURS_MESSAGE =
            "This desk has no staffing demand loaded. Upload staffing requirements and set agents' "
                    + "contracted hours before requesting a suggested library.";

    /**
     * Computes a fresh suggestion for {@code deskId}. No write of any kind occurs -- every
     * collaborator call below is a read.
     */
    @Transactional(readOnly = true)
    public ShiftLibrarySuggestionResponse generateSuggestion(UUID deskId) {
        long tenantId = TenantContext.getTenantId();

        // Same demand definition ShiftLibraryValidationService.validate already uses (D-05): a
        // window generation calls covered is a window the validator calls covered.
        List<StaffingRequirement> demand = staffingRequirementRepository.findAllLiveByDesk(tenantId, deskId)
                .stream()
                .filter(sr -> sr.getRequiredFTEs() > 0)
                .toList();
        List<AgentDayHours> agentDayHours = agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId);

        // D-05's never-pass-vacuously rule: a desk with zero live demand, or zero agents carrying
        // contracted hours, is refused and never handed an empty draft.
        if (demand.isEmpty() || agentDayHours.isEmpty()) {
            throw new PreSolveValidationException(NO_DEMAND_OR_HOURS_MESSAGE,
                    List.of(new ErrorDetail("demand", NO_DEMAND_OR_HOURS_MESSAGE, null)));
        }

        List<ShiftLibraryValidationService.Window> windows = distinctSortedWindows(demand);

        TimeslotBoundsResponse bounds = timeslotGeneratorService.getLiveBounds(deskId)
                .orElseThrow(() -> new IllegalStateException(
                        "Desk " + deskId + " has live demand but no live timeslot grid bounds"));

        BreakConfig breakConfig = resolveBreakConfig(tenantId, deskId);

        Map<DayOfWeek, List<BigDecimal>> hoursByWeekday = agentDayHours.stream()
                .collect(Collectors.groupingBy(AgentDayHours::getDayOfWeek,
                        Collectors.mapping(AgentDayHours::getHours, Collectors.toList())));

        Set<DayOfWeek> demandedWeekdays = windows.stream()
                .map(w -> w.date().getDayOfWeek())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));

        List<Candidate> candidates =
                enumerateCandidates(windows, bounds, breakConfig, hoursByWeekday, demandedWeekdays);

        // P-08's named refusal: exceeding the declared candidate cap is reported, never silently
        // truncated -- a desk whose data shape breaks the "tens, not thousands" sizing assumption
        // says so out loud instead of quietly returning a worse answer.
        if (candidates.size() > MAX_CANDIDATE_COUNT) {
            String message = "Candidate enumeration produced " + candidates.size()
                    + " candidates, exceeding the cap of " + MAX_CANDIDATE_COUNT
                    + ". Reduce the desk's demand time range or contracted-hours variety before "
                    + "requesting a suggested library.";
            throw new PreSolveValidationException(message, List.of(new ErrorDetail("candidates", message, null)));
        }

        List<Candidate> selected = greedyCover(candidates, windows);
        List<ShiftLibraryValidationService.Window> uncovered = stillUncovered(selected, windows);

        // D-12: do not refuse outright when full coverage is impossible -- the desk that most
        // needs help would get nothing. Return the best partial draft plus the still-uncovered
        // windows, named in the exact ErrorDetail shape SHLB-05's coverage report already emits.
        List<ErrorDetail> uncoveredDetails = uncovered.stream()
                .map(w -> new ErrorDetail("coverage", w.date() + " " + w.startTime() + "-" + w.endTime(), null))
                .toList();

        return buildResponse(selected, uncoveredDetails);
    }

    private List<ShiftLibraryValidationService.Window> distinctSortedWindows(List<StaffingRequirement> demand) {
        return demand.stream()
                .map(sr -> new ShiftLibraryValidationService.Window(
                        sr.getTimeslot().getDate(), sr.getTimeslot().getStartTime(), sr.getTimeslot().getEndTime()))
                .distinct()
                .sorted(Comparator.comparing(ShiftLibraryValidationService.Window::date)
                        .thenComparing(ShiftLibraryValidationService.Window::startTime))
                .toList();
    }

    /**
     * D-06 (Phase 13)-style resolution: the desk's most-recently-created persisted {@link Schedule}
     * supplies the break duration/threshold this generation run enumerates against -- the same
     * config the four break constraints already read at solve time -- falling back to {@link
     * Schedule}'s own field defaults (60 minutes / 4.00h) when the desk has no persisted schedule
     * yet. There is no desk-level break config to read instead: {@code break_duration_minutes} and
     * {@code break_min_shift_hours} live on {@code Schedule}, not {@code Desk}.
     */
    private BreakConfig resolveBreakConfig(long tenantId, UUID deskId) {
        List<Schedule> schedules = scheduleRepository.findByTenantIdAndDeskIdOrderByCreatedAtDesc(
                tenantId, deskId, PageRequest.of(0, 1));
        if (schedules.isEmpty()) {
            return new BreakConfig(FALLBACK_BREAK_DURATION_MINUTES, FALLBACK_BREAK_MIN_SHIFT_HOURS);
        }
        Schedule latest = schedules.get(0);
        BigDecimal breakMinShiftHours = latest.getBreakMinShiftHours() != null
                ? latest.getBreakMinShiftHours() : FALLBACK_BREAK_MIN_SHIFT_HOURS;
        return new BreakConfig(latest.getBreakDurationMinutes(), breakMinShiftHours);
    }

    // --- Candidate enumeration (P-08) ---

    /**
     * Enumerates candidate envelopes: for each distinct contracted-hours value present on the
     * demanded weekdays and each admissible break duration (a grid-increment multiple up to the
     * desk's configured break duration), a span length of that net duration plus that break
     * duration; span starts step the grid increment from the earliest demanded start to the latest;
     * band offsets step the grid increment strictly inside the span. A break-less (zero-band)
     * candidate is admissible only when its net working duration is strictly below the desk's
     * {@code breakMinShiftHours} threshold (the break-less-template prohibition this plan carries as
     * a hard requirement) -- rejected during enumeration, not filtered later, so the prohibition
     * holds by construction. Every candidate whose band boundaries are not grid-aligned is dropped so
     * nothing is proposed that {@code ShiftTemplateService.validateGridAlignment} would then reject.
     */
    private List<Candidate> enumerateCandidates(List<ShiftLibraryValidationService.Window> windows,
                                                 TimeslotBoundsResponse bounds, BreakConfig breakConfig,
                                                 Map<DayOfWeek, List<BigDecimal>> hoursByWeekday,
                                                 Set<DayOfWeek> demandedWeekdays) {
        int increment = bounds.incrementMinutes();

        Set<BigDecimal> distinctHours = new TreeSet<>();
        for (DayOfWeek weekday : demandedWeekdays) {
            for (BigDecimal hours : hoursByWeekday.getOrDefault(weekday, List.of())) {
                BigDecimal normalized = BigDecimals.normalize(hours);
                if (normalized != null && normalized.compareTo(BigDecimal.ZERO) > 0) {
                    distinctHours.add(normalized);
                }
            }
        }

        LocalTime earliestStart = windows.stream().map(ShiftLibraryValidationService.Window::startTime)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalTime latestStart = windows.stream().map(ShiftLibraryValidationService.Window::startTime)
                .max(Comparator.naturalOrder()).orElseThrow();

        List<Candidate> candidates = new ArrayList<>();
        for (BigDecimal hours : distinctHours) {
            int netMinutes = hours.multiply(BigDecimal.valueOf(60)).intValue();
            for (int breakDuration = 0; breakDuration <= breakConfig.breakDurationMinutes(); breakDuration += increment) {
                if (breakDuration == 0 && hours.compareTo(breakConfig.breakMinShiftHours()) >= 0) {
                    continue; // break-less-template prohibition -- never generated at full length
                }
                int spanLength = netMinutes + breakDuration;
                for (LocalTime spanStart = earliestStart; !spanStart.isAfter(latestStart);
                     spanStart = spanStart.plusMinutes(increment)) {
                    LocalTime spanEnd = spanStart.plusMinutes(spanLength);
                    if (!spanEnd.isAfter(spanStart)) {
                        continue; // guards against midnight wraparound
                    }
                    if (!ShiftTemplateService.isAligned(bounds.startTime(), increment, spanStart)
                            || !ShiftTemplateService.isAligned(bounds.startTime(), increment, spanEnd)) {
                        continue;
                    }
                    if (breakDuration == 0) {
                        addCandidateIfAdmissible(candidates, spanStart, spanEnd, spanLength, 0, 0,
                                hours, demandedWeekdays, hoursByWeekday);
                    } else {
                        for (int offset = increment; offset + breakDuration <= spanLength - increment;
                             offset += increment) {
                            LocalTime breakStart = spanStart.plusMinutes(offset);
                            LocalTime breakEnd = breakStart.plusMinutes(breakDuration);
                            if (!ShiftTemplateService.isAligned(bounds.startTime(), increment, breakStart)
                                    || !ShiftTemplateService.isAligned(bounds.startTime(), increment, breakEnd)) {
                                continue;
                            }
                            addCandidateIfAdmissible(candidates, spanStart, spanEnd, spanLength, offset,
                                    breakDuration, hours, demandedWeekdays, hoursByWeekday);
                        }
                    }
                    if (candidates.size() > MAX_CANDIDATE_COUNT) {
                        return candidates; // caller checks size against the declared cap
                    }
                }
            }
        }

        // P-09/D-10: stably ordered before the cover loop begins -- span start ascending, then span
        // length ascending, then first (only) band offset ascending.
        candidates.sort(Comparator.comparing(Candidate::spanStart)
                .thenComparingInt(Candidate::spanLengthMinutes)
                .thenComparingInt(Candidate::offsetMinutes));
        return candidates;
    }

    private void addCandidateIfAdmissible(List<Candidate> candidates, LocalTime spanStart, LocalTime spanEnd,
                                           int spanLength, int offset, int duration, BigDecimal hours,
                                           Set<DayOfWeek> demandedWeekdays,
                                           Map<DayOfWeek, List<BigDecimal>> hoursByWeekday) {
        Set<DayOfWeek> validWeekdays = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek weekday : demandedWeekdays) {
            boolean matches = hoursByWeekday.getOrDefault(weekday, List.of()).stream()
                    .anyMatch(h -> BigDecimals.normalize(h).compareTo(hours) == 0);
            if (matches) {
                validWeekdays.add(weekday);
            }
        }
        if (validWeekdays.isEmpty()) {
            return;
        }

        ShiftTemplate template = new ShiftTemplate();
        template.setStartTime(spanStart);
        template.setEndTime(spanEnd);
        template.setValidWeekdays(validWeekdays);
        // Internal-only effective range for coverage EVALUATION: unbounded so covers()'s
        // effective-range check never blocks a candidate regardless of wall-clock date or the
        // fixture's chosen demand dates. The RESPONSE DTO substitutes P-10's real
        // effectiveFrom=today separately in buildResponse -- this is purely an evaluation detail.
        template.setEffectiveFrom(LocalDate.MIN);
        template.setEffectiveTo(null);

        List<ShiftTemplateBreakBand> bands;
        if (duration == 0) {
            bands = List.of();
        } else {
            ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
            band.setOffsetMinutes(offset);
            band.setDurationMinutes(duration);
            band.setCapacity(null); // P-11: capacity always blank on a generated band
            bands = List.of(band);
        }

        BigDecimal netHours = template.getNetHours(duration);
        candidates.add(new Candidate(template, bands, spanStart, spanLength, offset, duration, netHours));
    }

    // --- Greedy-then-verify cover (P-09) ---

    private List<Candidate> greedyCover(List<Candidate> candidates,
                                         List<ShiftLibraryValidationService.Window> windows) {
        Set<ShiftLibraryValidationService.Window> uncovered = new LinkedHashSet<>(windows);
        List<Candidate> selected = new ArrayList<>();
        while (!uncovered.isEmpty()) {
            Candidate best = null;
            int bestCoverCount = 0;
            for (Candidate candidate : candidates) {
                int coverCount = 0;
                for (ShiftLibraryValidationService.Window window : uncovered) {
                    if (shiftLibraryValidationService.covers(candidate.template(), candidate.bands(), window)) {
                        coverCount++;
                    }
                }
                if (coverCount == 0) {
                    continue;
                }
                if (best == null || isBetterCandidate(candidate, coverCount, best, bestCoverCount)) {
                    best = candidate;
                    bestCoverCount = coverCount;
                }
            }
            if (best == null) {
                break; // no remaining candidate improves coverage further
            }
            selected.add(best);
            Candidate finalBest = best;
            uncovered.removeIf(window ->
                    shiftLibraryValidationService.covers(finalBest.template(), finalBest.bands(), window));
        }
        return selected;
    }

    /**
     * P-09's deterministic tiebreak (D-10): fewest total envelope-hours beyond demand -- for a
     * candidate whose net working duration already exactly matches the demanded contracted hours by
     * construction, the only "beyond demand" time is the break duration itself (the envelope's own
     * span minus its net working minutes) -- then earliest span start, then shortest span, then
     * smallest first band offset.
     */
    private boolean isBetterCandidate(Candidate candidate, int coverCount, Candidate currentBest, int bestCoverCount) {
        if (coverCount != bestCoverCount) {
            return coverCount > bestCoverCount;
        }
        int breakCompare = Integer.compare(candidate.durationMinutes(), currentBest.durationMinutes());
        if (breakCompare != 0) {
            return breakCompare < 0;
        }
        int startCompare = candidate.spanStart().compareTo(currentBest.spanStart());
        if (startCompare != 0) {
            return startCompare < 0;
        }
        int spanCompare = Integer.compare(candidate.spanLengthMinutes(), currentBest.spanLengthMinutes());
        if (spanCompare != 0) {
            return spanCompare < 0;
        }
        return candidate.offsetMinutes() < currentBest.offsetMinutes();
    }

    private List<ShiftLibraryValidationService.Window> stillUncovered(
            List<Candidate> selected, List<ShiftLibraryValidationService.Window> windows) {
        List<ShiftLibraryValidationService.Window> result = new ArrayList<>();
        for (ShiftLibraryValidationService.Window window : windows) {
            boolean covered = selected.stream()
                    .anyMatch(c -> shiftLibraryValidationService.covers(c.template(), c.bands(), window));
            if (!covered) {
                result.add(window);
            }
        }
        return result;
    }

    // --- Response assembly (P-10/P-11) ---

    private ShiftLibrarySuggestionResponse buildResponse(List<Candidate> selected, List<ErrorDetail> uncoveredDetails) {
        LocalDate today = LocalDate.now();
        List<SuggestedTemplate> templates = new ArrayList<>();
        int index = 1;
        for (Candidate candidate : selected) {
            List<SuggestedBand> bands = candidate.bands().stream()
                    .sorted(Comparator.comparingInt(ShiftTemplateBreakBand::getOffsetMinutes))
                    .map(b -> new SuggestedBand(b.getOffsetMinutes(), b.getDurationMinutes(), null))
                    .toList();
            List<DayOfWeek> validWeekdays = candidate.template().getValidWeekdays().stream().sorted().toList();
            templates.add(new SuggestedTemplate("Suggested " + index, candidate.template().getStartTime(),
                    candidate.template().getEndTime(), bands, validWeekdays, today, null, candidate.netHours()));
            index++;
        }
        return new ShiftLibrarySuggestionResponse(templates, uncoveredDetails);
    }

    private record BreakConfig(int breakDurationMinutes, BigDecimal breakMinShiftHours) {}

    private record Candidate(ShiftTemplate template, List<ShiftTemplateBreakBand> bands, LocalTime spanStart,
                              int spanLengthMinutes, int offsetMinutes, int durationMinutes, BigDecimal netHours) {}
}
