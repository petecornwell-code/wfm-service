package com.wfm.service;

import com.wfm.calc.StaffingAdjustment;
import com.wfm.dto.ErlangCCalculationRequest;
import com.wfm.dto.ErlangCalculationResponse;
import com.wfm.dto.ErlangXCalculationRequest;
import com.wfm.dto.StaffingAdjustmentOptionsDto;
import com.wfm.exception.UnprocessableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link ErlangCalculatorService} — the read-only calculator over {@link com.wfm.calc.ErlangC} and
 * {@link com.wfm.calc.ErlangX}.
 *
 * <p>The fixture is deliberately the one {@code ErlangCTest} pins by hand: 100 contacts in a half
 * hour at 180 s AHT is 10 Erlangs, and 80% within 20 s needs 14 agents. Reusing it means this class
 * tests the mapping and the adjustments, not the maths a lower test already fixes — and a failure
 * here points at the service rather than at the queueing model.
 */
class ErlangCalculatorServiceTest {

    private final ErlangCalculatorService service = new ErlangCalculatorService();

    private static ErlangCCalculationRequest erlangC(StaffingAdjustmentOptionsDto adjustments) {
        return new ErlangCCalculationRequest(100, 30, 180, 0.80, 20, adjustments);
    }

    private static ErlangXCalculationRequest erlangX(double patienceSeconds, double retryFraction,
                                                     StaffingAdjustmentOptionsDto adjustments) {
        return new ErlangXCalculationRequest(100, 30, 180, patienceSeconds, retryFraction,
                0.80, 20, null, adjustments);
    }

    @Test
    @DisplayName("the canonical Erlang C problem comes back as 14 agents with its diagnostics")
    void erlangCCanonical() {
        ErlangCalculationResponse r = service.calculateErlangC(erlangC(null));

        assertThat(r.model()).isEqualTo("ERLANG_C");
        assertThat(r.agentsRequired()).isEqualTo(14);
        assertThat(r.offeredLoad()).isEqualTo(10.0);
        // No retrials in this model, so the two loads are the same number by construction.
        assertThat(r.baseOfferedLoad()).isEqualTo(r.offeredLoad());
        assertThat(r.serviceLevel()).isCloseTo(0.888349, within(1e-5));
        assertThat(r.occupancy()).isCloseTo(10.0 / 14.0, within(1e-9));
        assertThat(r.averageSpeedOfAnswerSeconds()).isNotNull();
        // Erlang C models neither abandonment nor retrials; reporting a zero for either would be a
        // claim about caller behaviour this model does not make.
        assertThat(r.probabilityOfAbandon()).isNull();
        assertThat(r.retrialIterations()).isNull();
    }

    @Test
    @DisplayName("an omitted adjustments block rosters exactly the queueing answer")
    void noAdjustmentsMeansNoInflation() {
        ErlangCalculationResponse r = service.calculateErlangC(erlangC(null));

        assertThat(r.handlingAgents()).isEqualTo(14);
        assertThat(r.scheduledAgents()).isEqualTo(14);
        assertThat(r.occupancyRelief()).isZero();
        assertThat(r.shrinkageUplift()).isZero();
        // NONE must behave identically to absent, or the frontend's "leave it blank" is a lie.
        assertThat(service.calculateErlangC(erlangC(StaffingAdjustmentOptionsDto.NONE)))
                .isEqualTo(r);
    }

    @Test
    @DisplayName("shrinkage rosters more people than are handling contacts")
    void shrinkageUpliftIsReportedSeparately() {
        ErlangCalculationResponse r = service.calculateErlangC(
                erlangC(new StaffingAdjustmentOptionsDto(0.30, null, null)));

        assertThat(r.agentsRequired()).isEqualTo(14);
        assertThat(r.handlingAgents()).isEqualTo(14);
        assertThat(r.scheduledAgents()).isEqualTo(20);      // 14 / 0.7
        assertThat(r.shrinkageUplift()).isEqualTo(6);
        assertThat(r.occupancyRelief()).isZero();
    }

