package com.wfm.calc;

/**
 * The three inputs that turn a queueing answer into a rostering target, and which neither
 * {@link ErlangC} nor {@link ErlangX} should know about — they answer "how many agents must be
 * handling contacts", which is not the same question as "how many people must be scheduled".
 *
 * <p>Each defaults to a no-op, so a caller that says nothing gets the raw queueing answer and no
 * silent inflation. That matters because all three move headcount in the same direction, and the
 * implementation these replace omitted all three at once.
 */
public final class StaffingAdjustment {

    private StaffingAdjustment() {
    }

    /**
     * @param shrinkage     fraction of paid time NOT available for contacts — absence, training,
     *                      meetings, coaching. In [0,1); 0 disables. Note breaks are deliberately
     *                      excluded: this codebase's shift-envelope model already removes the break
     *                      window when expanding shifts into demand, so including them here would
     *                      double-count.
     * @param maxOccupancy  ceiling on the fraction of time an agent spends on contacts, e.g. 0.85.
     *                      {@code null} disables. Erlang will happily return a headcount implying
     *                      95%+ occupancy, which is arithmetically valid and unsurvivable.
     * @param concurrency   contacts one agent handles at once. 1 for voice; higher for chat. Values
     *                      below 1 are rejected rather than silently clamped.
     */
    public record Options(double shrinkage, Double maxOccupancy, double concurrency) {

        public static final Options NONE = new Options(0.0, null, 1.0);

        public Options {
            if (shrinkage < 0 || shrinkage >= 1) {
                throw new IllegalArgumentException(
                        "shrinkage is a fraction in [0,1): " + shrinkage);
            }
            if (maxOccupancy != null && (maxOccupancy <= 0 || maxOccupancy > 1)) {
                throw new IllegalArgumentException(
                        "maxOccupancy is a fraction in (0,1]: " + maxOccupancy);
            }
            if (concurrency < 1) {
                throw new IllegalArgumentException(
                        "concurrency is contacts per agent and cannot be below 1: " + concurrency);
            }
        }
    }

    /**
     * @param scheduled          the number to roster
     * @param handling           the number that must be handling contacts (the queueing answer,
     *                           after any occupancy relief)
     * @param occupancyRelief    extra agents added purely to respect the occupancy ceiling
     * @param shrinkageUplift    extra agents added purely to cover shrinkage
     */
    public record Result(int scheduled, int handling, int occupancyRelief, int shrinkageUplift) {
    }

    /**
     * Divides the offered load by concurrency — the load one agent can absorb. Applied to the LOAD
     * rather than to the answer, because a chat agent holding three conversations changes the
     * queueing problem, not just its result.
     */
    public static double effectiveLoad(double offeredLoad, Options options) {
        return offeredLoad / options.concurrency();
    }

    /**
     * Raises {@code handlingAgents} until occupancy is within the ceiling, then adds shrinkage.
     *
     * <p>Order matters and is deliberate: the occupancy ceiling is about the work, so it applies to
     * agents who are handling contacts; shrinkage is about the roster, so it applies last, to the
     * total. Reversing them would inflate the shrinkage uplift by the occupancy relief and
     * overstaff.
     */
    public static Result apply(int handlingAgents, double offeredLoad, Options options) {
        int handling = handlingAgents;
        if (options.maxOccupancy() != null && offeredLoad > 0) {
            // n must satisfy A/n <= cap, i.e. n >= A/cap.
            int needed = (int) Math.ceil(offeredLoad / options.maxOccupancy() - 1e-9);
            handling = Math.max(handling, needed);
        }
        int relief = handling - handlingAgents;

        int scheduled = options.shrinkage() <= 0
                ? handling
                : (int) Math.ceil(handling / (1.0 - options.shrinkage()) - 1e-9);
        return new Result(scheduled, handling, relief, scheduled - handling);
    }
}
