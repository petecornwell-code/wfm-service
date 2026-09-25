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
}
