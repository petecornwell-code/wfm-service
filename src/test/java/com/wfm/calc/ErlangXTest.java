package com.wfm.calc;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ErlangX} — the M/M/n+M chain, its retrial fixed point, and the properties that keep it
 * anchored to {@link ErlangC}.
 *
 * <p>Erlang A has no short table of memorable reference values the way Erlang C does, so most of
 * these are PROPERTY tests rather than point comparisons: the reduction to Erlang C as patience
 * grows, monotonicity in patience and in retrials, and the direction each convention moves
 * headcount. Properties are the stronger test here — they hold for every input, and they are what
 * actually broke in the implementation these replace.
 */
class ErlangXTest {

    private static Offset<Double> within(double d) {
        return Offset.offset(d);
    }

    /** 10 Erlangs, as in ErlangCTest, so the two can be compared directly. */
    private static ErlangX.Input input(double patienceSeconds, double retryFraction) {
        return new ErlangX.Input(100, 30, 180, patienceSeconds, retryFraction, 0.80, 20, false);
    }

    @Test
    @DisplayName("with infinite patience it IS Erlang C, not an approximation of it")
    void reducesToErlangCWhenNobodyAbandons() {
        ErlangX.Result x = ErlangX.requiredAgents(input(Double.POSITIVE_INFINITY, 0));
        ErlangC.Result c = ErlangC.requiredAgents(new ErlangC.Input(100, 30, 180, 0.80, 20));

        assertThat(x.agents()).isEqualTo(c.agents()).isEqualTo(14);
        assertThat(x.probabilityOfAbandon()).isZero();
        assertThat(x.serviceLevel()).isCloseTo(c.serviceLevel(), within(1e-12));
        assertThat(x.offeredLoad()).isEqualTo(c.offeredLoad());
    }

    @Test
    @DisplayName("the service-level approximation converges on Erlang C as patience grows")
    void serviceLevelConvergesOnErlangC() {
        double erlangC = ErlangC.serviceLevel(14, 10.0, 180, 20);
        double p100 = ErlangX.serviceLevel(14, 10.0, 180, 20, 100, false);
        double p10_000 = ErlangX.serviceLevel(14, 10.0, 180, 20, 10_000, false);
        double p1_000_000 = ErlangX.serviceLevel(14, 10.0, 180, 20, 1_000_000, false);

        // Monotone approach, and close enough to be indistinguishable at the limit.
        assertThat(Math.abs(p1_000_000 - erlangC)).isLessThan(Math.abs(p10_000 - erlangC));
        assertThat(Math.abs(p10_000 - erlangC)).isLessThan(Math.abs(p100 - erlangC));
        assertThat(p1_000_000).isCloseTo(erlangC, within(1e-4));
    }

    @Test
    @DisplayName("impatience relieves the queue, so it never needs MORE agents than Erlang C")
    void impatienceNeverCostsMoreThanErlangC() {
        int erlangC = ErlangC.requiredAgents(new ErlangC.Input(100, 30, 180, 0.80, 20)).agents();
        for (double patience : new double[] {15, 30, 60, 120, 300}) {
            assertThat(ErlangX.requiredAgents(input(patience, 0)).agents())
                    .as("patience %.0fs", patience)
                    .isLessThanOrEqualTo(erlangC);
        }
    }

