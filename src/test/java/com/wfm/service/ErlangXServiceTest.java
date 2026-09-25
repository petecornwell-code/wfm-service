package com.wfm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The first tests this class has ever had, written with the interval fix: {@code callVolume} is
 * contacts IN the timeslot, and the conversion to Erlangs now divides by the timeslot's own length
 * instead of a hardcoded hour.
 *
 * <p>Two properties carry the fix. {@link #hourlyAnswersAreUnchanged()} pins the six answers the
 * service gave BEFORE the change at a 60-minute interval, captured by running the old code — every
 * live desk is hourly, so this is the assertion that says no existing staffing number moved.
 * {@link #theFinalPassUsesTheSameDivisorAsTheLoop()} covers the half-fix: there were two divisors,
 * and repairing only the loop's leaves the converged answer on a different basis from the load that
 * produced it.
 *
 * <p>This class tests the service as it stands. It is not the same maths as {@link
 * com.wfm.calc.ErlangX}, which models abandonment from the queue chain and does not count a caller
 * who hung up as answered; the two disagree at every interval, including 60. Which one the
 * persisting endpoint should call is a live decision, not something this fix settles.
 */
class ErlangXServiceTest {

    private final ErlangXService service = new ErlangXService();

    @Test
    @DisplayName("hourly timeslots answer exactly as they did before the interval fix")
    void hourlyAnswersAreUnchanged() {
        // Captured from the pre-fix implementation, whose divisor was a hardcoded 3600. At a
        // 60-minute interval the new conversion is arithmetically the same, so every one of these
        // must still hold -- Saferide, Stubhub (EN) and Vinted were all hourly when this changed.
        assertThat(service.calculateRequiredAgents(100, 60, 180, 90, 25, 80, 20)).isEqualTo(8);
        assertThat(service.calculateRequiredAgents(100, 60, 300, 60, 0, 80, 20)).isEqualTo(11);
        assertThat(service.calculateRequiredAgents(50, 60, 240, 120, 50, 90, 30)).isEqualTo(6);
        assertThat(service.calculateRequiredAgents(1000, 60, 180, 90, 25, 80, 20)).isEqualTo(56);
        assertThat(service.calculateRequiredAgents(1, 60, 180, 90, 25, 80, 20)).isEqualTo(1);
        assertThat(service.calculateRequiredAgents(0, 60, 180, 90, 25, 80, 20)).isZero();
    }

    @Test
    @DisplayName("the same volume in a shorter timeslot needs more agents, which is the bug")
    void intervalLengthChangesTheAnswer() {
        int hourly = service.calculateRequiredAgents(100, 60, 180, 90, 25, 80, 20);
        int halfHourly = service.calculateRequiredAgents(100, 30, 180, 90, 25, 80, 20);
        int quarterHourly = service.calculateRequiredAgents(100, 15, 180, 90, 25, 80, 20);

        // 100 contacts in a quarter hour is four times the load of 100 in an hour. The old code
        // returned 8 for all three, understaffing the 15-minute desk by two thirds.
        assertThat(hourly).isEqualTo(8);
        assertThat(halfHourly).isEqualTo(13);
        assertThat(quarterHourly).isEqualTo(24);
        assertThat(halfHourly).isGreaterThan(hourly);
        assertThat(quarterHourly).isGreaterThan(halfHourly);
    }

    @Test
    @DisplayName("halving the interval and halving the volume is the same problem")
    void loadDependsOnTheRateNotTheRawVolume() {
        // The property that shows the scaling is right rather than merely monotonic: identical
        // arrival RATES must give identical answers however the interval is sliced.
        assertThat(service.calculateRequiredAgents(100, 30, 180, 90, 25, 80, 20))
                .isEqualTo(service.calculateRequiredAgents(200, 60, 180, 90, 25, 80, 20));
        assertThat(service.calculateRequiredAgents(25, 15, 300, 60, 0, 90, 20))
                .isEqualTo(service.calculateRequiredAgents(100, 60, 300, 60, 0, 90, 20));
    }

    @Test
    @DisplayName("the final pass converts on the same basis as the retrial loop")
    void theFinalPassUsesTheSameDivisorAsTheLoop() {
        // These inputs never satisfy the loop's "same agent count twice running" convergence check,
        // so all 100 iterations run and the answer comes from the final pass after it. That is the
        // ONLY path where the second divisor is reached, which is why a half-fix survives every
        // other test in this class: with the final pass left at 3600 these return 3 and 5 instead
        // of 7 and 13 -- a quieter wrong answer than the bug being fixed, because it looks like an
        // ordinary staffing number rather than an obviously hourly one.
        assertThat(service.calculateRequiredAgents(20, 15, 180, 90, 75, 90, 20)).isEqualTo(7);
        assertThat(service.calculateRequiredAgents(25, 15, 300, 60, 75, 90, 20)).isEqualTo(13);

        // And the rate equivalence still holds for a non-converging input, which it cannot if the
        // two conversions disagree.
        assertThat(service.calculateRequiredAgents(20, 15, 180, 90, 75, 90, 20))
                .isEqualTo(service.calculateRequiredAgents(80, 60, 180, 90, 75, 90, 20));
    }

    @Test
    @DisplayName("a non-positive interval is rejected rather than dividing by zero")
    void intervalMustBePositive() {
        // Guarded before the volume check, so it fires even for the zero-volume shortcut: a caller
        // that cannot say how long its timeslot is has a bug worth hearing about immediately.
        assertThatThrownBy(() -> service.calculateRequiredAgents(100, 0, 180, 90, 25, 80, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intervalMinutes");
        assertThatThrownBy(() -> service.calculateRequiredAgents(0, -15, 180, 90, 25, 80, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intervalMinutes");
    }

    @Test
    @DisplayName("no contacts and no handling time need nobody, at any interval")
    void degenerateInputs() {
        assertThat(service.calculateRequiredAgents(0, 15, 180, 90, 25, 80, 20)).isZero();
        assertThat(service.calculateRequiredAgents(100, 15, 0, 90, 25, 80, 20)).isZero();
        assertThat(service.calculateRequiredAgents(-5, 30, 180, 90, 25, 80, 20)).isZero();
    }
}
