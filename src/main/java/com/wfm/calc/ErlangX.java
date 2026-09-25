package com.wfm.calc;

/**
 * Erlang X — Erlang C extended with impatience and retrials, i.e. the M/M/n+M queue (Erlang A)
 * wrapped in a retrial fixed point.
 *
 * <p><b>Abandonment is computed from the chain, not approximated.</b> The stationary distribution of
 * M/M/n+M is solved directly: below {@code n} servers the birth-death ratios are Erlang's, and above
 * it each extra waiting caller adds an independent abandonment hazard, so the departure rate at
 * queue position {@code j} is {@code n*mu + j*theta}. From that distribution the expected queue
 * length gives the abandonment rate exactly, as {@code theta * E[Lq] / lambda}.
 *
 * <p><b>The within-threshold service level IS an approximation, and it is named as one.</b> The
 * waiting-time distribution of M/M/n+M is phase-type and has no tidy closed form. This uses the
 * standard practical form — the queue drains at the service surplus PLUS the abandonment hazard:
 * <pre>P(W &gt; t) = P(wait) * exp(-((n - A)/AHT + 1/patience) * t)</pre>
 * It reduces exactly to Erlang C as patience goes to infinity, which is the property that makes it
 * safe to use as the one model for both cases. {@link ErlangCComparison} exists so that reduction is
 * asserted rather than assumed.
 *
 * <p><b>Abandoned callers are not counted as answered.</b> {@link Input#countAbandonsAsAnswered()}
 * can switch to the other industry convention, but it defaults false: counting a caller who hung up
 * as a success raises the reported service level and therefore LOWERS required headcount, and the
 * predecessor of this class did exactly that while also omitting shrinkage and any occupancy
 * ceiling — three independent simplifications all pushing headcount down, which compounds rather
 * than cancels.
 *
 * <p>Pure and stateless, like {@link ErlangC}, whose primitives it reuses rather than re-deriving.
 */
public final class ErlangX {

    /** Queue depth past which the tail contributes nothing measurable. Abandonment guarantees decay. */
    private static final int MAX_QUEUE_DEPTH = 20_000;
    private static final double TAIL_EPSILON = 1e-14;
    private static final int MAX_RETRIAL_ITERATIONS = 100;
    private static final double RETRIAL_TOLERANCE = 1e-9;

    private ErlangX() {
    }

    /**
     * @param volume                       contacts arriving in the interval, before retrials
     * @param intervalMinutes              interval length — the conversion Erlang C's predecessor hardcoded
     * @param ahtSeconds                   average handling time
     * @param patienceSeconds              mean time before an unanswered caller abandons; {@code <= 0}
     *                                     or infinite means infinitely patient, i.e. pure Erlang C
     * @param retryFraction                fraction of abandoned callers who call back, in [0,1]
     * @param serviceLevelTarget           fraction to answer within the threshold
     * @param serviceLevelThresholdSeconds the "within"
     * @param countAbandonsAsAnswered      see the class note; defaults matter here
     */
    public record Input(double volume, int intervalMinutes, double ahtSeconds,
                        double patienceSeconds, double retryFraction,
                        double serviceLevelTarget, int serviceLevelThresholdSeconds,
                        boolean countAbandonsAsAnswered) {

        public Input {
            if (intervalMinutes <= 0) {
                throw new IllegalArgumentException("intervalMinutes must be positive: " + intervalMinutes);
            }
            if (retryFraction < 0 || retryFraction > 1) {
                throw new IllegalArgumentException(
                        "retryFraction is a fraction in [0,1], not a percentage: " + retryFraction);
            }
            if (serviceLevelTarget < 0 || serviceLevelTarget > 1) {
                throw new IllegalArgumentException(
                        "serviceLevelTarget is a fraction in [0,1], not a percentage: " + serviceLevelTarget);
            }
        }

        /** Offered load in Erlangs from the raw volume, before any retrial inflation. */
        public double baseOfferedLoad() {
            return volume * ahtSeconds / (intervalMinutes * 60.0);
        }

        boolean infinitelyPatient() {
            return patienceSeconds <= 0 || Double.isInfinite(patienceSeconds);
        }
    }