    @Test
    @DisplayName("less patience means more abandonment, monotonically")
    void abandonmentRisesAsPatienceFalls() {
        double prev = -1;
        for (double patience : new double[] {600, 300, 120, 60, 30, 15}) {
            double pAbandon = ErlangX.solveChain(12, 10.0, 180.0 / patience).probabilityOfAbandon();
            assertThat(pAbandon).as("patience %.0fs", patience).isGreaterThan(prev);
            prev = pAbandon;
        }
        assertThat(prev).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("the chain stays solvable when load EXCEEDS headcount — Erlang C cannot do this")
    void handlesOverloadWhereErlangCDivergesToInfinity() {
        // 20 Erlangs on 10 agents. Without abandonment this queue grows without bound and Erlang C
        // reports only "everyone waits, forever". With impatience it reaches a real steady state.
        ErlangX.ChainState overload = ErlangX.solveChain(10, 20.0, 180.0 / 60.0);
        assertThat(overload.probabilityOfWait()).isGreaterThan(0.9);
        assertThat(overload.probabilityOfAbandon()).isBetween(0.3, 0.7);
        assertThat(overload.expectedQueueLength()).isFinite().isGreaterThan(0.0);

        assertThat(ErlangC.probabilityOfWait(10, 20.0)).isEqualTo(1.0);
        assertThat(ErlangC.averageSpeedOfAnswer(10, 20.0, 180)).isInfinite();
    }

    @Test
    @DisplayName("retrials inflate the offered load above the first-attempt load")
    void retrialsInflateLoad() {
        ErlangX.Result none = ErlangX.requiredAgents(input(30, 0.0));
        ErlangX.Result half = ErlangX.requiredAgents(input(30, 0.5));
        ErlangX.Result all = ErlangX.requiredAgents(input(30, 1.0));

        assertThat(none.offeredLoad()).isEqualTo(none.baseOfferedLoad());
        assertThat(half.offeredLoad()).isGreaterThan(half.baseOfferedLoad());
        assertThat(all.offeredLoad()).isGreaterThan(half.offeredLoad());
        assertThat(all.agents()).isGreaterThanOrEqualTo(none.agents());
    }

    @Test
    @DisplayName("the retrial fixed point converges, rather than oscillating to the iteration cap")
    void retrialFixedPointConverges() {
        // The predecessor iterated on the AGENT COUNT and stopped when two successive integers
        // matched, which can flip between adjacent values forever. A load fixed point cannot.
        ErlangX.Result r = ErlangX.requiredAgents(input(20, 0.9));
        assertThat(r.retrialIterations()).isLessThan(20);
        assertThat(r.offeredLoad()).isFinite();
    }

    @Test
    @DisplayName("counting abandons as answered lowers headcount — the bias worth choosing deliberately")
    void abandonConventionMovesHeadcountDown() {
        ErlangX.Input strict = new ErlangX.Input(100, 30, 180, 30, 0, 0.80, 20, false);
        ErlangX.Input lenient = new ErlangX.Input(100, 30, 180, 30, 0, 0.80, 20, true);

        assertThat(ErlangX.requiredAgents(lenient).agents())
                .isLessThanOrEqualTo(ErlangX.requiredAgents(strict).agents());
    }

    @Test
    @DisplayName("interval length drives load here too, not a hardcoded hour")
    void intervalLengthIsHonoured() {
        assertThat(input(30, 0).baseOfferedLoad()).isEqualTo(10.0);
        assertThat(new ErlangX.Input(100, 60, 180, 30, 0, 0.80, 20, false).baseOfferedLoad())
                .isEqualTo(5.0);
        assertThat(new ErlangX.Input(100, 15, 180, 30, 0, 0.80, 20, false).baseOfferedLoad())
                .isEqualTo(20.0);
    }

    @Test
    @DisplayName("no volume needs no agents")
    void zeroVolume() {
        ErlangX.Result r = ErlangX.requiredAgents(
                new ErlangX.Input(0, 30, 180, 30, 0.5, 0.80, 20, false));
        assertThat(r.agents()).isZero();
        assertThat(r.serviceLevel()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("percentages where fractions belong are rejected")
    void rejectsPercentages() {
        assertThatThrownBy(() -> new ErlangX.Input(100, 30, 180, 30, 50, 0.8, 20, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryFraction");
        assertThatThrownBy(() -> new ErlangX.Input(100, 30, 180, 30, 0.5, 80, 20, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceLevelTarget");
    }

    @Test
    @DisplayName("the stationary distribution is a distribution — probabilities stay in range")
    void chainProducesValidProbabilities() {
        for (int n : new int[] {1, 5, 10, 14, 30}) {
            for (double load : new double[] {0.5, 5, 10, 25}) {
                ErlangX.ChainState c = ErlangX.solveChain(n, load, 180.0 / 45.0);
                assertThat(c.probabilityOfWait()).as("n=%d load=%.1f", n, load).isBetween(0.0, 1.0);
                assertThat(c.probabilityOfAbandon()).as("n=%d load=%.1f", n, load).isBetween(0.0, 1.0);
                assertThat(c.expectedQueueLength()).isFinite().isGreaterThanOrEqualTo(0.0);
            }
        }
    }

    // ------------------------------------------------------------------------
    //  The abandonment convention. Both branches must answer the same shape of
    //  question: "what fraction of callers got served in time". Until 2026-09-25
    //  the lenient branch answered "how many callers went away, by any means",
    //  which is not a service level at all.
    // ------------------------------------------------------------------------

    /** 200 contacts an hour at 300 s AHT = 16.7 Erlangs, 90 s patience, 20 s threshold. */
    private static final double FIXTURE_LOAD = 200 * 300 / 3600.0;
    private static final double FIXTURE_AHT = 300;
    private static final double FIXTURE_PATIENCE = 90;
    private static final int FIXTURE_THRESHOLD = 20;

    private static double slLenient(int agents) {
        return ErlangX.serviceLevel(agents, FIXTURE_LOAD, FIXTURE_AHT, FIXTURE_THRESHOLD,
                FIXTURE_PATIENCE, true);
    }

    private static double slStrict(int agents) {
        return ErlangX.serviceLevel(agents, FIXTURE_LOAD, FIXTURE_AHT, FIXTURE_THRESHOLD,
                FIXTURE_PATIENCE, false);
    }

    @Test
    @DisplayName("excluding abandons never scores a collapsing queue as a success")
    void lenientConventionIsNotDegenerate() {
        // The regression in one line: one agent against 16.7 Erlangs used to report 94%, because
        // 94% of callers hung up and every hang-up was counted as answered. A service level that
        // improves as the service collapses makes the agent search stop at one agent for any load.
        assertThat(slLenient(1)).isLessThan(0.50);
        assertThat(slLenient(2)).isLessThan(0.50);
        assertThat(slLenient(5)).isLessThan(0.50);

        // And the answer the search actually returns is a real headcount, not 1.
        ErlangX.Result r = ErlangX.requiredAgents(new ErlangX.Input(
                200, 60, FIXTURE_AHT, FIXTURE_PATIENCE, 0.25, 0.80, FIXTURE_THRESHOLD, true));
        assertThat(r.agents()).isGreaterThan(10);
    }

    @Test
    @DisplayName("service level rises with headcount under BOTH conventions")
    void serviceLevelIsMonotonicInAgents() {
        // Monotonicity is the property the old form broke, and it is what makes the upward search
        // for the smallest adequate headcount meaningful at all.
        for (int n = 1; n < 40; n++) {
            assertThat(slStrict(n + 1))
                    .as("strict service level at %d vs %d agents", n + 1, n)
                    .isGreaterThanOrEqualTo(slStrict(n) - 1e-12);
            assertThat(slLenient(n + 1))
                    .as("lenient service level at %d vs %d agents", n + 1, n)
                    .isGreaterThanOrEqualTo(slLenient(n) - 1e-12);
        }
    }

    @Test
    @DisplayName("excluding abandons is never harsher than counting them against the target")
    void lenientIsAlwaysAtLeastStrict() {
        // A smaller denominator can only raise the fraction. If this ever inverted, the two
        // branches would be answering different questions again.
        for (int n = 1; n < 40; n++) {
            assertThat(slLenient(n)).isGreaterThanOrEqualTo(slStrict(n) - 1e-12);
            assertThat(slLenient(n)).isBetween(0.0, 1.0);
            assertThat(slStrict(n)).isBetween(0.0, 1.0);
        }
    }

    @Test
    @DisplayName("with no abandonment the two conventions are the same number")
    void conventionsAgreeWhenNobodyAbandons() {
        // Patience an hour long against a well-staffed queue: P(abandon) is negligible, so there
        // is nothing to include or exclude and the choice must stop mattering.
        double lenient = ErlangX.serviceLevel(30, FIXTURE_LOAD, FIXTURE_AHT, FIXTURE_THRESHOLD, 3600, true);
        double strict = ErlangX.serviceLevel(30, FIXTURE_LOAD, FIXTURE_AHT, FIXTURE_THRESHOLD, 3600, false);
        assertThat(lenient).isCloseTo(strict, within(1e-4));
    }

    @Test
    @DisplayName("excluding abandons still lowers headcount, which is why it is not the default")
    void lenientConventionStillStaffsLower() {
        ErlangX.Input strict = new ErlangX.Input(
                200, 60, FIXTURE_AHT, FIXTURE_PATIENCE, 0.25, 0.80, FIXTURE_THRESHOLD, false);
        ErlangX.Input lenient = new ErlangX.Input(
                200, 60, FIXTURE_AHT, FIXTURE_PATIENCE, 0.25, 0.80, FIXTURE_THRESHOLD, true);

        assertThat(ErlangX.requiredAgents(lenient).agents())
                .isLessThanOrEqualTo(ErlangX.requiredAgents(strict).agents());
    }
}
