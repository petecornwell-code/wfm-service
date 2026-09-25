package com.wfm.service;

import com.wfm.calc.ErlangC;
import com.wfm.calc.ErlangX;
import com.wfm.calc.StaffingAdjustment;
import com.wfm.dto.ErlangCCalculationRequest;
import com.wfm.dto.ErlangCalculationResponse;
import com.wfm.dto.ErlangXCalculationRequest;
import com.wfm.dto.StaffingAdjustmentOptionsDto;
import com.wfm.exception.UnprocessableException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Staffing arithmetic on demand, over {@link ErlangC} and {@link ErlangX}.
 *
 * <p><b>This service reads and writes nothing.</b> It has no repositories, no {@code EntityManager}
 * and no {@code @Transactional} method — the constructor takes no arguments, which is the strongest
 * available proof of that claim. It exists so the corrected maths can be exercised against real
 * numbers without touching the {@code staffing_requirement} rows the solver consumes.
 * {@link StaffingRequirementService#calculateErlangX} is the opposite: it deletes every live
 * requirement in the date range before inserting its results, and still calls the older
 * {@link ErlangXService}. Nothing here changes that path.
 *
 * <p><b>Why the input ceiling.</b> Both models search upwards for the smallest headcount meeting the
 * target, and {@link ErlangX} solves a queue chain at every candidate. On this service's two cores,
 * shared with a running solve, an absurd load would spend minutes in that search and starve the
 * solver. Loads beyond {@link #MAX_OFFERED_LOAD} Erlangs are refused as unprocessable instead —
 * several times any desk this system schedules.
 */
@Service
public class ErlangCalculatorService {

    /** Erlangs past which a request is refused rather than searched. */
    static final double MAX_OFFERED_LOAD = 2_000.0;

    /** A day. Longer "intervals" are a unit mistake, not a forecast. */
    static final int MAX_INTERVAL_MINUTES = 1_440;

    public ErlangCalculationResponse calculateErlangC(ErlangCCalculationRequest request) {
        StaffingAdjustment.Options options = toOptions(request.adjustments());
        validateQueueingInputs(request.volume(), request.intervalMinutes(), request.ahtSeconds(),
                request.serviceLevelThresholdSeconds());

        ErlangC.Input input = new ErlangC.Input(
                effectiveVolume(request.volume(), options),
                request.intervalMinutes(),
                request.ahtSeconds(),
                request.serviceLevelTarget(),
                request.serviceLevelThresholdSeconds());
        guardLoad(input.offeredLoad());

        ErlangC.Result result = unsatisfiableAs422(() -> ErlangC.requiredAgents(input));
        // The load handed to the adjustment is the one the model solved -- already divided by
        // concurrency. Passing the raw load here would cap occupancy against work no agent carries.
        StaffingAdjustment.Result adjusted =
                StaffingAdjustment.apply(result.agents(), result.offeredLoad(), options);

        return new ErlangCalculationResponse(
                "ERLANG_C",
                result.agents(),
                adjusted.handling(),
                adjusted.scheduled(),
                adjusted.occupancyRelief(),
                adjusted.shrinkageUplift(),
                result.offeredLoad(),
                result.offeredLoad(),
                result.serviceLevel(),
                result.probabilityOfWait(),
                result.occupancy(),
                occupancyAt(result.offeredLoad(), adjusted.handling()),
                null,
                finiteOrNull(result.averageSpeedOfAnswerSeconds()),
                null);
    }

    public ErlangCalculationResponse calculateErlangX(ErlangXCalculationRequest request) {
        StaffingAdjustment.Options options = toOptions(request.adjustments());
        validateQueueingInputs(request.volume(), request.intervalMinutes(), request.ahtSeconds(),
                request.serviceLevelThresholdSeconds());
        if (request.patienceSeconds() < 0) {
            throw new IllegalArgumentException(
                    "patienceSeconds cannot be negative: " + request.patienceSeconds());
        }

        ErlangX.Input input = new ErlangX.Input(
                effectiveVolume(request.volume(), options),
                request.intervalMinutes(),
                request.ahtSeconds(),
                request.patienceSeconds(),
                request.retryFraction(),
                request.serviceLevelTarget(),
                request.serviceLevelThresholdSeconds(),
                Boolean.TRUE.equals(request.countAbandonsAsAnswered()));
        guardLoad(input.baseOfferedLoad());

        ErlangX.Result result = unsatisfiableAs422(() -> ErlangX.requiredAgents(input));
        // Retrials can push the converged load past the ceiling the base load cleared. Checked
        // after the fact rather than guessed at beforehand: the inflation factor is an output.
        guardLoad(result.offeredLoad());

        // Abandoned contacts are never handled, so the load that occupies an agent is the served
        // load. Capping occupancy against the offered load would buy relief for work nobody does.
        double servedLoad = result.offeredLoad() * (1.0 - result.probabilityOfAbandon());
        StaffingAdjustment.Result adjusted =
                StaffingAdjustment.apply(result.agents(), servedLoad, options);

        return new ErlangCalculationResponse(
                "ERLANG_X",
                result.agents(),
                adjusted.handling(),
                adjusted.scheduled(),
                adjusted.occupancyRelief(),
                adjusted.shrinkageUplift(),
                result.offeredLoad(),
                result.baseOfferedLoad(),
                result.serviceLevel(),
                result.probabilityOfWait(),
                result.occupancy(),
                occupancyAt(servedLoad, adjusted.handling()),
                result.probabilityOfAbandon(),
                null,
                result.retrialIterations());
    }

    /**
     * Concurrency divides the volume, which divides the load by exactly the same factor — the
     * interval conversion stays in {@code Input.offeredLoad()} and is not duplicated here.
     * {@code ErlangCalculatorServiceTest} pins that equivalence against
     * {@link StaffingAdjustment#effectiveLoad}, so the two cannot drift apart.
     */
    private static double effectiveVolume(double volume, StaffingAdjustment.Options options) {
        return volume / options.concurrency();
    }

    private static StaffingAdjustment.Options toOptions(StaffingAdjustmentOptionsDto dto) {
        StaffingAdjustmentOptionsDto d = dto == null ? StaffingAdjustmentOptionsDto.NONE : dto;
        return new StaffingAdjustment.Options(
                d.shrinkage() == null ? 0.0 : d.shrinkage(),
                d.maxOccupancy(),
                d.concurrency() == null ? 1.0 : d.concurrency());
    }

    /**
     * The guards the calc records do not make, because they are about a request rather than about
     * the maths. A negative volume would otherwise fall into the zero-load branch and come back as
     * a confident "0 agents".
     */
    private static void validateQueueingInputs(double volume, int intervalMinutes,
                                               double ahtSeconds, int thresholdSeconds) {
        if (volume < 0 || !Double.isFinite(volume)) {
            throw new IllegalArgumentException("volume must be zero or more: " + volume);
        }
        if (ahtSeconds < 0 || !Double.isFinite(ahtSeconds)) {
            throw new IllegalArgumentException("ahtSeconds must be zero or more: " + ahtSeconds);
        }
        if (thresholdSeconds < 0) {
            throw new IllegalArgumentException(
                    "serviceLevelThresholdSeconds must be zero or more: " + thresholdSeconds);
        }
        if (intervalMinutes > MAX_INTERVAL_MINUTES) {
            throw new IllegalArgumentException("intervalMinutes cannot exceed a day ("
                    + MAX_INTERVAL_MINUTES + "): " + intervalMinutes);
        }
    }

    private static void guardLoad(double offeredLoad) {
        if (offeredLoad > MAX_OFFERED_LOAD) {
            throw new UnprocessableException(
                    "Offered load of " + Math.round(offeredLoad) + " Erlangs exceeds the "
                            + (long) MAX_OFFERED_LOAD + " this calculator will search",
                    List.of("Check that volume is contacts in the interval and not a daily total, "
                            + "and that ahtSeconds is seconds and not minutes"));
        }
    }

    /** Occupancy at a headcount the queueing model did not itself report. */
    private static double occupancyAt(double load, int agents) {
        return agents <= 0 ? 0.0 : load / agents;
    }

    /** Zero load answers instantly and waits nobody; an infinite ASA is not a number to report. */
    private static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    /**
     * Both models throw {@link IllegalStateException} when no headcount up to their own ceiling
     * meets the target. That is a request the maths cannot satisfy, not a server fault, so it must
     * not reach the catch-all handler and come back as a 500.
     *
     * <p><b>Which requests actually reach it.</b> Not Erlang C: within {@link #MAX_OFFERED_LOAD}
     * every target below 1.0 is met well before {@code MAX_AGENTS}, and 1.0 itself is met by
     * floating-point saturation rather than refused — the canonical 10-Erlang problem returns 44
     * agents at a target of 1.0, where {@code 1 - P(wait) * decay} rounds to exactly one. Erlang X
     * does reach it, at the top of the allowed range: 2 000 Erlangs with every abandoned caller
     * retrying inflates the converged load past what 10 000 agents can serve at a 99% target, and
     * exhausts the search in roughly a quarter second. Without this mapping that request — a large
     * desk with pessimistic assumptions, not an attack — comes back as an internal error.
     */
    static <T> T unsatisfiableAs422(java.util.function.Supplier<T> calculation) {
        try {
            return calculation.get();
        } catch (IllegalStateException ex) {
            throw new UnprocessableException(ex.getMessage(),
                    List.of("Lower the service level target, raise patience, or split the load"));
        }
    }
}
