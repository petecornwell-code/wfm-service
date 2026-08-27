package com.wfm.solver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XCUT-05's executable deliverable: an explicit mode classification for every constraint
 * {@link ScheduleConstraintProvider#defineConstraints} registers.
 *
 * <p>This class lives in the test source set (Phase 14 P-06) so that
 * {@code src/main/java/com/wfm/solver/} stays byte-identical for the whole phase — MODE-05's
 * "additive, not a rewrite" claim is a structural fact, provable by {@code git diff --name-only},
 * not merely an assertion. {@link ScheduleConstraintClassificationTest} is the enforcement
 * mechanism: it derives the constraint set from {@link ConstraintWeights}'s
 * {@code @ConstraintWeight} annotations and from {@link ScheduleConstraintProvider}'s
 * {@code Constraint}-returning builder methods, both by reflection, and asserts this map's key
 * set agrees with both derivations. Adding a twentieth constraint (with a weight, or a builder
 * method, or both) fails the build until someone adds a row here.
 */
public final class ScheduleConstraintClassification {

    private ScheduleConstraintClassification() {
    }

    /**
     * The four-constant tag vocabulary (Phase 14 P-08).
     *
     * <p>{@link #MODE_GATED} is deliberately unused today — ROADMAP.md success criterion 5
     * records that the four break constraints tagged below "aren't actually mode-gated until
     * Phase 15". The tag exists for Phase 15 to move rows into; its current emptiness is itself
     * a finding, not an oversight.
     */
    public enum ModeClassification {
        MODE_AGNOSTIC,
        MODE_GATED,
        NEEDS_SHIFT_VARIANT,
        OPEN_RESOLVE_IN_PHASE_15
    }

    /**
     * One classification row. {@code owner} is non-blank only for
     * {@link ModeClassification#OPEN_RESOLVE_IN_PHASE_15} rows — it names who resolves the open
     * question, so an explicit OPEN is a classification (with an accountable owner), never a
     * silent omission.
     */
    public record Entry(ModeClassification classification, String basis, String owner) {
        public Entry {
            if (classification == null) {
                throw new IllegalArgumentException("classification must not be null");
            }
            if (basis == null || basis.isBlank()) {
                throw new IllegalArgumentException("basis must not be blank");
            }
            if (classification == ModeClassification.OPEN_RESOLVE_IN_PHASE_15) {
                if (owner == null || owner.isBlank()) {
                    throw new IllegalArgumentException(
                            "OPEN_RESOLVE_IN_PHASE_15 rows must name a non-blank owner");
                }
            }
        }
    }

    private static final String PHASE_15_OWNER = "Phase 15 — Shift Envelope & Coupling";

    /**
     * Every constraint {@link ScheduleConstraintProvider#defineConstraints} registers, keyed by
     * the exact string each constraint's {@code .asConstraint(...)} call passes — the same
     * string each corresponding {@link ConstraintWeights} field's {@code @ConstraintWeight}
     * carries. Insertion order mirrors {@code defineConstraints}'s array order.
     */
    public static Map<String, Entry> classifications() {
        Map<String, Entry> map = new LinkedHashMap<>();

        map.put("Unassigned assignment", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Operates per-timeslot on assigned-count vs. demand bounds; shift mode still has "
                        + "timeslots and demand — the constraint does not read how a day's shift was chosen.",
                null));

        map.put("Agent day off", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Pure agent x date join against AgentDayOff; unaffected by how a day's shift was chosen.",
                null));

        map.put("Specialization match", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "ENVL-03 (Phase 15) explicitly keeps specialization variable within the shift "
                        + "envelope, so this per-assignment specialization check is unchanged by shift mode.",
                null));

        map.put("One assignment per timeslot", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Structural (agent, timeslot) uniqueness; mode-independent.",
                null));

        map.put("Exactly one break", new Entry(
                ModeClassification.NEEDS_SHIFT_VARIANT,
                "One of D-03's named four break constraints. Break is currently derived from "
                        + "assignment gaps (countContiguousGaps/getGapLengths); D-01 makes shift-mode breaks "
                        + "a fixed template offset instead, so this constraint needs a shift-mode variant.",
                null));

        map.put("Break duration", new Entry(
                ModeClassification.NEEDS_SHIFT_VARIANT,
                "One of the four. Currently derives duration from the single assignment gap's "
                        + "length; a shift-mode variant compares against the template's fixed "
                        + "break_duration_minutes instead.",
                null));

        map.put("Break blocked window", new Entry(
                ModeClassification.NEEDS_SHIFT_VARIANT,
                "One of the four. Currently derives break position from assignment-gap position "
                        + "relative to the derived shift start/end; a shift-mode variant would compare "
                        + "against the template's fixed envelope instead.",
                null));

        map.put("Break start alignment", new Entry(
                ModeClassification.NEEDS_SHIFT_VARIANT,
                "One of the four. Currently derives break start from the assignment gap's start; "
                        + "a shift-mode variant is unnecessary in the same form once the break start is "
                        + "template-fixed (D-01), but the constraint as coded still needs re-deriving.",
                null));

        map.put("Shift envelope compliance", new Entry(
                ModeClassification.MODE_GATED,
                "Option A (SPIKE-COUPLING.md): joins AgentAssignment to AgentShiftAssignment on "
                        + "(agent, date), then ScheduleConfig, filtering to SHIFT mode before penalising "
                        + "a definite disagreement -- the hard constraint the whole coupling rests on, "
                        + "chosen over a filtered value range that reported 0hard/0soft while 9-14/24 "
                        + "seats sat outside their agent's envelope on 8/8 seeds. Doubly inert on a "
                        + "SLOT-scheduled desk: SolverService never populates AgentShiftAssignment rows "
                        + "there, and the explicit SHIFT-mode filter means the constraint stays silent "
                        + "even if a shift row were present.",
                null));

        map.put("Prefer primary specialization", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Pure agent-attribute soft preference (primary vs. secondary specialization); "
                        + "unaffected by shift structure.",
                null));

        map.put("Honour preferred start time", new Entry(
                ModeClassification.OPEN_RESOLVE_IN_PHASE_15,
                "Penalises a timeslot before AgentPreference.preferredStartTime — a solver-derived "
                        + "value compared against a preference. In shift mode the agent's start is chosen "
                        + "from the library, not per-timeslot; whether comparing against a template-fixed "
                        + "value is still the same constraint or needs its own variant cannot be answered "
                        + "without the shift envelope Phase 15 builds. Deliberately left open rather than "
                        + "guessed — this phase touches no solver code (14-RESEARCH.md Open Questions #1).",
                PHASE_15_OWNER));

        map.put("Honour preferred break time", new Entry(
                ModeClassification.OPEN_RESOLVE_IN_PHASE_15,
                "Currently derives the actual break start from assignment gaps (findBreakStart) and "
                        + "compares it to AgentPreference.preferredBreakTime — a solver-derived value "
                        + "compared against a preference, same shape as the start-time row above. In "
                        + "shift mode the break start is template-fixed (D-01); whether this constraint "
                        + "should compare the template's fixed break start against the preference, or "
                        + "become moot, needs solver-level judgement Phase 14 is scoped to avoid. Not "
                        + "automatically bundled with D-03's named four break constraints — those cannot "
                        + "exist without a shift envelope, whereas this one is answerable but genuinely "
                        + "undecided today.",
                PHASE_15_OWNER));

        map.put("Break clustering", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Constraint body is `penalizeConfigurable(a -> 0)` — a documented no-op placeholder "
                        + "today (\"Evaluated as a no-op placeholder ... deferred to Phase 5 optimization\", "
                        + "ScheduleConstraintProvider.java breakClustering). Classification is moot until "
                        + "it does something; recorded as mode-agnostic because an inert constraint has no "
                        + "mode-dependent behaviour to gate.",
                null));

        map.put("Contracted hours (over)", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Compares assignment count to AgentDayConfig.effectiveHours-derived expected slots; "
                        + "ROADMAP.md's joint-unsatisfiability argument (D-06) explicitly assumes this "
                        + "constraint still applies unchanged in shift mode.",
                null));

        map.put("Contracted hours (under)", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Same basis as Contracted hours (over) — expected-slots comparison, mode-independent.",
                null));

        map.put("Contracted hours (under, zero)", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Same basis as Contracted hours (over) — penalises agents with an AgentDayConfig but "
                        + "zero assignments; mode-independent.",
                null));

        map.put("Bulk over-allocation limit", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Per-timeslot demand-vs-supply comparison; mode-independent.",
                null));

        map.put("Bulk under-allocation soft", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Per-timeslot demand-vs-supply comparison; mode-independent.",
                null));

        map.put("Bulk under-allocation hard", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Per-timeslot demand-vs-supply comparison; mode-independent.",
                null));

        map.put("Minimum staffing", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Per-timeslot floor of at least one assigned agent, irrespective of forecast; "
                        + "mode-independent.",
                null));

        return java.util.Collections.unmodifiableMap(map);
    }
}