    @Test
    @DisplayName("an occupancy ceiling adds agents only when the raw answer breaches it")
    void occupancyCeiling() {
        // The canonical answer sits at 71% occupancy, comfortably inside an 85% ceiling.
        ErlangCalculationResponse slack = service.calculateErlangC(
                erlangC(new StaffingAdjustmentOptionsDto(null, 0.85, null)));
        assertThat(slack.occupancyRelief()).isZero();
        assertThat(slack.handlingAgents()).isEqualTo(slack.agentsRequired());

        // 1000 contacts in the same half hour is 100 Erlangs, where Erlang C's answer runs the
        // floor hot -- economies of scale are exactly why a big interval breaches the ceiling and a
        // small one does not.
        ErlangCalculationResponse tight = service.calculateErlangC(
                new ErlangCCalculationRequest(1000, 30, 180, 0.80, 20,
                        new StaffingAdjustmentOptionsDto(null, 0.85, null)));
        assertThat(tight.occupancy()).isGreaterThan(0.85);
        assertThat(tight.occupancyRelief()).isPositive();
        assertThat(tight.handlingAgents()).isGreaterThan(tight.agentsRequired());
        // The point of the ceiling: the occupancy actually worked is within it.
        assertThat(tight.occupancyAtHandlingAgents()).isLessThanOrEqualTo(0.85);
    }

    @Test
    @DisplayName("shrinkage applies after occupancy relief, never before")
    void adjustmentOrderIsPreserved() {
        ErlangCalculationResponse r = service.calculateErlangC(
                new ErlangCCalculationRequest(1000, 30, 180, 0.80, 20,
                        new StaffingAdjustmentOptionsDto(0.30, 0.85, null)));

        // Reversing the order would compute the uplift on the smaller pre-relief number and
        // understaff. Pinning the identity is what catches that, at any headcount.
        assertThat(r.scheduledAgents())
                .isEqualTo((int) Math.ceil(r.handlingAgents() / 0.70 - 1e-9));
        assertThat(r.handlingAgents()).isEqualTo(r.agentsRequired() + r.occupancyRelief());
        assertThat(r.shrinkageUplift()).isEqualTo(r.scheduledAgents() - r.handlingAgents());
    }

    @Test
    @DisplayName("concurrency divides the load by exactly StaffingAdjustment's own factor")
    void concurrencyMatchesEffectiveLoad() {
        StaffingAdjustment.Options options = new StaffingAdjustment.Options(0.0, null, 3.0);
        ErlangCalculationResponse r = service.calculateErlangC(
                erlangC(new StaffingAdjustmentOptionsDto(null, null, 3.0)));

        // The service divides the volume; StaffingAdjustment divides the load. They must agree, or
        // a chat desk is staffed on one basis and reported on another.
        assertThat(r.offeredLoad())
                .isCloseTo(StaffingAdjustment.effectiveLoad(10.0, options), within(1e-12));
        assertThat(r.agentsRequired()).isLessThan(14);
    }

    @Test
    @DisplayName("Erlang X with infinite patience answers exactly as Erlang C")
    void erlangXReducesToErlangCThroughTheService() {
        ErlangCalculationResponse c = service.calculateErlangC(erlangC(null));
        // patience 0 is the record's own "infinitely patient" spelling.
        ErlangCalculationResponse x = service.calculateErlangX(erlangX(0, 0, null));

        assertThat(x.model()).isEqualTo("ERLANG_X");
        assertThat(x.agentsRequired()).isEqualTo(c.agentsRequired());
        assertThat(x.serviceLevel()).isCloseTo(c.serviceLevel(), within(1e-9));
        assertThat(x.offeredLoad()).isEqualTo(c.offeredLoad());
        assertThat(x.probabilityOfAbandon()).isZero();
        assertThat(x.retrialIterations()).isZero();
        // The ASA field is the one asymmetry, and it is deliberate: Erlang X's waiting-time
        // distribution is phase-type, so the service reports no mean rather than a wrong one.
        assertThat(x.averageSpeedOfAnswerSeconds()).isNull();
        assertThat(c.averageSpeedOfAnswerSeconds()).isNotNull();
    }

    @Test
    @DisplayName("impatience lowers headcount and retrials inflate the load above the base")
    void erlangXImpatienceAndRetrials() {
        ErlangCalculationResponse patient = service.calculateErlangC(erlangC(null));
        ErlangCalculationResponse impatient = service.calculateErlangX(erlangX(90, 0, null));

        assertThat(impatient.probabilityOfAbandon()).isNotNull().isPositive();
        // Callers who hang up are work that never arrives, so fewer agents meet the same target --
        // the reason an Erlang C baseline is the conservative one.
        assertThat(impatient.agentsRequired()).isLessThanOrEqualTo(patient.agentsRequired());

        ErlangCalculationResponse retrying = service.calculateErlangX(erlangX(90, 0.50, null));
        assertThat(retrying.baseOfferedLoad()).isEqualTo(10.0);
        assertThat(retrying.offeredLoad()).isGreaterThan(retrying.baseOfferedLoad());
        assertThat(retrying.retrialIterations()).isNotNull().isPositive();
    }

