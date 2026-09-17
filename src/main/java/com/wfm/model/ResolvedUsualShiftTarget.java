package com.wfm.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Pre-resolved usual-shift target for one (agent, date), fed to the solver as a problem fact
 * (Phase 17, D-11). Built by {@code SolverService.resolveUsualShiftTargets} before solving, one
 * per working agent-day whose stored usual-shift row resolves to a live template for that
 * date — see {@code UsualShiftResolutionService}'s javadoc: "Do not create a second copy of this
 * method." This record carries the ALREADY-resolved era result; it performs no further
 * resolution and holds no reference back to the stored row.
 *
 * <p>Styled like {@link ScheduleConfig} — a plain immutable record, no behaviour beyond field
 * access, safe to share as a Timefold {@code @ProblemFactCollectionProperty} entry.
 *
 * <p>An agent-day with no matching {@code ResolvedUsualShiftTarget} is USHF-04's penalty-free
 * state falling out of a join finding no match — never a special case in the constraint or the
 * report that reads this record.
 */
public record ResolvedUsualShiftTarget(UUID agentId, LocalDate date, LocalTime usualStartTime) {}
