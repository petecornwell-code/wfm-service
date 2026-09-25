package com.wfm.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link StaffingAdjustment} — the step between "how many agents must be handling contacts" and
 * "how many people must be rostered".
 *
 * <p>The ordering assertion is the one that matters: shrinkage applies to the total AFTER any
 * occupancy relief, never before, or the uplift is computed on a smaller base and the result
 * understaffs.
 */
class StaffingAdjustmentTest {

    @Test
    @DisplayName("the default options change nothing at all")
    void defaultsAreANoOp() {
        StaffingAdjustment.Result r = StaffingAdjustment.apply(14, 10.0, StaffingAdjustment.Options.NONE);
        assertThat(r.scheduled()).isEqualTo(14);
        assertThat(r.handling()).isEqualTo(14);
        assertThat(r.occupancyRelief()).isZero();
        assertThat(r.shrinkageUplift()).isZero();
        assertThat(StaffingAdjustment.effectiveLoad(10.0, StaffingAdjustment.Options.NONE))
                .isEqualTo(10.0);
    }

    @Test
    @DisplayName("shrinkage rosters more people than are handling contacts")
    void shrinkageUplift() {
        // 14 handling, 30% shrinkage: 14 / 0.7 = 20.
        StaffingAdjustment.Result r = StaffingAdjustment.apply(14, 10.0,
                new StaffingAdjustment.Options(0.30, null, 1.0));
        assertThat(r.handling()).isEqualTo(14);
        assertThat(r.scheduled()).isEqualTo(20);
        assertThat(r.shrinkageUplift()).isEqualTo(6);
    }

    @Test
    @DisplayName("an occupancy ceiling adds agents when the queueing answer runs them too hot")
    void occupancyCeiling() {
        // 12 agents on 10 Erlangs is 83.3% occupancy. Capped at 80%, 10/0.8 = 12.5 -> 13.
        StaffingAdjustment.Result r = StaffingAdjustment.apply(12, 10.0,
                new StaffingAdjustment.Options(0.0, 0.80, 1.0));
        assertThat(r.handling()).isEqualTo(13);
        assertThat(r.occupancyRelief()).isEqualTo(1);
        assertThat(10.0 / r.handling()).isLessThanOrEqualTo(0.80);
    }

    @Test
    @DisplayName("a ceiling the queueing answer already respects adds nobody")
    void occupancyCeilingNotBinding() {
        // 14 agents on 10 Erlangs is 71.4%, already inside an 85% cap.
        StaffingAdjustment.Result r = StaffingAdjustment.apply(14, 10.0,
                new StaffingAdjustment.Options(0.0, 0.85, 1.0));
        assertThat(r.handling()).isEqualTo(14);
        assertThat(r.occupancyRelief()).isZero();
    }

    @Test
    @DisplayName("shrinkage applies AFTER occupancy relief, on the larger base")
    void shrinkageAppliesToTheRelievedTotal() {
        // 12 handling -> 13 for an 80% cap -> 13 / 0.75 = 17.33 -> 18.
        StaffingAdjustment.Result r = StaffingAdjustment.apply(12, 10.0,
                new StaffingAdjustment.Options(0.25, 0.80, 1.0));
        assertThat(r.handling()).isEqualTo(13);
        assertThat(r.scheduled()).isEqualTo(18);
        // Applying shrinkage FIRST would give ceil(12/0.75) = 16, then the cap on 16 changes
        // nothing — 16, understaffing by two. The order is not cosmetic.
        assertThat(r.scheduled()).isGreaterThan(16);
    }

    @Test
    @DisplayName("concurrency divides the load, because it changes the queueing problem itself")
    void concurrencyDividesLoad() {
        assertThat(StaffingAdjustment.effectiveLoad(30.0,
                new StaffingAdjustment.Options(0, null, 3.0))).isEqualTo(10.0);

        // A chat desk at 3 concurrent contacts needs far fewer agents than the voice equivalent.
        double voice = 30.0;
        double chat = StaffingAdjustment.effectiveLoad(voice, new StaffingAdjustment.Options(0, null, 3.0));
        int voiceAgents = ErlangC.requiredAgents(new ErlangC.Input(600, 60, 180, 0.80, 20)).agents();
        assertThat(chat).isLessThan(voice);
        assertThat(voiceAgents).isGreaterThan(30);
    }

    @Test
    @DisplayName("nonsense inputs are rejected rather than clamped into plausible answers")
    void rejectsNonsense() {
        assertThatThrownBy(() -> new StaffingAdjustment.Options(1.0, null, 1.0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("shrinkage");
        assertThatThrownBy(() -> new StaffingAdjustment.Options(30, null, 1.0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("shrinkage");
        assertThatThrownBy(() -> new StaffingAdjustment.Options(0, 1.5, 1.0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxOccupancy");
        assertThatThrownBy(() -> new StaffingAdjustment.Options(0, null, 0.5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("concurrency");
    }

    @Test
    @DisplayName("the three adjustments compound rather than cancel, which is why all three are explicit")
    void adjustmentsCompound() {
        int raw = 12;
        int capped = StaffingAdjustment.apply(raw, 10.0,
                new StaffingAdjustment.Options(0, 0.80, 1.0)).scheduled();
        int both = StaffingAdjustment.apply(raw, 10.0,
                new StaffingAdjustment.Options(0.30, 0.80, 1.0)).scheduled();
        assertThat(raw).isLessThan(capped);
        assertThat(capped).isLessThan(both);
        // Omitting both, as the previous implementation did, understaffs by this much:
        assertThat(both - raw).isEqualTo(7);
    }
}
