package com.wfm.model;

import ai.timefold.solver.core.api.domain.constraintweight.ConstraintConfiguration;
import ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeight;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.jpa.api.score.buildin.hardsoft.HardSoftScoreConverter;
import jakarta.persistence.*;
import java.util.UUID;

@ConstraintConfiguration
@Entity
@Table(name = "constraint_weights", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "desk_id"})
})
public class ConstraintWeights {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @ConstraintWeight("Unassigned assignment")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "unassigned_assignment_weight")
    private HardSoftScore unassignedAssignmentWeight = HardSoftScore.ofSoft(1000);

    @ConstraintWeight("Agent day off")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "agent_day_off_weight")
    private HardSoftScore agentDayOffWeight = HardSoftScore.ofHard(10_000);

    @ConstraintWeight("Specialization match")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "spec_match_weight")
    private HardSoftScore specMatchWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("One assignment per timeslot")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "no_overlap_weight")
    private HardSoftScore noOverlapWeight = HardSoftScore.ofHard(1000);

    @ConstraintWeight("Exactly one break")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "exactly_one_break_weight")
    private HardSoftScore exactlyOneBreakWeight = HardSoftScore.ofHard(100);

    @ConstraintWeight("Break duration")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "break_duration_weight")
    private HardSoftScore breakDurationWeight = HardSoftScore.ofHard(10);

    @ConstraintWeight("Break blocked window")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "break_blocked_window_weight")
    private HardSoftScore breakBlockedWindowWeight = HardSoftScore.ofHard(10);

    @ConstraintWeight("Break start alignment")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "break_alignment_weight")
    private HardSoftScore breakAlignmentWeight = HardSoftScore.ofHard(10);

    @ConstraintWeight("Prefer primary specialization")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "prefer_primary_weight")
    private HardSoftScore preferPrimaryWeight = HardSoftScore.ofSoft(1);

    @ConstraintWeight("Honour preferred start time")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "honour_start_time_weight")
    private HardSoftScore honourStartTimeWeight = HardSoftScore.ofSoft(5);

    @ConstraintWeight("Honour preferred break time")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "honour_break_time_weight")
    private HardSoftScore honourBreakTimeWeight = HardSoftScore.ofSoft(5);

    @ConstraintWeight("Break clustering")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "break_clustering_weight")
    private HardSoftScore breakClusteringWeight = HardSoftScore.ofSoft(2);

    @ConstraintWeight("Contracted hours (over)")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "contracted_hours_over_weight")
    private HardSoftScore contractedHoursOverWeight = HardSoftScore.ofHard(1001);

    @ConstraintWeight("Contracted hours (under)")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "contracted_hours_under_weight")
    private HardSoftScore contractedHoursUnderWeight = HardSoftScore.ofHard(100);

    @ConstraintWeight("Contracted hours (under, zero)")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "contracted_hours_under_zero_weight")
    private HardSoftScore contractedHoursUnderZeroWeight = HardSoftScore.ofHard(100);

    @ConstraintWeight("Bulk over-allocation limit")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "bulk_overallocation_limit_weight")
    private HardSoftScore bulkOverallocationLimitWeight = HardSoftScore.ofHard(1);

    @ConstraintWeight("Bulk under-allocation soft")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "bulk_underallocation_soft_weight")
    private HardSoftScore bulkUnderallocationSoftWeight = HardSoftScore.ofSoft(1);

    @ConstraintWeight("Bulk under-allocation hard")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "bulk_underallocation_hard_weight")
    private HardSoftScore bulkUnderallocationHardWeight = HardSoftScore.ofHard(1);

    /**
     * Weight for "Minimum staffing" — at least one agent on every timeslot, whatever the
     * forecast says. Soft by default so an under-supplied day still yields a schedule;
     * set to {@code ofHard(n)} to make an unstaffed hour illegal instead. 1000 outranks
     * every other soft term so it reliably pulls an agent onto an empty hour.
     */
    @ConstraintWeight("Minimum staffing")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "min_staffing_weight")
    private HardSoftScore minStaffingWeight = HardSoftScore.ofSoft(1000);

    /**
     * Weight for "Shift envelope compliance" (Phase 15, ENVL-02) — the hard constraint the whole
     * Option A coupling rests on (SPIKE-COUPLING.md). Hard by default: an agent seated outside
     * their chosen envelope is an illegal schedule, not a preference. Weight-driven like every
     * other constraint in this file, per {@code minimumStaffing}'s precedent that hard-vs-soft is
     * a per-desk configuration row, not a code decision.
     */
    @ConstraintWeight("Shift envelope compliance")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "shift_envelope_compliance_weight")
    private HardSoftScore shiftEnvelopeComplianceWeight = HardSoftScore.ofHard(1);

    /**
     * Weight for "Band capacity" (Phase 15, ENVL-08/D-03) — a band's set capacity is a hard cap
     * only when set; blank/null capacity is unlimited and never produces a tuple for this
     * constraint to penalise at all. Hard by default: an over-capacity agent-day on a band is an
     * illegal schedule, not a preference — mirroring {@code shiftEnvelopeComplianceWeight}'s
     * reasoning and V37/V38/V41's precedent that hard-vs-soft is this column's value, not a code
     * decision.
     */
    @ConstraintWeight("Band capacity")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "band_capacity_weight")
    private HardSoftScore bandCapacityWeight = HardSoftScore.ofHard(1);

    /**
     * Weight for "Shift work contiguity" (Phase 15 follow-up, G-15-27) — an agent's working hours
     * on a shift-scheduled desk must be contiguous apart from their break.
     *
     * <p>This restores for SHIFT mode a guarantee that used to hold BY ACCIDENT. D-01's
     * exact-equality eligibility rule made legal in-envelope slots equal contracted slots, so an
     * agent had to occupy every one of them and no hole was representable. V44's bounded slack
     * relaxed that equality for good reasons (see {@code AgentShiftAssignment
     * .getEligibleShiftBandPairs}) and silently took the contiguity guarantee with it — in the one
     * mode where nothing else enforces it, because {@code exactlyOneBreak}, {@code breakDuration},
     * {@code breakBlockedWindow} and {@code breakStartAlignment} are all mode-gated off in SHIFT
     * mode (the band defines the break, so seat-derived break geometry is the wrong instrument).
     *
     * <p>Hard, and weighted 10 (V46) — strictly above {@code shiftEnvelopeComplianceWeight} so a
     * split shift still outranks a single out-of-envelope seat, but not so far above that it
     * dominates the model. This shipped at 100 in V45 and was WRONG at that scale: against an
     * envelope weight of 1, a 100:1 ratio makes it rational to breach the envelope 99 times to
     * avoid one split, and the live desk produced 52 envelope violations doing so. Measured on
     * that desk, holding every operator requirement fixed and varying only the ratio:
     * 100:1 gave 52 violations, 100:100 gave 1 but was too rigid to place agents, 10:1 gave 6,
     * and 10:10 gave 3 — the best measured. See G-15-30.
     */
    @ConstraintWeight("Shift work contiguity")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "shift_work_contiguity_weight")
    private HardSoftScore shiftWorkContiguityWeight = HardSoftScore.ofHard(10);

    /**
     * Weight for "Usual shift consistency" (Phase 17, CONS-01/CONS-02/CONS-04) — a per-agent-day
     * target-deviation penalty comparing the assigned shift envelope start against the agent's
     * era-resolved usual shift start ({@code ShiftBandPair.startDeviationMinutes}), past the
     * {@link #consistencyToleranceMinutes} dead zone.
     *
     * <p>This ADOPTS the {@code consistent_start_weight} column V38 already created — that
     * migration shipped the column with no Java field pointed at it (an orphan), sized for a
     * PER-AGENT penalty this phase's PER-AGENT-DAY formulation replaces (D-02/D-06; see V48's
     * migration comment). V38's own comment already states why this must stay soft: "A hard
     * consistency rule does not force consistent starts; it forces shorter shifts."
     *
     * <p>D-07: the hard component MUST stay zero. This is ENFORCED, not merely documented — a
     * non-zero hard component here is rejected at save time by {@code ConstraintWeightsService}
     * (plan 17-02), not just discouraged in a comment. This deliberately diverges from Phase 15's
     * precedent that hard-vs-soft is "a per-desk configuration row, not a code decision"
     * ({@code shiftEnvelopeComplianceWeight}, {@code bandCapacityWeight}) — for those constraints
     * hard is the correct setting; here hard is a documented failure mode, exactly as V38's
     * comment already warned.
     */
    @ConstraintWeight("Usual shift consistency")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "consistent_start_weight")
    private HardSoftScore consistentStartWeight = HardSoftScore.ofSoft(2);

    /**
     * Tolerance band, in minutes, for "Usual shift consistency" (Phase 17, D-04/D-05) — a
     * deviation up to and including this many minutes is a genuine dead zone (zero penalty); the
     * first penalised minute is one beyond it. Symmetric: applied to {@code abs(delta)}, never an
     * early bound and a late bound separately (D-05; asymmetry deferred, not rejected).
     *
     * <p>DELIBERATE CONVENTION BREAK, stated here rather than left to be rediscovered: every
     * other non-key column in this {@code @ConstraintConfiguration} is a {@link HardSoftScore}.
     * This is a plain {@code int} — kept on {@code constraint_weights} (not {@code Desk}, not
     * {@code Schedule}/{@code ScheduleConfig}) so the band and the weight acting on it are one
     * row, one screen, one API call (CONS-02). See {@code 17-CONTEXT.md} D-04 for the full
     * rationale, including the two rejected alternatives.
     */
    @Column(name = "consistency_tolerance_minutes")
    private int consistencyToleranceMinutes = 60;

    /**
     * Weight for "Preferred start (shift mode)" (Phase 17, CONS-05/CONS-06/D-08) — a NEW
     * shift-granularity preference constraint (plan 17-02's {@code
     * ScheduleConstraintProvider.preferredStartShiftMode}) penalising the absolute deviation
     * between the assigned envelope start and the agent's {@code preferredStartTime},
     * anchor-style in both directions (never lateness-only).
     *
     * <p>Plan 17-01 declared this field, with V48, WITHOUT this annotation — annotating it before
     * the constraint method that reads it existed would have orphaned a constraint-weight with no
     * builder method, failing {@code ScheduleConstraintClassificationTest}'s reflective
     * completeness guard (Phase 14 P-07, XCUT-05). Plan 17-02 adds both the annotation and the
     * constraint method in the same commit, closing that gap (see 17-01-SUMMARY.md Deviation 1).
     *
     * <p>D-08: this weight's soft score MUST stay strictly below {@link #consistentStartWeight}'s
     * soft score — consistency always outranks preference. Enforced at save time (plan 17-02's
     * {@code ConstraintWeightsService}), not merely by convention, and covered by a named test
     * (CONS-06's "documented and observable", not "implicit in relative constraint weights a
     * reader would have to reverse-engineer").
     */
    @ConstraintWeight("Preferred start (shift mode)")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "preferred_start_shift_mode_weight")
    private HardSoftScore preferredStartShiftModeWeight = HardSoftScore.ofSoft(1);

    /**
     * Weight for "Shift start mix" (Phase 18, MIX-02) — how hard the solver is pushed toward the
     * pre-solve start-time head counts in {@code ShiftStartMixTargetService}.
     *
     * <p><b>Ships at zero, i.e. the constraint is inert, because the steer is MEASURED NOT TO
     * WORK.</b> On {@code LiveShapeShiftDeskFixture} a contrary target moves the solved start mix
     * by exactly nothing at soft 25, 200, 2,000 and 100,000, and at {@code ofHard(1)} — the solver
     * absorbs 16 extra hard points rather than move one agent-day. The constraint scores correctly
     * throughout; the search simply never revises the mix. That is the same rigidity already on
     * record for {@code consistentStartWeight}, swept at 2, 5 and 60 on the live desk with no
     * effect on the mix either: the construction heuristic fixes the start mix, and no weight on
     * {@code AgentShiftAssignment.shiftBandPair} revises it afterwards, because revising it means
     * re-pointing the seats too and {@code solverConfig.xml}'s {@code 0hard} annealing temperature
     * refuses every intermediate state.
     *
     * <p>A non-zero value here is therefore a pure score-reporting change today: it will show the
     * mix deviation in {@code explain()} without altering the schedule. Useful for measurement,
     * misleading if mistaken for a fix.
     *
     * <p><b>The fix this is waiting on is structural, not numeric.</b> The target has to constrain
     * what the CH may build — the per-row value range, or a pre-assigned envelope — rather than
     * price what it did build. See {@code ShiftStartMixSteerTest}'s disabled test, which is
     * written to pass the moment that lands.
     */
    @ConstraintWeight("Shift start mix")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "shift_start_mix_weight")
    private HardSoftScore shiftStartMixWeight = HardSoftScore.ZERO;

    /**
     * (Phase 18, MIX-03) How far the pre-solve start-mix target reaches on this desk — see
     * {@link ShiftStartMixMode}. A plain enum column, not a {@code HardSoftScore}, and the second
     * deliberate convention break on this table after {@code consistencyToleranceMinutes} (V48
     * D-04): it is a mode, not a price, and it belongs on the same per-desk operator-editable row
     * as the weight it governs so the two stay one row, one screen, one API call.
     *
     * <p>{@code OFF} by default. {@code ENFORCE} is the only value that changes a schedule.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "shift_start_mix_mode", nullable = false, length = 16)
    private ShiftStartMixMode shiftStartMixMode = ShiftStartMixMode.OFF;

    public ConstraintWeights() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public HardSoftScore getUnassignedAssignmentWeight() { return unassignedAssignmentWeight; }
    public void setUnassignedAssignmentWeight(HardSoftScore unassignedAssignmentWeight) { this.unassignedAssignmentWeight = unassignedAssignmentWeight; }

    public HardSoftScore getAgentDayOffWeight() { return agentDayOffWeight; }
    public void setAgentDayOffWeight(HardSoftScore agentDayOffWeight) { this.agentDayOffWeight = agentDayOffWeight; }

    public HardSoftScore getSpecMatchWeight() { return specMatchWeight; }
    public void setSpecMatchWeight(HardSoftScore specMatchWeight) { this.specMatchWeight = specMatchWeight; }

    public HardSoftScore getNoOverlapWeight() { return noOverlapWeight; }
    public void setNoOverlapWeight(HardSoftScore noOverlapWeight) { this.noOverlapWeight = noOverlapWeight; }

    public HardSoftScore getExactlyOneBreakWeight() { return exactlyOneBreakWeight; }
    public void setExactlyOneBreakWeight(HardSoftScore exactlyOneBreakWeight) { this.exactlyOneBreakWeight = exactlyOneBreakWeight; }

    public HardSoftScore getBreakDurationWeight() { return breakDurationWeight; }
    public void setBreakDurationWeight(HardSoftScore breakDurationWeight) { this.breakDurationWeight = breakDurationWeight; }

    public HardSoftScore getBreakBlockedWindowWeight() { return breakBlockedWindowWeight; }
    public void setBreakBlockedWindowWeight(HardSoftScore breakBlockedWindowWeight) { this.breakBlockedWindowWeight = breakBlockedWindowWeight; }

    public HardSoftScore getBreakAlignmentWeight() { return breakAlignmentWeight; }
    public void setBreakAlignmentWeight(HardSoftScore breakAlignmentWeight) { this.breakAlignmentWeight = breakAlignmentWeight; }

    public HardSoftScore getPreferPrimaryWeight() { return preferPrimaryWeight; }
    public void setPreferPrimaryWeight(HardSoftScore preferPrimaryWeight) { this.preferPrimaryWeight = preferPrimaryWeight; }

    public HardSoftScore getHonourStartTimeWeight() { return honourStartTimeWeight; }
    public void setHonourStartTimeWeight(HardSoftScore honourStartTimeWeight) { this.honourStartTimeWeight = honourStartTimeWeight; }

    public HardSoftScore getHonourBreakTimeWeight() { return honourBreakTimeWeight; }
    public void setHonourBreakTimeWeight(HardSoftScore honourBreakTimeWeight) { this.honourBreakTimeWeight = honourBreakTimeWeight; }

    public HardSoftScore getBreakClusteringWeight() { return breakClusteringWeight; }
    public void setBreakClusteringWeight(HardSoftScore breakClusteringWeight) { this.breakClusteringWeight = breakClusteringWeight; }

    public HardSoftScore getContractedHoursOverWeight() { return contractedHoursOverWeight; }
    public void setContractedHoursOverWeight(HardSoftScore contractedHoursOverWeight) { this.contractedHoursOverWeight = contractedHoursOverWeight; }

    public HardSoftScore getContractedHoursUnderWeight() { return contractedHoursUnderWeight; }
    public void setContractedHoursUnderWeight(HardSoftScore contractedHoursUnderWeight) { this.contractedHoursUnderWeight = contractedHoursUnderWeight; }

    public HardSoftScore getContractedHoursUnderZeroWeight() { return contractedHoursUnderZeroWeight; }
    public void setContractedHoursUnderZeroWeight(HardSoftScore contractedHoursUnderZeroWeight) { this.contractedHoursUnderZeroWeight = contractedHoursUnderZeroWeight; }

    public HardSoftScore getBulkOverallocationLimitWeight() { return bulkOverallocationLimitWeight; }
    public void setBulkOverallocationLimitWeight(HardSoftScore bulkOverallocationLimitWeight) { this.bulkOverallocationLimitWeight = bulkOverallocationLimitWeight; }

    public HardSoftScore getBulkUnderallocationSoftWeight() { return bulkUnderallocationSoftWeight; }
    public void setBulkUnderallocationSoftWeight(HardSoftScore bulkUnderallocationSoftWeight) { this.bulkUnderallocationSoftWeight = bulkUnderallocationSoftWeight; }

    public HardSoftScore getBulkUnderallocationHardWeight() { return bulkUnderallocationHardWeight; }
    public void setBulkUnderallocationHardWeight(HardSoftScore bulkUnderallocationHardWeight) { this.bulkUnderallocationHardWeight = bulkUnderallocationHardWeight; }

    public HardSoftScore getMinStaffingWeight() { return minStaffingWeight; }
    public void setMinStaffingWeight(HardSoftScore minStaffingWeight) { this.minStaffingWeight = minStaffingWeight; }

    public HardSoftScore getShiftEnvelopeComplianceWeight() { return shiftEnvelopeComplianceWeight; }
    public void setShiftEnvelopeComplianceWeight(HardSoftScore shiftEnvelopeComplianceWeight) { this.shiftEnvelopeComplianceWeight = shiftEnvelopeComplianceWeight; }

    public HardSoftScore getBandCapacityWeight() { return bandCapacityWeight; }
    public void setBandCapacityWeight(HardSoftScore bandCapacityWeight) { this.bandCapacityWeight = bandCapacityWeight; }

    public HardSoftScore getShiftWorkContiguityWeight() { return shiftWorkContiguityWeight; }
    public void setShiftWorkContiguityWeight(HardSoftScore shiftWorkContiguityWeight) { this.shiftWorkContiguityWeight = shiftWorkContiguityWeight; }

    public HardSoftScore getConsistentStartWeight() { return consistentStartWeight; }
    public void setConsistentStartWeight(HardSoftScore consistentStartWeight) { this.consistentStartWeight = consistentStartWeight; }

    public int getConsistencyToleranceMinutes() { return consistencyToleranceMinutes; }
    public void setConsistencyToleranceMinutes(int consistencyToleranceMinutes) { this.consistencyToleranceMinutes = consistencyToleranceMinutes; }

    public HardSoftScore getPreferredStartShiftModeWeight() { return preferredStartShiftModeWeight; }
    public void setPreferredStartShiftModeWeight(HardSoftScore preferredStartShiftModeWeight) { this.preferredStartShiftModeWeight = preferredStartShiftModeWeight; }

    public HardSoftScore getShiftStartMixWeight() { return shiftStartMixWeight; }
    public void setShiftStartMixWeight(HardSoftScore shiftStartMixWeight) { this.shiftStartMixWeight = shiftStartMixWeight; }

    public ShiftStartMixMode getShiftStartMixMode() { return shiftStartMixMode; }
    public void setShiftStartMixMode(ShiftStartMixMode shiftStartMixMode) { this.shiftStartMixMode = shiftStartMixMode; }
}