    /**
     * @param agents                  smallest headcount meeting the target
     * @param offeredLoad             load INCLUDING converged retrials
     * @param baseOfferedLoad         load from first-attempt contacts only
     * @param probabilityOfWait       P(a caller has to wait at all)
     * @param probabilityOfAbandon    P(a caller abandons), exact from the chain
     * @param serviceLevel            fraction answered within the threshold, per the chosen convention
     * @param occupancy               fraction of paid time handling contacts, net of abandons
     * @param retrialIterations       fixed-point iterations used; high values mean a fragile input
     */
    public record Result(int agents, double offeredLoad, double baseOfferedLoad,
                         double probabilityOfWait, double probabilityOfAbandon,
                         double serviceLevel, double occupancy, int retrialIterations) {
    }

    /** The stationary quantities of M/M/n+M at a given headcount and load. */
    record ChainState(double probabilityOfWait, double expectedQueueLength,
                      double probabilityOfAbandon) {
    }

    /**
     * Solves the M/M/n+M stationary distribution at {@code agents} for {@code offeredLoad}.
     *
     * <p>Ratios are accumulated relative to {@code p(0)} and normalised at the end, so nothing is
     * ever divided by a factorial. Working in ratios also keeps the unstable case sane: with
     * abandonment there IS no unstable case, because the queue's departure rate grows without bound
     * as it lengthens — which is exactly why this model can answer questions Erlang C cannot, such
     * as what happens when load exceeds headcount.
     *
     * @param beta {@code AHT / patience} — the abandonment hazard in service-time units
     */
    static ChainState solveChain(int agents, double offeredLoad, double beta) {
        // r[k] = p(k)/p(0). Below the server count the ratio is Erlang's A/k.
        double sumBelow = 1.0;          // k = 0 term
        double r = 1.0;
        for (int k = 1; k <= agents; k++) {
            r *= offeredLoad / k;
            sumBelow += r;
        }
        double rAtN = r;                // all servers busy, nobody queued

        // Above the server count, position j faces n*mu + j*theta, i.e. A / (n + j*beta).
        double sumQueue = 0.0;          // sum of r over queued states
        double weightedQueue = 0.0;     // sum of j * r, for E[Lq]
        double term = rAtN;
        for (int j = 1; j <= MAX_QUEUE_DEPTH; j++) {
            term *= offeredLoad / (agents + j * beta);
            sumQueue += term;
            weightedQueue += j * term;
            if (term < TAIL_EPSILON * (sumBelow + sumQueue)) {
                break;
            }
        }

        double total = sumBelow + sumQueue;
        // P(wait) is P(all servers busy) — the state with n busy and nobody queued counts, since
        // that caller still waits for a service completion. PASTA makes the time average the
        // arriving customer's view.
        double pWait = (rAtN + sumQueue) / total;
        double eLq = weightedQueue / total;
        // Abandonment rate is theta * E[Lq]; dividing by lambda gives the per-caller probability.
        // In AHT units theta = beta/AHT and lambda = A/AHT, so the AHT cancels.
        double pAbandon = offeredLoad <= 0 ? 0.0 : Math.min(1.0, eLq * beta / offeredLoad);
        return new ChainState(pWait, eLq, pAbandon);
    }

    /**
     * Fraction answered within the threshold. See the class note: the chain gives abandonment
     * exactly, this decay is the standard approximation and reduces to Erlang C when patience is
     * infinite.
     */
    static double serviceLevel(int agents, double offeredLoad, double ahtSeconds,
                               double thresholdSeconds, double patienceSeconds,
                               boolean countAbandonsAsAnswered) {
        if (patienceSeconds <= 0 || Double.isInfinite(patienceSeconds)) {
            return ErlangC.serviceLevel(agents, offeredLoad, ahtSeconds, thresholdSeconds);
        }
        double beta = ahtSeconds / patienceSeconds;
        ChainState chain = solveChain(agents, offeredLoad, beta);

        double surplusRate = (agents - offeredLoad) / ahtSeconds;   // may be negative under overload
        double drainRate = surplusRate + 1.0 / patienceSeconds;
        double pWaitBeyond = drainRate <= 0
                ? chain.probabilityOfWait()
                : chain.probabilityOfWait() * Math.exp(-drainRate * thresholdSeconds);

        double answeredWithin = 1.0 - pWaitBeyond;
        if (countAbandonsAsAnswered) {
            // The other convention: a caller who hung up before the threshold is not counted
            // against the target. Raises SL and lowers headcount — chosen, never defaulted.
            return Math.min(1.0, answeredWithin + chain.probabilityOfAbandon());
        }
        return Math.max(0.0, Math.min(1.0, answeredWithin - chain.probabilityOfAbandon()));
    }

