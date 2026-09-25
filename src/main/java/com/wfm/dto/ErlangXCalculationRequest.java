package com.wfm.dto;

/**
 * An Erlang X question — Erlang C plus impatience and retrials.
 *
 * <p>Carries the same five queueing fields as {@link ErlangCCalculationRequest} plus the three that
 * only this model has. They are separate records rather than one with nullable extras so that
 * neither endpoint can silently ignore a field the caller meant to matter.
 *
 * @param volume                        first-attempt contacts in the interval, before retrials
 * @param intervalMinutes               length of that interval
 * @param ahtSeconds                    average handling time
 * @param patienceSeconds               mean time before an unanswered caller abandons; 0 or absent
 *                                      means infinitely patient, which is pure Erlang C
 * @param retryFraction                 fraction of abandoned callers who call back, in [0,1]
 * @param serviceLevelTarget            fraction to answer within the threshold
 * @param serviceLevelThresholdSeconds  the "within"
 * @param countAbandonsAsAnswered       null is false — a caller who hung up is not a success.
 *                                      Setting it true raises the reported service level and
 *                                      therefore lowers headcount, so it is chosen, never defaulted.
 * @param adjustments                   rostering adjustments; null means none
 */
public record ErlangXCalculationRequest(
        double volume,
        int intervalMinutes,
        double ahtSeconds,
        double patienceSeconds,
        double retryFraction,
        double serviceLevelTarget,
        int serviceLevelThresholdSeconds,
        Boolean countAbandonsAsAnswered,
        StaffingAdjustmentOptionsDto adjustments
) {}
