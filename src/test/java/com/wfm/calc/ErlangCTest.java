package com.wfm.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ErlangC} pinned against hand-computed values, so the maths is fixed rather than merely
 * exercised.
 *
 * <p>The fixture: 100 calls in a half hour at 180 s AHT is 10 Erlangs. Every expected value below
 * was computed independently from the Erlang B recursion and the Erlang C formula, NOT read off a
 * remembered table — a first draft of this test asserted a widely-quoted "12 agents" that turns out
 * to belong to a different AHT/threshold ratio, and it failed against a correct implementation.
 * The boundary is genuinely tight here: 13 agents reach 0.7956 and 14 reach 0.8884, so a target of
 * 0.80 lands between two integers and makes an off-by-one immediately visible.
 */
class ErlangCTest {

    /** 100 calls / 30 min at 180s AHT = 100*180/1800 = 10 Erlangs. */
    private static ErlangC.Input canonical() {
        return new ErlangC.Input(100, 30, 180, 0.80, 20);
    }

    @Test
    @DisplayName("offered load divides by the interval, not by a hardcoded hour")
    void offeredLoadUsesTheInterval() {
        assertThat(canonical().offeredLoad()).isEqualTo(10.0);
        // The same 100 calls over an hour is half the load — the distinction the old
        // implementation could not express, because it always divided by 3600.
        assertThat(new ErlangC.Input(100, 60, 180, 0.80, 20).offeredLoad()).isEqualTo(5.0);
        // ...and over 15 minutes, double it.
        assertThat(new ErlangC.Input(100, 15, 180, 0.80, 20).offeredLoad()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("Erlang B matches the textbook value for 10 Erlangs on 12 servers")
    void erlangBReferenceValue() {
        assertThat(ErlangC.erlangB(12, 10.0)).isCloseTo(0.119739, within(1e-6));
    }

    @Test
    @DisplayName("P(wait) for 10 Erlangs on 12 agents is 0.4494")
    void probabilityOfWaitReferenceValue() {
        assertThat(ErlangC.probabilityOfWait(12, 10.0)).isCloseTo(0.449388, within(1e-6));
        assertThat(ErlangC.probabilityOfWait(14, 10.0)).isCloseTo(0.174131, within(1e-6));
    }

    @Test
    @DisplayName("the 10-Erlang / 80-in-20 problem needs 14 agents, and 13 is genuinely short")
    void canonicalRequiredAgents() {
        ErlangC.Result r = ErlangC.requiredAgents(canonical());
        assertThat(r.agents()).isEqualTo(14);
        assertThat(r.offeredLoad()).isEqualTo(10.0);
        assertThat(r.serviceLevel()).isCloseTo(0.888349, within(1e-5));
        // The agent below must miss the target, or "smallest" is not what is being returned. This
        // one misses by four thousandths, which is the tightest possible check on the boundary.
        assertThat(ErlangC.serviceLevel(13, 10.0, 180, 20)).isCloseTo(0.795590, within(1e-5));
        assertThat(ErlangC.serviceLevel(13, 10.0, 180, 20)).isLessThan(0.80);
    }

    @Test
    @DisplayName("interval length changes the answer, which is the whole point of the fix")
    void intervalLengthChangesTheAnswer() {
        // Same 100 calls, same AHT, same target — three different intervals, three different loads.
        int perHour = ErlangC.requiredAgents(new ErlangC.Input(100, 60, 180, 0.80, 20)).agents();
        int perHalfHour = ErlangC.requiredAgents(new ErlangC.Input(100, 30, 180, 0.80, 20)).agents();
        int perQuarter = ErlangC.requiredAgents(new ErlangC.Input(100, 15, 180, 0.80, 20)).agents();
        assertThat(perHour).isLessThan(perHalfHour);
        assertThat(perHalfHour).isLessThan(perQuarter);
        // The old code would have returned the 60-minute answer for all three.
        assertThat(perQuarter).isGreaterThan(perHour + 5);
    }

    @Test
    @DisplayName("an unstable queue reports everyone waiting and no service level")
    void unstableQueue() {
        assertThat(ErlangC.probabilityOfWait(10, 10.0)).isEqualTo(1.0);
        assertThat(ErlangC.serviceLevel(10, 10.0, 180, 20)).isZero();
        assertThat(ErlangC.averageSpeedOfAnswer(10, 10.0, 180)).isInfinite();
    }

    @Test
    @DisplayName("staffing never starts below the offered load, since n must exceed A")
    void staffingExceedsOfferedLoad() {
        ErlangC.Result r = ErlangC.requiredAgents(new ErlangC.Input(600, 60, 300, 0.80, 20));
        assertThat(r.offeredLoad()).isEqualTo(50.0);
        assertThat(r.agents()).isGreaterThan(50);
        assertThat(r.occupancy()).isLessThan(1.0);
    }

    @Test
    @DisplayName("no volume needs no agents, and that is an answer rather than an error")
    void zeroVolume() {
        ErlangC.Result r = ErlangC.requiredAgents(new ErlangC.Input(0, 60, 180, 0.80, 20));
        assertThat(r.agents()).isZero();
        assertThat(r.serviceLevel()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a percentage passed where a fraction belongs is rejected, not silently used")
    void rejectsPercentageTargets() {
        assertThatThrownBy(() -> new ErlangC.Input(100, 30, 180, 80, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fraction");
        assertThatThrownBy(() -> new ErlangC.Input(100, 0, 180, 0.8, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intervalMinutes");
    }

    @Test
    @DisplayName("ASA and occupancy are reported, because a target alone does not say if it is liveable")
    void diagnostics() {
        ErlangC.Result r = ErlangC.requiredAgents(canonical());
        // 14 agents on 10 Erlangs: ASA 7.8 s, occupancy 71.4%.
        assertThat(r.averageSpeedOfAnswerSeconds()).isCloseTo(7.83, within(0.01));
        assertThat(r.occupancy()).isCloseTo(10.0 / 14.0, within(1e-9));
    }

    private static org.assertj.core.data.Offset<Double> within(double d) {
        return org.assertj.core.data.Offset.offset(d);
    }
}