    @Test
    @DisplayName("Erlang X caps occupancy against served load, not against abandoned contacts")
    void erlangXOccupancyUsesServedLoad() {
        ErlangCalculationResponse r = service.calculateErlangX(
                new ErlangXCalculationRequest(1000, 30, 180, 90, 0.0, 0.80, 20, null,
                        new StaffingAdjustmentOptionsDto(null, 0.85, null)));

        double served = r.offeredLoad() * (1.0 - r.probabilityOfAbandon());
        assertThat(r.occupancyAtHandlingAgents())
                .isCloseTo(served / r.handlingAgents(), within(1e-9))
                .isLessThanOrEqualTo(0.85);
    }

    @Test
    @DisplayName("countAbandonsAsAnswered defaults to false and is never inferred")
    void abandonConventionDefaultsToFalse() {
        ErlangCalculationResponse strict = service.calculateErlangX(erlangX(90, 0, null));
        ErlangCalculationResponse lenient = service.calculateErlangX(
                new ErlangXCalculationRequest(100, 30, 180, 90, 0, 0.80, 20, true, null));

        // Counting a hang-up as a success raises the reported service level, which lowers headcount.
        // Absent and explicitly-false must be the same answer; true must be a different one.
        assertThat(strict).isEqualTo(service.calculateErlangX(
                new ErlangXCalculationRequest(100, 30, 180, 90, 0, 0.80, 20, false, null)));
        assertThat(lenient.agentsRequired()).isLessThanOrEqualTo(strict.agentsRequired());
    }

