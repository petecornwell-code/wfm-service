package com.wfm.calc;

/**
 * Erlang C — the classical M/M/n queue: callers arrive Poisson, are served exponentially, and wait
 * forever. No abandonment, no retrials. This is the conservative baseline every other staffing
 * model is compared against, and the primitives here ({@link #erlangB}, {@link #probabilityOfWait})
 * are shared with {@link ErlangX} rather than re-derived there.
 *
 * <p><b>Interval length is an explicit input, not an assumption.</b> Offered load is
 * {@code volume * aht / (intervalMinutes * 60)}, so a half-hour interval carrying 100 calls is
 * twice the load of an hour carrying 100. The predecessor of this class hardcoded a 3600-second
 * divisor while its caller supplied per-interval volumes, which was correct only at 60-minute
 * intervals and understated load by the interval ratio everywhere else.
 *
 * <p><b>Pure and stateless</b> — no Spring, no repositories, nothing to mock. Every method is a
 * function of its arguments, which is what makes the reference values in {@code ErlangCTest}
 * meaningful as a regression net.
 */
public final class ErlangC {

    /** Above this many agents the search gives up rather than looping forever. */
    static final int MAX_AGENTS = 10_000;

    private ErlangC() {
    }

    /**
     * What a staffing calculation was asked, in the units the caller actually has.
     *
     * @param volume                  contacts arriving in the interval (not a per-hour rate)
     * @param intervalMinutes         length of that interval — 15, 30 and 60 are all normal
     * @param ahtSeconds              average handling time, including wrap-up
     * @param serviceLevelTarget      fraction to answer within the threshold, e.g. {@code 0.80}
     * @param serviceLevelThresholdSeconds the "within" in "80% within 20 seconds"
     */
    public record Input(double volume, int intervalMinutes, double ahtSeconds,
                        double serviceLevelTarget, int serviceLevelThresholdSeconds) {

        public Input {
            if (intervalMinutes <= 0) {
                throw new IllegalArgumentException("intervalMinutes must be positive: " + intervalMinutes);
            }
            if (serviceLevelTarget < 0 || serviceLevelTarget > 1) {
                throw new IllegalArgumentException(
                        "serviceLevelTarget is a fraction in [0,1], not a percentage: " + serviceLevelTarget);
            }
        }

        /** Offered load in Erlangs. THE interval conversion — nothing else divides by seconds. */
        public double offeredLoad() {
            return volume * ahtSeconds / (intervalMinutes * 60.0);
        }
    }

    /**
     * The answer plus the diagnostics needed to judge it. Occupancy and ASA are what tell an
     * operator whether a staffing number is liveable, not just whether it hits the target.
     *
     * @param agents                 smallest headcount meeting the target
     * @param offeredLoad            Erlangs offered
     * @param probabilityOfWait      P(a caller waits at all) at {@code agents}
     * @param serviceLevel           fraction answered within the threshold at {@code agents}
     * @param averageSpeedOfAnswerSeconds mean wait across all callers
     * @param occupancy              fraction of paid time spent handling contacts
     */
    public record Result(int agents, double offeredLoad, double probabilityOfWait,
                         double serviceLevel, double averageSpeedOfAnswerSeconds,
                         double occupancy) {
    }

    /**
     * Erlang B (blocking probability) by the Jagerman recursion, which is overflow-safe: the
     * closed form needs {@code A^n / n!} and both halves leave double range well before the agent
     * counts this codebase reaches.
     */
    public static double erlangB(int agents, double offeredLoad) {
        if (agents <= 0 || offeredLoad <= 0) {
            return offeredLoad <= 0 ? 0.0 : 1.0;
        }
        double b = 1.0;
        for (int i = 1; i <= agents; i++) {
            b = (offeredLoad * b) / (i + offeredLoad * b);
        }
        return b;
    }

    /**
     * P(wait &gt; 0) — the Erlang C probability. Returns 1 when the offered load meets or exceeds
     * the agent count: the queue is unstable and grows without bound, so everyone waits.
     */
    public static double probabilityOfWait(int agents, double offeredLoad) {
        if (agents <= 0 || offeredLoad <= 0) {
            return 0.0;
        }
        if (agents <= offeredLoad) {
            return 1.0;
        }
        double b = erlangB(agents, offeredLoad);
        double rho = offeredLoad / agents;
        return b / (1.0 - rho * (1.0 - b));
    }

    /** Fraction answered within {@code thresholdSeconds}: {@code 1 - C * e^(-(n-A)t/AHT)}. */
    public static double serviceLevel(int agents, double offeredLoad, double ahtSeconds,
                                      double thresholdSeconds) {
        if (agents <= offeredLoad) {
            return 0.0;
        }
        double c = probabilityOfWait(agents, offeredLoad);
        double decay = (agents - offeredLoad) * thresholdSeconds / ahtSeconds;
        return 1.0 - c * Math.exp(-decay);
    }

    /** Mean wait over ALL callers, answered immediately or not: {@code C * AHT / (n - A)}. */
    public static double averageSpeedOfAnswer(int agents, double offeredLoad, double ahtSeconds) {
        if (agents <= offeredLoad) {
            return Double.POSITIVE_INFINITY;
        }
        return probabilityOfWait(agents, offeredLoad) * ahtSeconds / (agents - offeredLoad);
    }

    /** Occupancy — the fraction of paid agent time spent on contacts. */
    public static double occupancy(int agents, double offeredLoad) {
        return agents <= 0 ? 0.0 : offeredLoad / agents;
    }

    /**
     * Smallest headcount meeting the service-level target, with the diagnostics for that headcount.
     *
     * <p>Starts at {@code floor(A) + 1} because Erlang C requires {@code n > A} strictly — at
     * {@code n = A} the queue is unstable and no service level is achievable. Zero volume or zero
     * AHT needs nobody, which is a real answer rather than an edge case to reject.
     */
    public static Result requiredAgents(Input in) {
        double a = in.offeredLoad();
        if (a <= 0) {
            return new Result(0, 0, 0, 1.0, 0, 0);
        }
        int start = (int) Math.floor(a) + 1;
        for (int n = start; n <= MAX_AGENTS; n++) {
            double sl = serviceLevel(n, a, in.ahtSeconds(), in.serviceLevelThresholdSeconds());
            if (sl >= in.serviceLevelTarget()) {
                return new Result(n, a, probabilityOfWait(n, a), sl,
                        averageSpeedOfAnswer(n, a, in.ahtSeconds()), occupancy(n, a));
            }
        }
        throw new IllegalStateException(
                "No agent count up to " + MAX_AGENTS + " meets a service level of "
                        + in.serviceLevelTarget() + " for an offered load of " + a + " Erlangs");
    }
}
