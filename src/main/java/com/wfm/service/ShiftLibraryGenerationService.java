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
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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

        // One template set PER DEMAND SHAPE, not one per desk. A single set spanning every weekday
        // has to straddle shapes that want different envelopes: on the live desk it proposed
        // weekend envelopes starting at 08:00 and 09:00, where weekend demand is ZERO, and a
        // 12:00-21:00 envelope that misses the 11:00 weekend peak entirely while covering two dead
        // hours. Clusters are derived from the demand curve, never from the calendar.
        List<Set<DayOfWeek>> shapeClusters = clusterWeekdaysByDemandShape(demand, bounds);

        List<Candidate> selected = new ArrayList<>();
        int totalCandidates = 0;
        for (Set<DayOfWeek> cluster : shapeClusters) {
            List<ShiftLibraryValidationService.Window> clusterWindows = windows.stream()
                    .filter(w -> cluster.contains(w.date().getDayOfWeek()))
                    .toList();
            if (clusterWindows.isEmpty()) {
                continue;
            }
            List<StaffingRequirement> clusterDemand = demand.stream()
                    .filter(sr -> cluster.contains(sr.getTimeslot().getDate().getDayOfWeek()))
                    .toList();

            List<Candidate> clusterCandidates =
                    enumerateCandidates(clusterWindows, bounds, breakConfig, hoursByWeekday, cluster);
            totalCandidates += clusterCandidates.size();

            // P-08's named refusal: exceeding the declared candidate cap is reported, never
            // silently truncated -- a desk whose data shape breaks the "tens, not thousands"
            // sizing assumption says so out loud instead of quietly returning a worse answer.
            // Counted across ALL clusters so clustering cannot be used to slip past the cap.
            if (totalCandidates > MAX_CANDIDATE_COUNT) {
                String message = "Candidate enumeration produced " + totalCandidates
                        + " candidates, exceeding the cap of " + MAX_CANDIDATE_COUNT
                        + ". Reduce the desk's demand time range or contracted-hours variety before "
                        + "requesting a suggested library.";
                throw new PreSolveValidationException(message,
                        List.of(new ErrorDetail("candidates", message, null)));
            }

            List<Candidate> clusterSelected = greedyCover(clusterCandidates, clusterWindows);
            clusterSelected = expandForSupply(clusterSelected, clusterCandidates, clusterWindows,
                    demandHours(clusterDemand), supplyHours(clusterDemand, hoursByWeekday));
            selected.addAll(clusterSelected);
        }

        // Demand-aware band placement (Task 2) needs a per-weekday, per-start-time FTE view of the
        // WHOLE desk's demand -- the same aggregation clusterWeekdaysByDemandShape already builds,
        // reused here rather than re-derived. A single desk-wide map is correct for every cluster's
        // templates too: each weekday belongs to exactly one cluster, so scoring a candidate only
        // ever looks up entries for that candidate's own valid weekdays.
        Map<DayOfWeek, Map<LocalTime, Integer>> demandByWeekdayAndStart = aggregateDemandByWeekdayAndStart(demand);

        // D-12: do not refuse outright when full coverage is impossible -- the desk that most
        // needs help would get nothing. Return the best partial draft plus the still-uncovered
        // windows, named in the exact ErrorDetail shape SHLB-05's coverage report already emits.
        // uncoveredDetails is computed INSIDE buildResponse, from the emitted (deduped, final-band)
        // templates -- never from these pre-expansion single-band candidates (Task 1/G-15-23) -- so
        // the report and the returned templates can never disagree.
        return buildResponse(selected, hoursByWeekday, demandByWeekdayAndStart, bounds.incrementMinutes(), windows);
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

    // --- Demand-shape clustering ---

    /**
     * Cosine similarity above which two weekdays are treated as carrying the SAME demand shape.
     *
     * <p>Not fitted to one dataset — chosen to sit in the gap that real data leaves. On the live
     * desk, weekdays resemble each other at 0.949–0.991 and weekends at 0.986, while every
     * cross-pair falls between 0.680 and 0.788. There is 0.16 of clear air between the two bands,
     * so any threshold in roughly [0.80, 0.94] produces the identical split; 0.90 sits in the
     * middle of it. A desk whose days genuinely form a continuum degrades gracefully to one
     * cluster, which is exactly the pre-clustering behaviour.
     */
    private static final double SAME_SHAPE_SIMILARITY = 0.90;

    /**
     * Partitions the demanded weekdays by the SHAPE of their demand curve, never by the calendar.
     *
     * <p>Deliberately shape-keyed rather than weekday-keyed: a "weekend" is not a property of the
     * calendar but of the demand, and datasets change. If a desk's Wednesday looks like its
     * Saturday, they belong in one cluster and will get one template set; nothing here encodes
     * Mon-Fri versus Sat/Sun. On the live desk that split falls out anyway — weekdays run
     * 08:00-20:00 peaking at 17:00, weekends run 10:00-19:00 peaking at 11:00 — but it falls out
     * of the data rather than being assumed.
     *
     * <p>Profiles are compared on SHAPE, not scale: each weekday's hourly FTE vector is compared
     * by cosine similarity, which is invariant to magnitude. A quiet Sunday with the same contour
     * as a busy Saturday clusters with it, and correctly so — they want the same ENVELOPES, and
     * they differ only in how many agents those envelopes must seat, which supply-aware expansion
     * already handles per cluster.
     *
     * <p>Clustering is at WEEKDAY granularity, not per-date, because {@code
     * ShiftTemplate.validWeekdays} is a weekly mask — a template cannot be made to apply to one
     * specific date. Aggregating each weekday's dates first guarantees every weekday lands in
     * exactly one cluster, so the result is always expressible.
     *
     * <p>Complete linkage (a weekday joins only if it is similar to EVERY existing member, not
     * merely to one) prevents a chain of pairwise-similar days from silently merging two genuinely
     * different shapes. Weekdays are considered in natural order and clusters kept in creation
     * order, so the partition is deterministic.
     */
    private List<Set<DayOfWeek>> clusterWeekdaysByDemandShape(List<StaffingRequirement> demand,
                                                                TimeslotBoundsResponse bounds) {
        int increment = Math.max(1, bounds.incrementMinutes());
        Map<DayOfWeek, Map<LocalTime, Integer>> byWeekday = aggregateDemandByWeekdayAndStart(demand);

        List<LocalTime> slots = byWeekday.values().stream()
                .flatMap(m -> m.keySet().stream())
                .distinct().sorted().toList();

        Map<DayOfWeek, double[]> profiles = new TreeMap<>();
        for (Map.Entry<DayOfWeek, Map<LocalTime, Integer>> e : byWeekday.entrySet()) {
            double[] v = new double[slots.size()];
            for (int i = 0; i < slots.size(); i++) {
                v[i] = e.getValue().getOrDefault(slots.get(i), 0);
            }
            if (norm(v) > 0) {
                profiles.put(e.getKey(), v);
            }
        }

        List<Set<DayOfWeek>> clusters = new ArrayList<>();
        for (Map.Entry<DayOfWeek, double[]> entry : profiles.entrySet()) {
            Set<DayOfWeek> home = null;
            for (Set<DayOfWeek> cluster : clusters) {
                boolean similarToAll = cluster.stream()
                        .allMatch(m -> cosine(entry.getValue(), profiles.get(m)) >= SAME_SHAPE_SIMILARITY);
                if (similarToAll) {
                    home = cluster;
                    break;
                }
            }
            if (home == null) {
                home = new LinkedHashSet<>();
                clusters.add(home);
            }
            home.add(entry.getKey());
        }
        return clusters;
    }

    /**
     * Per-weekday, per-start-time summed FTE demand -- the same aggregation shape {@link
     * #clusterWeekdaysByDemandShape} needs for its cosine-similarity vectors and Task 2's
     * demand-ranked band placement needs for scoring offsets. Built once and shared (D-08-style
     * single-implementation discipline) rather than re-derived per caller.
     */
    private static Map<DayOfWeek, Map<LocalTime, Integer>> aggregateDemandByWeekdayAndStart(
            List<StaffingRequirement> demand) {
        Map<DayOfWeek, Map<LocalTime, Integer>> byWeekday = new TreeMap<>();
        for (StaffingRequirement sr : demand) {
            byWeekday.computeIfAbsent(sr.getTimeslot().getDate().getDayOfWeek(), k -> new TreeMap<>())
                    .merge(sr.getTimeslot().getStartTime(), sr.getRequiredFTEs(), Integer::sum);
        }
        return byWeekday;
    }

    private static double norm(double[] v) {
        double sum = 0;
        for (double x : v) {
            sum += x * x;
        }
        return Math.sqrt(sum);
    }

    private static double cosine(double[] a, double[] b) {
        double na = norm(a);
        double nb = norm(b);
        if (na == 0 || nb == 0) {
            return 0;
        }
        double dot = 0;
        for (int i = 0; i < a.length && i < b.length; i++) {
            dot += a[i] * b[i];
        }
        return dot / (na * nb);
    }

    // --- Supply-aware expansion beyond minimal cover ---

    /**
     * {@link #greedyCover} answers "what is the SMALLEST library that covers demand". On a desk
     * whose rostered hours EXCEED its demand, that is the wrong question, and the gap is not
     * academic: on the live desk a minimal 3-template cover left an irreducible hard score that
     * only additional envelope variety could clear. Measured there — going from 3 distinct
     * envelope spans to 5 took the residual from -18 to -6, and neither added span was needed for
     * COVERAGE. Both were needed for ABSORPTION.
     *
     * <p>The mechanism is the zero-slack identity in {@code AgentShiftAssignment
     * #getEligibleShiftBandPairs}: a pair is eligible only when its net hours EXACTLY equal the
     * agent-day's contracted hours, so an agent must occupy 100% of their legal in-envelope slots.
     * With few distinct envelopes, every agent's legal slots are the same slots; the thin hours run
     * out of seats; and any agent who cannot fill one is forced OUTSIDE their envelope to reach
     * contracted hours. More distinct envelopes means more distinct legal-slot sets, so the surplus
     * has somewhere legal to sit.
     *
     * <p>Target span count is {@code ceil(coverSpans * supply / demand)} — the cover's own size
     * scaled by how over-supplied the desk is. A desk at or below 100% supply keeps the minimal
     * cover unchanged, so this is strictly additive to existing behaviour. On the live desk (789
     * demand hours against 1104 supply, 3 cover spans) it yields {@code ceil(3 * 1.399) = 5},
     * which is exactly the five stagger positions that desk was found to need by hand.
     *
     * <p>Additions are constrained to spans lying INSIDE the demanded time range. Enumeration's
     * grid-alignment check is modular and does not bound a span to the operating window, so
     * without this an expansion could propose an envelope running past the last demanded hour.
     * Candidates are consumed in their existing deterministic sort order, so repeated requests
     * against an unchanged desk still return an identical draft.
     */
    private List<Candidate> expandForSupply(List<Candidate> selected, List<Candidate> candidates,
                                             List<ShiftLibraryValidationService.Window> windows,
                                             BigDecimal demandHours, BigDecimal supplyHours) {
        if (demandHours.signum() <= 0 || supplyHours.compareTo(demandHours) <= 0) {
            return selected; // not over-supplied — minimal cover is the right answer
        }

        Set<String> chosenSpans = selected.stream().map(ShiftLibraryGenerationService::spanKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int targetSpans = supplyHours
                .divide(demandHours, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(chosenSpans.size()))
                .setScale(0, RoundingMode.CEILING)
                .intValue();
        if (targetSpans <= chosenSpans.size()) {
            return selected;
        }

        LocalTime earliestStart = windows.stream().map(ShiftLibraryValidationService.Window::startTime)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalTime latestEnd = windows.stream().map(ShiftLibraryValidationService.Window::endTime)
                .max(Comparator.naturalOrder()).orElseThrow();

        List<Candidate> expanded = new ArrayList<>(selected);
        for (Candidate candidate : candidates) {
            if (chosenSpans.size() >= targetSpans) {
                break;
            }
            if (chosenSpans.contains(spanKey(candidate))) {
                continue;
            }
            LocalTime start = candidate.template().getStartTime();
            LocalTime end = candidate.template().getEndTime();
            if (start.isBefore(earliestStart) || end.isAfter(latestEnd)) {
                continue; // never propose an envelope reaching outside the demanded range
            }
            chosenSpans.add(spanKey(candidate));
            expanded.add(candidate);
        }
        return expanded;
    }

    private static String spanKey(Candidate candidate) {
        return candidate.template().getStartTime() + "-" + candidate.template().getEndTime();
    }

    /** Total demanded person-hours: each requirement's FTEs multiplied by its timeslot length. */
    private BigDecimal demandHours(List<StaffingRequirement> demand) {
        BigDecimal total = BigDecimal.ZERO;
        for (StaffingRequirement sr : demand) {
            long minutes = ChronoUnit.MINUTES.between(sr.getTimeslot().getStartTime(),
                    sr.getTimeslot().getEndTime());
            total = total.add(BigDecimal.valueOf(minutes)
                    .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(sr.getRequiredFTEs())));
        }
        return total;
    }

    /**
     * Total rostered person-hours over the DATES that actually carry demand — contracted hours are
     * a weekly pattern, so each demanded date contributes its own weekday's roster. Counting
     * weekday patterns once instead would undercount any week containing a repeated weekday.
     */
    private BigDecimal supplyHours(List<StaffingRequirement> demand,
                                    Map<DayOfWeek, List<BigDecimal>> hoursByWeekday) {
        Set<LocalDate> demandedDates = demand.stream()
                .map(sr -> sr.getTimeslot().getDate())
                .collect(Collectors.toCollection(TreeSet::new));
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate date : demandedDates) {
            for (BigDecimal hours : hoursByWeekday.getOrDefault(date.getDayOfWeek(), List.of())) {
                BigDecimal normalized = BigDecimals.normalize(hours);
                if (normalized != null && normalized.signum() > 0) {
                    total = total.add(normalized);
                }
            }
        }
        return total;
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

    // --- Response assembly (P-10/P-11) ---

    /**
     * The break bands a suggested template is emitted with. Chosen so a draft an operator accepts
     * unchanged is one the solver can actually use.
     *
     * <p>THREE, not one. Coverage enumeration reasons about ONE band because a band's only role
     * there is which hour it leaves unworked. But a single band means every agent on that shift
     * breaks in the SAME hour. Observed live: a one-band library gave 18 of 18 agents a 16:00
     * break, emptied the hour, and forced agents to work through their own break to hold it — 13
     * hard violations from a draft that passed every validation check. Splitting into three bands
     * removed all of them.
     */
    private static final int SUGGESTED_BAND_COUNT = 3;

    /**
     * A fully-formed candidate emission: the candidate's template, its FINAL band list (Task 2's
     * demand-ranked offsets, not the single coverage-bearing band {@code Candidate} carries), and
     * its sorted valid-weekday list -- everything {@link #dedupeKey} and the emitted-bands
     * uncovered-window recomputation (Task 1) need, computed once and reused for both.
     */
    private record EmittedRow(ShiftTemplate template, List<ShiftTemplateBreakBand> bands,
                               List<DayOfWeek> validWeekdays, BigDecimal netHours) {}

    private ShiftLibrarySuggestionResponse buildResponse(List<Candidate> selected,
                                                          Map<DayOfWeek, List<BigDecimal>> hoursByWeekday,
                                                          Map<DayOfWeek, Map<LocalTime, Integer>> demandByWeekdayAndStart,
                                                          int incrementMinutes,
                                                          List<ShiftLibraryValidationService.Window> windows) {
        List<EmittedRow> rows = new ArrayList<>();
        for (Candidate candidate : selected) {
            List<DayOfWeek> validWeekdays = candidate.template().getValidWeekdays().stream().sorted().toList();
            List<ShiftTemplateBreakBand> bands = suggestedBands(candidate, validWeekdays, hoursByWeekday,
                    demandByWeekdayAndStart, incrementMinutes, windows);
            rows.add(new EmittedRow(candidate.template(), bands, validWeekdays, candidate.netHours()));
        }

        // Dedupe AFTER band selection has run for every candidate (Task 1/G-15-23 defect 1): this
        // is where two distinct candidates -- selected by greedyCover because their DIFFERENT
        // coverage-bearing offsets self-cover each other's break hour (D-02) -- can collapse into
        // an identical emitted template once expanded to the same final band set. Keyed on exact
        // identity (start, end, sorted weekdays, ordered (offset,duration,capacity) band list),
        // never on span alone: two rows identical on that tuple cover exactly the same windows, so
        // dropping one cannot change any covers() result -- but two rows sharing only a span do
        // NOT, and collapsing those would silently discard a stagger position expandForSupply
        // deliberately added. First occurrence wins (existing deterministic iteration order).
        //
        // Dedupe does not backfill the lost row: expandForSupply counts distinct SPANS (spanKey),
        // so a collapsed exact duplicate never reduced the span count the supply-aware target was
        // computed against -- the draft simply comes back with one fewer row than the pre-dedupe
        // expansion produced, which is the correct answer, not a shortfall to paper over.
        List<EmittedRow> deduped = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (EmittedRow row : rows) {
            if (seenKeys.add(dedupeKey(row))) {
                deduped.add(row);
            }
        }

        // uncoveredDetails is computed from the EMITTED, DEDUPED, FINAL-BAND templates -- the same
        // covers() predicate stillUncovered used pre-refactor, just applied after dedupe instead of
        // before expansion -- so the reported list and the returned templates can never disagree
        // (Task 1). Assigning "Suggested N" AFTER dedupe keeps the numbering contiguous: a collapsed
        // duplicate never leaves a gap an operator would read as a missing row.
        List<ErrorDetail> uncoveredDetails = computeUncoveredDetails(deduped, windows);

        LocalDate today = LocalDate.now();
        List<SuggestedTemplate> templates = new ArrayList<>();
        int index = 1;
        for (EmittedRow row : deduped) {
            List<SuggestedBand> bands = row.bands().stream()
                    .map(b -> new SuggestedBand(b.getOffsetMinutes(), b.getDurationMinutes(), b.getCapacity()))
                    .toList();
            templates.add(new SuggestedTemplate("Suggested " + index, row.template().getStartTime(),
                    row.template().getEndTime(), bands, row.validWeekdays(), today, null, row.netHours()));
            index++;
        }
        return new ShiftLibrarySuggestionResponse(templates, uncoveredDetails);
    }

    /**
     * The exact-identity dedupe key (Task 1): start time, end time, sorted valid-weekday list, and
     * the full ordered (offset, duration, capacity) band tuple. {@code EmittedRow.bands()} is
     * already offset-sorted (see {@link #toBands}), so this is stable across otherwise-identical
     * rows regardless of which candidate produced them first.
     */
    private static String dedupeKey(EmittedRow row) {
        String bandsKey = row.bands().stream()
                .map(b -> b.getOffsetMinutes() + ":" + b.getDurationMinutes() + ":" + b.getCapacity())
                .collect(Collectors.joining(","));
        return row.template().getStartTime() + "|" + row.template().getEndTime() + "|"
                + row.validWeekdays() + "|" + bandsKey;
    }

    /**
     * Recomputes the uncovered-window report against the FINAL emitted rows -- the same {@code
     * covers()} predicate {@code stillUncovered} used pre-refactor against the pre-expansion
     * single-band candidates, now applied post-dedupe/post-band-selection so the report can never
     * drift from what the response actually returns (Task 1/D-08: one predicate, one call site
     * describing the emitted shape).
     */
    private List<ErrorDetail> computeUncoveredDetails(List<EmittedRow> rows,
                                                       List<ShiftLibraryValidationService.Window> windows) {
        List<ErrorDetail> details = new ArrayList<>();
        for (ShiftLibraryValidationService.Window window : windows) {
            boolean covered = rows.stream()
                    .anyMatch(r -> shiftLibraryValidationService.covers(r.template(), r.bands(), window));
            if (!covered) {
                details.add(new ErrorDetail("coverage", window.date() + " " + window.startTime()
                        + "-" + window.endTime(), null));
            }
        }
        return details;
    }

    /**
     * Selects {@link #SUGGESTED_BAND_COUNT} grid-aligned, capacity-capped break bands for the
     * selected candidate's envelope -- demand-ranked (Task 2/G-15-23 defect 2), never the outward
     * walk from the coverage-bearing offset it replaces.
     *
     * <p>Done HERE rather than during enumeration for three reasons. Band count does not affect
     * {@code netHours} (duration is unchanged), so eligibility — an exact net-hours match against
     * contracted hours — is untouched. {@code covers()} is ANY-band, so additional bands can only
     * ADD coverage, never remove it, and the cover guarantee {@code greedyCover} established is
     * preserved by construction (subject to the explicit re-check below, since band PLACEMENT can
     * now move away from the coverage-bearing offset). And expanding during enumeration would
     * multiply an already capped candidate space for no gain in the cover search.
     *
     * <p>A zero-band candidate stays zero-band: that is the break-less template the enumeration
     * deliberately admits only below the desk's {@code breakMinShiftHours} threshold, and it means
     * "no break", which has nothing to spread.
     *
     * <p>The admissible offset RANGE is unchanged from the outward-walk implementation --
     * {@code [incrementMinutes, spanLengthMinutes - duration - incrementMinutes]} -- and is NOT to
     * be widened. Those bounds are what forbid an edge break; Test 10's caveat and the operator's
     * explicit ruling ("Breaks are mid-shift only now") make relaxing them a regression, not an
     * improvement in search room.
     *
     * <p>Each admissible offset is scored by the demand its break window would sit on, summed over
     * the break window's timeslots and taken as the MAXIMUM across the template's valid weekdays --
     * the same busiest-day-not-average-day rule {@link #suggestedCapacity} already applies to
     * headcount, and for the same reason: a break that empties the peak on one day is not excused
     * by being harmless on another. The {@link #SUGGESTED_BAND_COUNT} lowest-scoring offsets are
     * chosen, ties broken by ascending offset (deterministic, D-11).
     *
     * <p>Coverage is the harder constraint and wins: if the demand-ranked set does not cover every
     * window the pre-expansion coverage-bearing single band covered, the coverage-bearing offset is
     * put back, evicting the highest-scoring (worst) chosen offset so the emitted band COUNT is
     * unchanged -- {@link #suggestedCapacity}'s arithmetic depends on that count, not on which
     * offsets were chosen.
     *
     * <p><b>Reverses P-11's "capacity always blank on a generated band".</b> That rule made the
     * generator's own output invisible to {@code ShiftLibraryValidationService}: its capacity check
     * skips any template carrying a blank-capacity band as "unlimited by construction", so a
     * generated draft could not be assessed by the very validator meant to guard it. Blank capacity
     * is also precisely what permits a whole shift to break at once.
     */
    private List<ShiftTemplateBreakBand> suggestedBands(Candidate candidate, List<DayOfWeek> validWeekdays,
                                                          Map<DayOfWeek, List<BigDecimal>> hoursByWeekday,
                                                          Map<DayOfWeek, Map<LocalTime, Integer>> demandByWeekdayAndStart,
                                                          int incrementMinutes,
                                                          List<ShiftLibraryValidationService.Window> windows) {
        if (candidate.durationMinutes() == 0 || candidate.bands().isEmpty()) {
            return List.of(); // break-less template: no break to spread
        }

        int duration = candidate.durationMinutes();
        // Same bounds enumerateCandidates uses, so every emitted band is one
        // ShiftTemplateService.validateGridAlignment would accept. Do NOT relax these to gain
        // search room -- they are what keeps every band off the envelope's edges.
        int minOffset = incrementMinutes;
        int maxOffset = candidate.spanLengthMinutes() - duration - incrementMinutes;

        List<Integer> admissibleOffsets = new ArrayList<>();
        for (int offset = minOffset; offset <= maxOffset; offset += incrementMinutes) {
            admissibleOffsets.add(offset);
        }

        Map<Integer, Integer> scoreByOffset = new LinkedHashMap<>();
        for (int offset : admissibleOffsets) {
            scoreByOffset.put(offset, scoreOffset(candidate.template(), offset, duration, validWeekdays,
                    demandByWeekdayAndStart, incrementMinutes));
        }

        List<Integer> ranked = new ArrayList<>(admissibleOffsets);
        ranked.sort(Comparator.<Integer>comparingInt(scoreByOffset::get).thenComparingInt(o -> o));

        int bandCount = Math.min(SUGGESTED_BAND_COUNT, ranked.size());
        Set<Integer> chosen = new LinkedHashSet<>(ranked.subList(0, bandCount));

        // Coverage re-check: the demand ranking can legitimately drop the coverage-bearing offset.
        // If it does, and the windows that offset's single band covered are no longer all covered
        // by the chosen set, force it back in -- evicting the worst (highest-scoring) member so the
        // count this candidate emits does not change.
        List<ShiftLibraryValidationService.Window> originallyCovered = windows.stream()
                .filter(w -> shiftLibraryValidationService.covers(candidate.template(), candidate.bands(), w))
                .toList();
        List<ShiftTemplateBreakBand> chosenPreview = toBands(chosen, duration, null);
        boolean coverageOk = originallyCovered.stream()
                .allMatch(w -> shiftLibraryValidationService.covers(candidate.template(), chosenPreview, w));
        if (!coverageOk) {
            int worst = chosen.stream().max(Comparator.comparingInt(scoreByOffset::get)).orElseThrow();
            chosen.remove(worst);
            chosen.add(candidate.offsetMinutes());
        }

        Integer capacity = suggestedCapacity(candidate, validWeekdays, hoursByWeekday, chosen.size());
        return toBands(chosen, duration, capacity);
    }

    /**
     * Sums this template's demand at {@code offset}'s break window (i.e. {@code
     * [templateStart+offset, templateStart+offset+duration)}), per grid slot, taken as the MAXIMUM
     * across {@code validWeekdays} -- a break that empties the peak on the busiest day the template
     * serves is not excused by being harmless on a quieter one it also serves.
     */
    private int scoreOffset(ShiftTemplate template, int offset, int duration, List<DayOfWeek> validWeekdays,
                             Map<DayOfWeek, Map<LocalTime, Integer>> demandByWeekdayAndStart,
                             int incrementMinutes) {
        LocalTime breakStart = template.getStartTime().plusMinutes(offset);
        LocalTime breakEnd = breakStart.plusMinutes(duration);
        int maxAcrossWeekdays = 0;
        for (DayOfWeek weekday : validWeekdays) {
            Map<LocalTime, Integer> daySlots = demandByWeekdayAndStart.getOrDefault(weekday, Map.of());
            int sum = 0;
            for (LocalTime slot = breakStart; slot.isBefore(breakEnd); slot = slot.plusMinutes(incrementMinutes)) {
                sum += daySlots.getOrDefault(slot, 0);
            }
            maxAcrossWeekdays = Math.max(maxAcrossWeekdays, sum);
        }
        return maxAcrossWeekdays;
    }

    private static List<ShiftTemplateBreakBand> toBands(Set<Integer> offsets, int duration, Integer capacity) {
        return offsets.stream().sorted().map(offset -> {
            ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
            band.setOffsetMinutes(offset);
            band.setDurationMinutes(duration);
            band.setCapacity(capacity);
            return band;
        }).toList();
    }

    /**
     * Per-band capacity: {@code floor(headcount / 2)}, floored at 1.
     *
     * <p>Satisfies both constraints that bound this number, which pull in opposite directions.
     * TOTAL capacity must EXCEED the headcount — {@code bandCapacityWeight} is {@code ofHard(1)},
     * so under-sizing does not merely degrade a schedule, it makes the desk unsolvable; with three
     * bands, {@code 3 * floor(h/2) >= h} for every {@code h >= 2}. And no SINGLE band may admit
     * more than half the shift, which is the threshold {@code ShiftLibraryValidationService}'s
     * break-concentration advisory warns at; {@code 2 * floor(h/2) <= h} always. So a draft
     * accepted unchanged is one that validator reports clean.
     *
     * <p>Headcount is the MAXIMUM across the template's valid weekdays, not the sum or the mean:
     * capacity must hold on the busiest day the template serves, not on an average day that may
     * never occur.
     */
    private Integer suggestedCapacity(Candidate candidate, List<DayOfWeek> validWeekdays,
                                       Map<DayOfWeek, List<BigDecimal>> hoursByWeekday, int bandCount) {
        long headcount = 0;
        for (DayOfWeek weekday : validWeekdays) {
            long onThisDay = hoursByWeekday.getOrDefault(weekday, List.of()).stream()
                    .filter(h -> BigDecimals.normalize(h).compareTo(candidate.netHours()) == 0)
                    .count();
            headcount = Math.max(headcount, onThisDay);
        }
        if (headcount <= 0) {
            return null; // no agent can hold this template; leave unlimited rather than invent a cap
        }
        long perBand = Math.max(1, headcount / 2);
        // Defensive: with fewer bands than intended (a short span), still keep the total above the
        // headcount so the hard capacity constraint cannot be the thing that breaks the desk.
        while ((long) bandCount * perBand < headcount) {
            perBand++;
        }
        return (int) perBand;
    }

    private record BreakConfig(int breakDurationMinutes, BigDecimal breakMinShiftHours) {}

    private record Candidate(ShiftTemplate template, List<ShiftTemplateBreakBand> bands, LocalTime spanStart,
                              int spanLengthMinutes, int offsetMinutes, int durationMinutes, BigDecimal netHours) {}
}
