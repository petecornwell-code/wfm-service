package com.wfm.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An Erlang C calculation that REPLACES the live staffing requirements of ONE business date, plus
 * those of each {@code copyTo} date, and nothing else. The mirror of {@link ErlangXRequest}
 * without the two fields Erlang C has no notion of: callers here wait forever and never retry.
 *
 * <p><b>Percentages, not fractions</b> — {@code serviceLevelTarget} is {@code 80} for 80%, matching
 * {@link ErlangXRequest} so one screen speaks one convention. The service converts before calling
 * {@link com.wfm.calc.ErlangC}, which takes a fraction and rejects anything above 1.
 *
 * <p>The interval is NOT a field here: it comes from each row's own timeslot, so a figure typed
 * against a 15-minute slot is converted on fifteen minutes.
 *
 * <p>A request without a {@code businessDate} is refused with 400 before anything is deleted. An
 * old client that still sends {@code from}/{@code to} has those fields ignored and is refused the
 * same way, so it can never trigger a period-wide replace.
 *
 * @param businessDate the one business date whose live requirements are replaced; every item's
 *                     timeslot must belong to it
 * @param parameters   one entry per timeslot and specialization being calculated
 * @param adjustments  shrinkage, occupancy ceiling and concurrency; null means none of them
 * @param copyTo       optional (null or empty means none): other business dates that receive the
 *                     source date's per-slot result, matched by equal start and end time. Only
 *                     these dates are replaced; dates not listed are unchanged
 */
public record ErlangCRequest(
        LocalDate businessDate,
        List<Item> parameters,
        StaffingAdjustmentOptionsDto adjustments,
        List<LocalDate> copyTo
) {
    /**
     * @param callVolume            contacts arriving IN the timeslot
     * @param aht                   average handling time in seconds
     * @param serviceLevelTarget    target percentage answered within the threshold (0-100)
     * @param serviceLevelThreshold the "within", in seconds
     */
    public record Item(
            UUID timeslotId,
            UUID specializationId,
            int callVolume,
            double aht,
            double serviceLevelTarget,
            int serviceLevelThreshold
    ) {}
}
