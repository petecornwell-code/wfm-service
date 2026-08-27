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
     * <p>{@link #MODE_GATED} was deliberately unused at Phase 14 close — ROADMAP.md success
     * criterion 5 recorded that the four break constraints tagged {@code NEEDS_SHIFT_VARIANT}
     * "aren't actually mode-gated until Phase 15". Phase 15 (plan 15-06) is that move: six rows
     * now carry {@code MODE_GATED} (see the note on {@link #classifications()}), and
     * {@code NEEDS_SHIFT_VARIANT}/{@code OPEN_RESOLVE_IN_PHASE_15} are both empty from this phase
     * onward — the constants stay in the vocabulary as the historical record of what Phase 14 left
     * open and Phase 15 closed, not because either is expected to be used again.
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
     *
     * <p><strong>Phase 15 resolution (2026-08-27, plan 15-06).</strong> Task 1 moves six rows:
     * the four D-03-named break constraints ("Exactly one break", "Break duration", "Break
     * blocked window", "Break start alignment") from {@code NEEDS_SHIFT_VARIANT} to
     * {@code MODE_GATED}, and the two preference constraints ("Honour preferred start time",
     * "Honour preferred break time") from {@code OPEN_RESOLVE_IN_PHASE_15} to {@code MODE_GATED}
     * — zero rows remain {@code OPEN_RESOLVE_IN_PHASE_15} after Task 1 (XCUT-05 complete).
     * {@code PHASE_15_OWNER}'s string is retained verbatim on the two preference rows — it still
     * reads the phase's *former* name ("Shift Envelope & Coupling") deliberately, as a historical
     * record of who resolved them; renaming or deleting the constant would break
     * {@code 14-VERIFICATION.md} item 21's verified claim that the constant matches its markdown
     * mirror byte-for-byte (P-27). Task 2 reclassifies "Break clustering" from
     * {@code MODE_AGNOSTIC} to {@code MODE_GATED} once it has a real body (ENVL-09). Task 3 adds
     * a new "Band capacity" row, also {@code MODE_GATED} (ENVL-08/D-03).
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
                ModeClassification.MODE_GATED,
                "Phase 15 resolution (ENVL-05): reclassified from NEEDS_SHIFT_VARIANT. Break "
                        + "placement in shift mode is the assigned band's offset, not something to "
                        + "discover from assignment gaps (countContiguousGaps/getGapLengths) -- there is "
                        + "no longer a question for a shift-mode variant to answer, so the constraint is "
                        + "simply gated off for SHIFT desks (ifExists(ScheduleConfig, filtering(mode != "
                        + "SHIFT)); the body is untouched byte-for-byte, P-25) and stays fully active, "
                        + "unchanged, for SLOT desks.",
                null));

        map.put("Break duration", new Entry(
                ModeClassification.MODE_GATED,
                "Phase 15 resolution (ENVL-05): same reasoning as 'Exactly one break' -- the "
                        + "template's fixed break_duration_minutes (now a band's duration_minutes) "
                        + "replaces the single assignment gap's length as the only place break duration "
                        + "is defined in shift mode, so the constraint is gated off there and unchanged "
                        + "for SLOT desks.",
                null));

        map.put("Break blocked window", new Entry(
                ModeClassification.MODE_GATED,
                "Phase 15 resolution (ENVL-05): same reasoning as 'Exactly one break' -- the "
                        + "template's fixed envelope and band offset replace the derived shift "
                        + "start/end and gap position as the only place break position is decided in "
                        + "shift mode, so the constraint is gated off there and unchanged for SLOT desks.",
                null));

        map.put("Break start alignment", new Entry(
                ModeClassification.MODE_GATED,
                "Phase 15 resolution (ENVL-05): same reasoning as 'Exactly one break' -- the "
                        + "band's offset is fixed at template-authoring time (D-01, itself grid-aligned "
                        + "by Phase 14's D-02), so there is no solver-chosen break start left to check "
                        + "in shift mode; the constraint is gated off there and unchanged for SLOT desks.",
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

        map.put("Band capacity", new Entry(
                ModeClassification.MODE_GATED,
                "Phase 15 (ENVL-08/D-03): groups AgentShiftAssignment by (date, shiftBandPair), "
                        + "penalising agent-day counts on a pair that exceed its band's set capacity. A "
                        + "blank capacity produces no tuple at all -- unlimited, not zero. Inert on a "
                        + "SLOT desk because no AgentShiftAssignment rows exist there to group, the same "
                        + "structural-inertness shape as 'Shift envelope compliance'.",
                null));

        map.put("Prefer primary specialization", new Entry(
                ModeClassification.MODE_AGNOSTIC,
                "Pure agent-attribute soft preference (primary vs. secondary specialization); "
                        + "unaffected by shift structure.",
                null));

        map.put("Honour preferred start time", new Entry(
                ModeClassification.MODE_GATED,
                "Phase 15 resolution (ENVL-05/P-26): reclassified from OPEN_RESOLVE_IN_PHASE_15. "
                        + "In shift mode the agent's start comes from the assigned library shift, not a "
                        + "per-slot solver decision, so this constraint would tune against a signal the "
                        + "operator no longer controls per-slot -- gated off for SHIFT desks and "
                        + "unchanged for SLOT desks. PHASE_15_OWNER is retained verbatim as the recorded "
                        + "resolver (P-27) even though the row is no longer OPEN -- the Entry record "
                        + "permits an owner on any classification, and the phase's former name stays "
                        + "byte-identical to its markdown mirror. Phase 17's CONS-05 use of "
                        + "preferredStartTime at shift granularity (as a tiebreak between two "
                        + "equally-scored shifts) is a NEW use of the preference, not a reason to leave "
                        + "this per-slot constraint on.",
                PHASE_15_OWNER));

        map.put("Honour preferred break time", new Entry(
                ModeClassification.MODE_GATED,
                "Phase 15 resolution (ENVL-05/P-26): reclassified from OPEN_RESOLVE_IN_PHASE_15, "
                        + "same reasoning as 'Honour preferred start time' -- in shift mode the break "
                        + "comes from the assigned band, not a solver-derived gap, so this constraint "
                        + "would tune against a signal the operator no longer controls per-slot. Gated "
                        + "off for SHIFT desks and unchanged for SLOT desks. PHASE_15_OWNER retained "
                        + "verbatim (P-27).",
                PHASE_15_OWNER));

        map.put("Break clustering", new Entry(
                ModeClassification.MODE_GATED,
                "Phase 15 (ENVL-09) gave this constraint a real body, reclassified from "
                        + "MODE_AGNOSTIC (the prior row described only the inert placeholder): a "
                        + "cross-agent, per-timeslot aggregation penalising on-break agents exceeding "
                        + "breakClusterThresholdPct percent of the timeslot's assigned agents. The "
                        + "on-break half explicitly gates SchedulingMode.SHIFT and is structurally inert "
                        + "on a SLOT desk (zero AgentShiftAssignment rows to derive 'on break' from), so "
                        + "the whole constraint's penalty is zero on every SLOT desk by construction -- "
                        + "the same mode-dependent-behaviour shape as 'Shift envelope compliance'. The "
                        + "assigned-agent half of the aggregation runs identically in both modes, exactly "
                        + "as it always has.",
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