    /**
     * Smallest headcount meeting the target, with retrials resolved to a fixed point first.
     *
     * <p>Retrials are solved on the LOAD, not on the agent count. The predecessor recomputed
     * staffing inside the loop and stopped when two successive integers matched, which can oscillate
     * between adjacent values and never settle; a load fixed point converges monotonically because
     * each pass adds a strictly smaller increment. The iteration count is returned so a fragile
     * input is visible rather than silent.
     */
    public static Result requiredAgents(Input in) {
        double base = in.baseOfferedLoad();
        if (base <= 0) {
            return new Result(0, 0, 0, 0, 0, 1.0, 0, 0);
        }
        if (in.infinitelyPatient()) {
            // No abandonment means no retrials either: Erlang C is not an approximation here, it
            // is the same model. Delegating keeps one implementation of that case.
            ErlangC.Result c = ErlangC.requiredAgents(new ErlangC.Input(
                    in.volume(), in.intervalMinutes(), in.ahtSeconds(),
                    in.serviceLevelTarget(), in.serviceLevelThresholdSeconds()));
            return new Result(c.agents(), c.offeredLoad(), base, c.probabilityOfWait(), 0.0,
                    c.serviceLevel(), c.occupancy(), 0);
        }

        double beta = in.ahtSeconds() / in.patienceSeconds();
        double load = base;
        int iterations = 0;

        if (in.retryFraction() > 0) {
            for (; iterations < MAX_RETRIAL_ITERATIONS; iterations++) {
                int trialAgents = searchAgents(in, load, beta);
                double pAbandon = solveChain(trialAgents, load, beta).probabilityOfAbandon();
                double next = base / (1.0 - pAbandon * in.retryFraction());
                if (Math.abs(next - load) <= RETRIAL_TOLERANCE * load) {
                    load = next;
                    break;
                }
                load = next;
            }
        }

        int agents = searchAgents(in, load, beta);
        ChainState chain = solveChain(agents, load, beta);
        double served = load * (1.0 - chain.probabilityOfAbandon());
        return new Result(agents, load, base, chain.probabilityOfWait(),
                chain.probabilityOfAbandon(),
                serviceLevel(agents, load, in.ahtSeconds(), in.serviceLevelThresholdSeconds(),
                        in.patienceSeconds(), in.countAbandonsAsAnswered()),
                agents <= 0 ? 0.0 : served / agents, iterations);
    }

    /**
     * Smallest headcount meeting the target at a fixed load. Starts at 1, not at {@code floor(A)+1}:
     * with abandonment a queue is stable at any headcount, so understaffed answers are legitimate
     * and reachable — that is precisely what this model is for.
     */
    private static int searchAgents(Input in, double load, double beta) {
        for (int n = 1; n <= ErlangC.MAX_AGENTS; n++) {
            double sl = serviceLevel(n, load, in.ahtSeconds(), in.serviceLevelThresholdSeconds(),
                    in.patienceSeconds(), in.countAbandonsAsAnswered());
            if (sl >= in.serviceLevelTarget()) {
                return n;
            }
        }
        throw new IllegalStateException(
                "No agent count up to " + ErlangC.MAX_AGENTS + " meets a service level of "
                        + in.serviceLevelTarget() + " for an offered load of " + load + " Erlangs");
    }

    /** Marker for the test that asserts this model reduces to Erlang C as patience grows. */
    interface ErlangCComparison {
    }
}
