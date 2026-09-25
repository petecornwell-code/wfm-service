package com.wfm.dto;

/**
 * The three rostering adjustments, as a caller supplies them. Every field is nullable and every
 * null means "no adjustment", so a request that omits this block gets the raw queueing answer with
 * nothing added to it.
 *
 * <p>Nullable rather than primitive on purpose: {@code 0.0} shrinkage and "shrinkage not supplied"
 * must be indistinguishable in effect but distinguishable in the payload, and {@code maxOccupancy}
 * has no sensible numeric "off" value at all.
 *
 * @param shrinkage    fraction of paid time unavailable for contacts, in [0,1). Null or 0 disables.
 * @param maxOccupancy ceiling on contact-handling time per agent, in (0,1]. Null disables.
 * @param concurrency  contacts one agent handles at once — 1 for voice, higher for chat. Null is 1.
 */
public record StaffingAdjustmentOptionsDto(
        Double shrinkage,
        Double maxOccupancy,
        Double concurrency
) {
    /** What an omitted adjustments block means. */
    public static final StaffingAdjustmentOptionsDto NONE =
            new StaffingAdjustmentOptionsDto(null, null, null);
}