    @Test
    @DisplayName("a negative or nonsense input is rejected, not answered with a confident zero")
    void inputValidation() {
        // Without this guard a negative volume falls into the zero-load branch and returns 0 agents.
        assertThatThrownBy(() -> service.calculateErlangC(
                new ErlangCCalculationRequest(-1, 30, 180, 0.80, 20, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("volume");
        assertThatThrownBy(() -> service.calculateErlangC(
                new ErlangCCalculationRequest(100, 30, -180, 0.80, 20, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ahtSeconds");
        assertThatThrownBy(() -> service.calculateErlangC(
                new ErlangCCalculationRequest(100, 30, 180, 0.80, -20, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceLevelThresholdSeconds");
        assertThatThrownBy(() -> service.calculateErlangC(
                new ErlangCCalculationRequest(100, 2000, 180, 0.80, 20, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("day");
        assertThatThrownBy(() -> service.calculateErlangX(
                new ErlangXCalculationRequest(100, 30, 180, -5, 0, 0.80, 20, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("patienceSeconds");
        // The records' own guards must still surface as 400s, not 500s: a percentage typed where a
        // fraction belongs is the likeliest operator mistake on this screen.
        assertThatThrownBy(() -> service.calculateErlangC(
                new ErlangCCalculationRequest(100, 30, 180, 80, 20, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceLevelTarget");
    }

    @Test
    @DisplayName("a load beyond the search ceiling is 422, not a minutes-long CPU burn")
    void loadCeilingIsUnprocessable() {
        // A daily total typed into a half-hour interval -- the mistake this ceiling is for.
        assertThatThrownBy(() -> service.calculateErlangC(
                new ErlangCCalculationRequest(100_000, 30, 180, 0.80, 20, null)))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("Erlangs");
        assertThatThrownBy(() -> service.calculateErlangX(
                new ErlangXCalculationRequest(100_000, 30, 180, 90, 0.2, 0.80, 20, null, null)))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    @DisplayName("a 100% target saturates to a finite headcount rather than being refused")
    void hundredPercentTargetIsMetByFloatingPointSaturation() {
        // Worth pinning because it is the opposite of what the arithmetic suggests: P(wait) is never
        // zero, so 100% within 20 s should be unreachable. At 44 agents on 10 Erlangs it is
        // 2.2e-15, and 1 - 2.2e-15 * decay rounds to exactly 1.0 in a double -- so the search stops
        // there and the operator gets a number, not an error. This is also why the 422 mapping
        // below has to be tested directly: no input inside the load ceiling reaches it.
        ErlangCalculationResponse r = service.calculateErlangC(
                new ErlangCCalculationRequest(100, 30, 180, 1.0, 20, null));

        assertThat(r.agentsRequired()).isEqualTo(44);
        assertThat(r.serviceLevel()).isEqualTo(1.0);
        assertThat(r.probabilityOfWait()).isLessThan(1e-14).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("an unsatisfiable target would be 422, not a 500")
    void unsatisfiableTargetMapsToUnprocessable() {
        // The models' documented failure, mapped where it belongs. Asserted against the mapping
        // itself rather than through an input, per the test above: the guard exists for the day a
        // ceiling changes, and an untested guard is the one that turns out to be wrong then.
        assertThatThrownBy(() -> ErlangCalculatorService.unsatisfiableAs422(() -> {
            throw new IllegalStateException(
                    "No agent count up to 10000 meets a service level of 0.8");
        }))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("No agent count");

        // And a real request that reaches it, so the mapping is not only unit-tested in isolation:
        // 2000 Erlangs (the ceiling exactly, 20000 contacts at 180 s over 30 min) with one-second
        // patience and every abandoned caller retrying. The retrial fixed point inflates the load
        // beyond what MAX_AGENTS can serve at a 99% target. Costs about a quarter second, which is
        // the price of proving the 422 rather than assuming it.
        assertThatThrownBy(() -> service.calculateErlangX(
                new ErlangXCalculationRequest(20_000, 30, 180, 1.0, 1.0, 0.99, 20, null, null)))
                .isInstanceOf(UnprocessableException.class);

        // Anything else must pass through untouched -- a real fault stays a 500.
        assertThatThrownBy(() -> ErlangCalculatorService.unsatisfiableAs422(() -> {
            throw new ArithmeticException("genuinely broken");
        }))
                .isInstanceOf(ArithmeticException.class);

        assertThat(ErlangCalculatorService.unsatisfiableAs422(() -> 14)).isEqualTo(14);
    }

    @Test
    @DisplayName("zero volume needs nobody, and says so without dividing by zero")
    void zeroVolume() {
        ErlangCalculationResponse r = service.calculateErlangC(
                new ErlangCCalculationRequest(0, 30, 180, 0.80, 20,
                        new StaffingAdjustmentOptionsDto(0.30, 0.85, null)));

        assertThat(r.agentsRequired()).isZero();
        assertThat(r.scheduledAgents()).isZero();
        assertThat(r.occupancyAtHandlingAgents()).isZero();
        assertThat(r.serviceLevel()).isEqualTo(1.0);
        // An infinite ASA is not a number to put in front of an operator.
        assertThat(r.averageSpeedOfAnswerSeconds()).isZero();
    }

    @Test
    @DisplayName("the service holds no state and no collaborators, which is what makes it read-only")
    void nothingToPersistWith() {
        // The claim in this class's javadoc, asserted rather than trusted: no repository, no
        // EntityManager, nothing injected that could write a row. A future change that wires one in
        // fails here, where the reason is written down, rather than silently making a calculator
        // that mutates staffing requirements.
        assertThat(Arrays.stream(ErlangCalculatorService.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic() && !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName))
                .isEmpty();
        assertThat(Arrays.stream(ErlangCalculatorService.class.getDeclaredMethods())
                .flatMap(m -> Arrays.stream(m.getAnnotations()))
                .map(a -> a.annotationType().getName()))
                .noneMatch(name -> name.contains("Transactional"));
    }

    @Test
    @DisplayName("every public method is a pure function of its argument")
    void repeatedCallsAgree() {
        // Two identical requests must give identical answers, in either order and with no warm-up:
        // the property that makes this endpoint safe to call from a keystroke handler.
        ErlangCalculationResponse first = service.calculateErlangX(erlangX(90, 0.25, null));
        ErlangCalculationResponse second = service.calculateErlangX(erlangX(90, 0.25, null));
        assertThat(second).isEqualTo(first);

        for (Method m : ErlangCalculatorService.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                assertThat(m.getParameterCount()).isEqualTo(1);
            }
        }
    }
}
