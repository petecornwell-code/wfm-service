package com.wfm.dto;

/**
 * An Erlang C question, asked directly rather than through a desk's stored timeslots.
 *
 * <p>Interval length is an explicit field because {@code volume} is contacts IN the interval, not a
 * per-hour rate: 100 contacts in a quarter hour is four times the load of 100 in an hour.
 *
 * @param volume                        contacts arriving in the interval
 * @param intervalMinutes               length of that interval — 15, 30 and 60 are all normal
 * @param ahtSeconds                    average handling time, including wrap-up
 * @param serviceLevelTarget            fraction to answer within the threshold, e.g. 0.80
 * @param serviceLevelThresholdSeconds  the "within" in "80% within 20 seconds"
 * @param adjustments                   rostering adjustments; null means none
 */
public record ErlangCCalculationRequest(
        double volume,
        int intervalMinutes,
        double ahtSeconds,
        double serviceLevelTarget,
        int serviceLevelThresholdSeconds,
        StaffingAdjustmentOptionsDto adjustments
) {}
