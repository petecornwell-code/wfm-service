package com.wfm.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An Erlang C calculation that REPLACES the live staffing requirements for its date range, the
 * mirror of {@link ErlangXRequest} without the two fields Erlang C has no notion of: callers here
 * wait forever and never retry.
 *
 * <p><b>Percentages, not fractions</b> — {@code serviceLevelTarget} is {@code 80} for 80%, matching
 * {@link ErlangXRequest} so one screen speaks one convention. The service converts before calling
 * {@link com.wfm.calc.ErlangC}, which takes a fraction and rejects anything above 1.
 *
 * <p>The interval is NOT a field here: it comes from each row's own timeslot, so a figure typed
 * against a 15-minute slot is converted on fifteen minutes.
 *
 * @param from       first date whose live requirements are replaced
 * @param to         last such date
 * @param parameters one entry per timeslot and specialization being calculated
 */
public record ErlangCRequest(
        LocalDate from,
        LocalDate to,
        List<Item> parameters
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
