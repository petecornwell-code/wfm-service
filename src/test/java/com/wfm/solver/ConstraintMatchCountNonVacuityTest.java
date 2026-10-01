package com.wfm.solver;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeight;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SOLV-06's per-constraint match-count instrument, and D-12's diagnosis. A score alone cannot
 * distinguish a constraint that matched nothing from one that matched a satisfied tuple -- both
 * score zero -- so a join that silently drops every tuple it should have matched (the failure
 * mode this phase's migration can introduce) is invisible to any test that only reads the total
 * score. This class reads {@code getConstraintMatchCount()} per constraint instead.
 *
 * <p><strong>Completeness is inherited, not re-derived (D-12).</strong> {@link #constraintWeightNames()}
 * and {@link #constraintBuilderMethodCount()} below are copied VERBATIM from
 * {@link ScheduleConstraintClassificationTest}'s identically-named private methods -- the same
 * reflective derivation {@code ScheduleConstraintClassification}'s own enforcement test already
 * uses to guarantee "adding a twentieth constraint fails the build until someone adds a row
 * here". This class inherits that property for its own table rather than hand-typing a parallel
 * list that could drift out of date silently, which is the exact failure this precedent exists to
 * close.
 *
 * <p><strong>The literal baseline (D-12, this plan's own "literal numbers ARE the recorded
 * baseline" rule).</strong> {@link #EXPECTED_MIDNIGHT_COVERAGE_COUNTS} is a hand-written table of
 * every registered constraint's match count on {@link MidnightBoundaryFixture#midnightCoverageScenario()}
 * (the {@code 00:00} baseline), captured by running the un-migrated tree as it stood on
 * 2026-10-01, before plan 20-05's join-and-anchor migration. These numbers are committed as
 * literals and MUST NEVER be regenerated from post-migration output in order to make an assertion
 * pass -- refreshing them to match whatever the code now produces is exactly the failure this
 * instrument exists to detect. If the migration is correct, every number here stays unchanged
 * (the {@code 00:00} anchor is where business date already equals calendar date, so nothing about
 * this specific scenario's counts should move).
 *
 * <p><strong>A zero-weight constraint is elided from {@code explain()}, not reported at zero
 * (an empirical finding, not plan narrative).</strong> {@code ConstraintWeights.shiftStartMixWeight}
 * ships at {@link HardSoftScore#ZERO} by default (its own javadoc: "the steer is MEASURED NOT TO
 * WORK"). Timefold does not report a zero-weight constraint in
 * {@code getConstraintMatchTotalMap()} at all -- not even with a zero count -- confirmed directly
 * against this tree: the map holds 25 entries, not 26, for every scenario in
 * {@link MidnightBoundaryFixture} under its default {@link ConstraintWeights}. {@link #matchCountOf}
 * therefore normalises {@code shiftStartMixWeight} to a non-zero value on the schedule BEFORE
 * reading any count, for every scenario this class reads. This changes nothing about which
 * {@code AgentShiftAssignment}/{@code ShiftStartMixTarget} pairs match -- {@code penalizeConfigurable}'s
 * weight scales the penalty, never the join predicate, and no scenario in
 * {@link MidnightBoundaryFixture} carries any {@code ShiftStartMixTarget} fact at all, so the
 * match count is zero either way -- it only stops Timefold eliding the entry, so this class's
 * "exactly one match total found" completeness check (D-12, copied from
 * {@code MidnightBoundaryRegressionTest#requireConstraint}) holds without exception for all 26
 * constraints, including this one.
 *
 * <p>Never calls the optimiser: every scenario arrives fully pinned from {@link MidnightBoundaryFixture},
 * and only {@code SolutionManager.update}/{@code .explain} are called anywhere in this class --
 * matching {@code MidnightBoundaryRegressionTest.NoSearchDuringEvaluation}'s precedent. A real
 * solve would make every count non-deterministic and this whole instrument worthless.
 */
class ConstraintMatchCountNonVacuityTest {

    // ------------------------------------------------------------------
    //  Reflective completeness (D-12) -- copied verbatim from
    //  ScheduleConstraintClassificationTest, never re-derived.
    // ------------------------------------------------------------------

    /**
     * Derivation 1: every {@code @ConstraintWeight} annotation value declared on
     * {@link ConstraintWeights}. These are exactly the strings each constraint's
     * {@code .asConstraint(...)} call registers.
     */
    private static Set<String> constraintWeightNames() {
        Set<String> names = new HashSet<>();
        for (Field field : ConstraintWeights.class.getDeclaredFields()) {
            ConstraintWeight annotation = field.getAnnotation(ConstraintWeight.class);
            if (annotation != null) {
                names.add(annotation.value());
            }
        }
        return names;
    }

    /**
     * Derivation 2: every method on {@link ScheduleConstraintProvider} that returns a
     * {@link Constraint} and takes exactly one {@link ConstraintFactory} parameter.
     */
    private static long constraintBuilderMethodCount() {
        long count = 0;
        for (Method method : ScheduleConstraintProvider.class.getDeclaredMethods()) {
            if (method.getReturnType().equals(Constraint.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].equals(ConstraintFactory.class)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    //  The literal 00:00 baseline (D-12) -- captured 2026-10-01, pre-migration.
    //  Every registered constraint has a row, including every row whose expected
    //  value is zero. Never regenerate these numbers from post-migration output.
    // ------------------------------------------------------------------

    private static final Map<String, Integer> EXPECTED_MIDNIGHT_COVERAGE_COUNTS = buildExpectedMidnightCoverageCounts();

    private static Map<String, Integer> buildExpectedMidnightCoverageCounts() {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("Unassigned assignment", 0);
        expected.put("Agent day off", 0);
        expected.put("Agent not working that day", 0);
        expected.put("Specialization match", 0);
        expected.put("One assignment per timeslot", 0);
        expected.put("Exactly one break", 0);
        expected.put("Break duration", 0);
        expected.put("Break blocked window", 0);
        expected.put("Break start alignment", 0);
        expected.put("Shift envelope compliance", 0);
        expected.put("Band capacity", 0);
        expected.put("Shift work contiguity", 0);
        expected.put("Prefer primary specialization", 0);
        expected.put("Honour preferred start time", 0);
        expected.put("Honour preferred break time", 0);
        expected.put("Break clustering", 0);
        expected.put("Contracted hours (over)", 0);
        expected.put("Contracted hours (under)", 0);
        expected.put("Contracted hours (under, zero)", 0);
        expected.put("Bulk over-allocation limit", 0);
        expected.put("Bulk under-allocation soft", 0);
        expected.put("Bulk under-allocation hard", 0);
        expected.put("Minimum staffing", 0);
        expected.put("Usual shift consistency", 0);
        expected.put("Preferred start (shift mode)", 0);
        expected.put("Shift start mix", 0);
        return java.util.Collections.unmodifiableMap(expected);
    }

    // ------------------------------------------------------------------
    //  Reading a count, safely (D-12 -- the helper precedent)
    // ------------------------------------------------------------------

    private static SolutionManager<Schedule, HardSoftScore> newSolutionManager() {
        SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));
        return SolutionManager.create(factory);
    }

    /**
     * Normalises {@code shiftStartMixWeight} away from {@link HardSoftScore#ZERO} if the schedule
     * carries the shipped default -- see this class's javadoc "A zero-weight constraint is
     * elided" section. A no-op for every other constraint weight.
     */
    private static Schedule normalizeZeroWeightConstraints(Schedule schedule) {
        if (HardSoftScore.ZERO.equals(schedule.getConstraintWeights().getShiftStartMixWeight())) {
            schedule.getConstraintWeights().setShiftStartMixWeight(HardSoftScore.ofSoft(1));
        }
        return schedule;
    }

    /**
     * Reads one constraint's match count, filtering the explanation map by
     * {@link ConstraintMatchTotal#getConstraintName()} -- never the raw map key, which carries a
     * constraint-package prefix (the precedent's own documented past mistake,
     * {@code MidnightBoundaryRegressionTest:57-58}). Asserts exactly one match total is found
     * before reading the count, so an unregistered or renamed constraint fails loudly here rather
     * than silently defaulting to zero.
     */
    private static int matchCountOf(SolutionManager<Schedule, HardSoftScore> solutionManager,
            Schedule schedule, String constraintName) {
        List<ConstraintMatchTotal<HardSoftScore>> matches = solutionManager.explain(schedule)
                .getConstraintMatchTotalMap().values().stream()
                .filter(total -> constraintName.equals(total.getConstraintName()))
                .toList();
        assertThat(matches)
                .as("constraint '%s' must be present in the explanation before its match count is read",
                        constraintName)
                .hasSize(1);
        return matches.get(0).getConstraintMatchCount();
    }

    /** Every registered constraint's match count on {@code schedule}, keyed by constraint name. */
    private static Map<String, Integer> allMatchCounts(Schedule schedule) {
        normalizeZeroWeightConstraints(schedule);
        SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
        solutionManager.update(schedule);
        Map<String, Integer> counts = new TreeMap<>();
        for (String name : constraintWeightNames()) {
            counts.put(name, matchCountOf(solutionManager, schedule, name));
        }
        return counts;
    }

    // ------------------------------------------------------------------
    //  Completeness (D-12, inherited from ScheduleConstraintClassificationTest's pattern)
    // ------------------------------------------------------------------

    @Test
    void completeness_tableKeySetExactlyEqualsTheReflectedConstraintWeightNames() {
        Set<String> table = EXPECTED_MIDNIGHT_COVERAGE_COUNTS.keySet();
        Set<String> registered = constraintWeightNames();

        Set<String> missingFromTable = new HashSet<>(registered);
        missingFromTable.removeAll(table);
        Set<String> staleInTable = new HashSet<>(table);
        staleInTable.removeAll(registered);

        assertThat(missingFromTable)
                .as("constraints registered via @ConstraintWeight but missing a row in this class's "
                        + "expected-count table -- these are unclassified and MUST be given a row")
                .isEmpty();
        assertThat(staleInTable)
                .as("rows in this class's expected-count table that no longer correspond to a "
                        + "registered @ConstraintWeight -- these are stale and MUST be removed")
                .isEmpty();
    }

    @Test
    void completeness_tableSizeExactlyEqualsTheReflectedBuilderMethodCount() {
        assertThat((long) EXPECTED_MIDNIGHT_COVERAGE_COUNTS.size())
                .as("expected-count table row count must equal the number of Constraint-returning "
                        + "builder methods on ScheduleConstraintProvider -- a 27th constraint added "
                        + "with a weight, a builder method, or both, must fail here until a row is added")
                .isEqualTo(constraintBuilderMethodCount());
    }

    // ------------------------------------------------------------------
    //  Non-vacuity, 00:00 baseline (D-12)
    // ------------------------------------------------------------------

    @Test
    void midnightBaseline_everyRegisteredConstraintMatchesItsLiteralExpectedCount() {
        Schedule schedule = normalizeZeroWeightConstraints(MidnightBoundaryFixture.midnightCoverageScenario());
        SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
        solutionManager.update(schedule);

        Map<String, Integer> actual = new TreeMap<>();
        Map<String, Integer> mismatches = new TreeMap<>();
        for (Map.Entry<String, Integer> expected : EXPECTED_MIDNIGHT_COVERAGE_COUNTS.entrySet()) {
            int count = matchCountOf(solutionManager, schedule, expected.getKey());
            actual.put(expected.getKey(), count);
            if (count != expected.getValue()) {
                mismatches.put(expected.getKey(), count);
            }
        }

        assertThat(mismatches)
                .as("every registered constraint's match count on the 00:00 coverage scenario must "
                        + "equal its literal committed expected value from EXPECTED_MIDNIGHT_COVERAGE_COUNTS "
                        + "-- actual (constraint -> count) for every registered constraint was: %s", actual)
                .isEmpty();
    }

    // ------------------------------------------------------------------
    //  Anchor invariance (D-12's teeth) -- EXPECTED RED until plan 20-05's migration
    // ------------------------------------------------------------------

    /**
     * Compares every registered constraint's match count between two scenarios of identical
     * day-relative geometry, differing only in which anchor they are built at. Returns the
     * differing constraints, mapped to their (left, right) counts, so the failure message can name
     * exactly which constraint moved and by how much -- SOLV-06's "names which constraint moved"
     * property, D-12's diagnosis.
     */
    private static Map<String, String> diffMatchCounts(Schedule left, Schedule right) {
        normalizeZeroWeightConstraints(left);
        normalizeZeroWeightConstraints(right);
        SolutionManager<Schedule, HardSoftScore> solutionManager = newSolutionManager();
        solutionManager.update(left);
        solutionManager.update(right);

        Map<String, String> differing = new TreeMap<>();
        for (String name : constraintWeightNames()) {
            int leftCount = matchCountOf(solutionManager, left, name);
            int rightCount = matchCountOf(solutionManager, right, name);
            if (leftCount != rightCount) {
                differing.put(name, leftCount + " (00:00) vs " + rightCount + " (21:00)");
            }
        }
        return differing;
    }

    @Test
    void anchorInvariance_coveragePair_everyConstraintCountMatchesAcrossTheAnchor() {
        // midnightCoverageScenario (00:00) and ninePmCoverageScenario (21:00) share identical
        // day-relative geometry -- two hourly demanded slots, two agents each contracted to
        // exactly one slot (MidnightBoundaryFixture's own javadoc for both scenarios). Re-anchoring
        // alone must change nothing about which seats a band covers or which agent-day a seat
        // belongs to (SOLV-06's must_haves.truths) -- so every registered constraint's match count
        // must be identical between the two.
        Map<String, String> differing = diffMatchCounts(
                MidnightBoundaryFixture.midnightCoverageScenario(),
                MidnightBoundaryFixture.ninePmCoverageScenario());

        assertThat(differing)
                .as("re-anchoring the coverage scenario from 00:00 to 21:00 must not change any "
                        + "constraint's match count -- a differing constraint is one that does not yet "
                        + "resolve the business date or the interval origin correctly. Differing "
                        + "constraints (name -> \"count (00:00) vs count (21:00)\"): %s", differing)
                .isEmpty();
    }

    @Test
    void anchorInvariance_bandFlushPair_everyConstraintCountMatchesAcrossTheAnchor() {
        // breakBandFlushToEnvelopeEndScenario (00:00) and ninePmBreakBandFlushToEnvelopeEndScenario
        // (21:00) share identical day-relative geometry -- a shift envelope whose break band
        // finishes exactly flush to the envelope's own end. Same invariance argument as the
        // coverage pair above.
        Map<String, String> differing = diffMatchCounts(
                MidnightBoundaryFixture.breakBandFlushToEnvelopeEndScenario(),
                MidnightBoundaryFixture.ninePmBreakBandFlushToEnvelopeEndScenario());

        assertThat(differing)
                .as("re-anchoring the band-flush scenario from 00:00 to 21:00 must not change any "
                        + "constraint's match count -- a differing constraint is one that does not yet "
                        + "resolve the business date or the interval origin correctly. Differing "
                        + "constraints (name -> \"count (00:00) vs count (21:00)\"): %s", differing)
                .isEmpty();
    }
}
