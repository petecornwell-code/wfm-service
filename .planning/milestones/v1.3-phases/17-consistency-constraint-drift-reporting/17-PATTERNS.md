# Phase 17: Consistency Constraint & Drift Reporting - Pattern Map

**Mapped:** 2026-09-04
**Files analyzed:** 19 (backend: 13 new/modified, frontend: 3, migration: 1, test: 5 new + 1 modified)
**Analogs found:** 19 / 19 (all have a strong same-codebase analog; no "no analog" files this phase)

## Resolved Open Questions (from RESEARCH.md, verified this session against HEAD)

1. **Do `ConstraintWeightsServiceTest` / `ConstraintWeightsControllerTest` already exist?**
   **No.** `find src/test -iname "*ConstraintWeights*"` returns nothing. Wave 0 must create both
   test classes from scratch — no existing harness to extend. Follow `ConstraintWeightsService`/
   `ConstraintWeightsController`'s own structure (plain `@Service`/`@RestController`, `TenantContext`
   scoping) for what the tests exercise; there is no sibling test file to copy setup boilerplate from,
   so use a standard `@ExtendWith(MockitoExtension.class)` unit-test shape for the service and
   `@WebMvcTest`/`MockMvc` (check what other simple controllers in `src/test/java/com/wfm/controller/`
   use) for the controller.

2. **Does `ConstraintWeights` already reach the solver as a problem fact?**
   **Yes, already wired — no new plumbing needed for the tolerance-band/weight values themselves.**
   `Schedule.java:106` has `@ConstraintConfigurationProvider private ConstraintWeights
   constraintWeights;`, and `SolverService.java:375` calls `schedule.setConstraintWeights(weights)`
   after loading it from `constraintWeightsRepository` (around `SolverService.java:232`). This means
   any new `@ConstraintWeight` field (the two new weight columns) and any plain new field on
   `ConstraintWeights` (the tolerance-band int) are automatically visible inside
   `ScheduleConstraintProvider` via `factory.forEach(ConstraintWeights.class)` or by reading the
   `ConstraintConfiguration` instance directly in a lambda — exactly like every existing
   `@ConstraintWeight` field. **The one genuinely new piece of plumbing (confirmed) is the resolved
   usual-shift *target* itself** (`ResolvedUsualShiftTarget`, per-agent-per-date, era-resolved) —
   that is not on `ConstraintWeights` at all, has no existing carrier, and must be built via the
   `resolvePreferences` pre-solve pattern (see Pattern Assignment below). Do not conflate the two:
   config values need no new wiring; the resolved target does.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/resources/db/migration/V48__*.sql` | migration | batch | `V38__add_consistent_start_weight.sql`, `V42__add_band_capacity_weight.sql` | exact |
| `src/main/java/com/wfm/model/ResolvedUsualShiftTarget.java` (NEW) | model (problem-fact record) | transform | `ScheduleConfig.java` | exact (house convention for immutable problem-fact records) |
| `src/main/java/com/wfm/model/ShiftBandPair.java` (+static method) | model (shared calculation) | transform | itself — `covers(...)` static overload | exact |
| `src/main/java/com/wfm/model/ConstraintWeights.java` (+3 fields) | model (`@ConstraintConfiguration`) | CRUD | itself — existing 22 `@ConstraintWeight` fields, `shiftWorkContiguityWeight` most recent | exact |
| `src/main/java/com/wfm/model/Schedule.java` (+field) | model | CRUD | itself — existing `@ProblemFactCollectionProperty` fields | exact |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (+2 constraints) | service (Timefold constraint stream) | transform/event-driven | `shiftEnvelopeCompliance` (stream-order contract), `honourPreferredStartTime` (mode-gating shape, anchor-style idea from reverted `7861b83`) | exact |
| `src/main/java/com/wfm/service/SolverService.java` (+resolveUsualShiftTargets) | service (pre-solve resolution) | transform | `resolvePreferences` (`SolverService.java:567`) | exact |
| `src/main/java/com/wfm/service/ConstraintWeightsService.java` (+3 fields, +save-time validation) | service | CRUD | itself — `updateWeights`'s partial-update `if (x != null)` merge shape | exact |
| `src/main/java/com/wfm/dto/ConstraintWeightsDto.java` (+3 fields) | model (DTO) | transform | itself — existing `ScoreDto` fields | exact |
| `src/main/java/com/wfm/controller/ConstraintWeightsController.java` (unchanged shape, new fields flow through) | controller | request-response | itself | exact |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` (+buildDriftReport) | service (report builder) | request-response (derived-on-read) | `buildPreferenceReport` (`:289`), `buildShiftDescriptorsByAgentDate` | exact |
| `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` (+DriftReport records) | model (DTO) | transform | `PreferenceReport`/`PreferenceReportEntry` records (`:115-130`) | exact |
| `src/main/java/com/wfm/service/ScheduleService.java` (+wire+date-filter driftReport) | service | request-response | itself — `:200-228` preferenceReport date filtering | exact |
| `src/main/java/com/wfm/service/ScheduleExportService.java` (+writeDriftReport) | service (file I/O) | file-I/O | `writePreferenceReport` (`:172`) | exact |
| `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` (+2 rows) | test (completeness guard) | — | itself | exact |
| `src/test/java/com/wfm/solver/UsualShiftConsistencyConstraintTest.java` (NEW) | test | — | `BandCapacityConstraintTest.java` | exact |
| `src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java` (NEW) | test | — | `BandCapacityConstraintTest.java` | exact |
| `src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java` (NEW, no prior file) | test | — | none direct — build fresh; see Resolved Open Question 1 | role-match only |
| `frontend/src/pages/ScheduleResults.tsx` (+Drift Report tab) | component (React) | request-response | `PreferenceTab` (`:989`), tab bar (`:254-262`) | exact |
| `frontend/src/pages/ConstraintWeightsPage.tsx` (+3 rows, 1 non-Score) | component (React form) | CRUD | itself — `CONSTRAINTS`/`DEFAULTS` arrays, row-render loop (`:99`) | exact |
| `frontend/src/api/client.ts` (+DriftReport* interfaces, +3 ConstraintWeightsData fields) | utility (API types) | transform | `PreferenceReport` TS interfaces already in the file | exact |

## Pattern Assignments

### `src/main/resources/db/migration/V48__*.sql`

**Analog:** `src/main/resources/db/migration/V38__add_consistent_start_weight.sql` (adopted column) and `V42__add_band_capacity_weight.sql` (most recent sibling weight-column migration)

```sql
-- V38 (verified, full comment read this session):
ALTER TABLE constraint_weights
    ADD COLUMN consistent_start_weight VARCHAR(50) NOT NULL DEFAULT '0hard/2soft';
-- V38's comment sizes 2 soft for a PER-AGENT penalty (28 CSRs x 4 increments x 2 = 224 soft).
-- D-02/D-06/Pitfall 4: this phase's charging is PER-AGENT-DAY. The V48 migration comment must
-- redo this arithmetic for the new formulation and MUST NOT carry `2` forward unexamined —
-- write the benchmark-derived default from 17-BENCHMARK.md instead.
```

V48 must, in one migration:
1. Add `consistent_start_weight`'s Java-side field is new (`ConstraintWeights.consistentStartWeight`), but the **column already exists** since V38 — do not re-add the column; only the Java entity field is new. Confirm no second `ALTER TABLE ... ADD COLUMN consistent_start_weight` is emitted (would fail — column already present).
2. `UPDATE constraint_weights SET consistent_start_weight = '<benchmark-derived>hard/<benchmark-derived>soft'` only if V38's shipped default (`0hard/2soft`) needs correcting per D-06 — the migration comment must state the corrected arithmetic explicitly, referencing V38's comment by migration number and quoting why the old sizing no longer applies.
3. `ADD COLUMN consistency_tolerance_minutes INT NOT NULL DEFAULT <planner's discretion value>` (D-04, plain int, not a `HardSoftScore` pair).
4. `ADD COLUMN <new_preference_weight_column> VARCHAR(50) NOT NULL DEFAULT '<benchmark-derived>hard/<benchmark-derived>soft'` (D-08), named to match whatever Java field name is chosen (e.g. `preferred_start_shift_mode_weight`).

Confirmed migration head this session: **V47** is the highest-numbered file on disk
(`ls db/migration | grep -oE '^V[0-9]+' | sort -n | tail -1` → `47`). **V48 is correct and current** — re-run this `ls` immediately before writing the migration if time has passed.

---

### `src/main/java/com/wfm/model/ResolvedUsualShiftTarget.java` (NEW)

**Analog:** `src/main/java/com/wfm/model/ScheduleConfig.java`

```java
// Source: src/main/java/com/wfm/model/ScheduleConfig.java (verified, full file, 20 lines)
package com.wfm.model;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Immutable problem fact holding schedule configuration values
 * so constraints can access them via join/forEach in constraint streams.
 */
public record ScheduleConfig(
        int incrementMinutes,
        LocalTime startTime,
        // ... plain scalar fields only, no repository access
        SchedulingMode schedulingMode
) {}
```

New file, same house style (plain immutable record, no behavior beyond field access):
```java
package com.wfm.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Pre-resolved usual-shift target for one (agent, date), fed to the solver as a problem fact.
 *  Built by SolverService.resolveUsualShiftTargets before solving — see UsualShiftResolutionService
 *  javadoc: "Do not create a second copy of this method." This record carries the ALREADY-resolved
 *  era-by-name result; it performs no further resolution.  */
public record ResolvedUsualShiftTarget(UUID agentId, LocalDate date, LocalTime usualStartTime) {}
```

Wire onto `Schedule.java` alongside its sibling `@ProblemFactCollectionProperty` fields (`:110-159`):
```java
@ProblemFactCollectionProperty
private List<ResolvedUsualShiftTarget> resolvedUsualShiftTargets = new ArrayList<>();
```

---

### `src/main/java/com/wfm/model/ShiftBandPair.java` (+static `startDeviationMinutes`)

**Analog:** itself — the `covers(...)` static-overload precedent (verified, lines 18-70+)

```java
// Source: src/main/java/com/wfm/model/ShiftBandPair.java:18-70 (verified this session)
public record ShiftBandPair(ShiftTemplate template, ShiftTemplateBreakBand band) {

    // Instance convenience method delegates to the static form with zero behavioural difference:
    public boolean covers(Timeslot ts) {
        return covers(template.getStartTime(), template.getEndTime(),
                band == null ? null : band.getOffsetMinutes(),
                band == null ? null : band.getDurationMinutes(),
                ts.getStartTime(), ts.getEndTime());
    }

    // The ONE implementation — every caller (constraint stream, report layer) calls this static
    // form directly when it only holds scalars, not the wrapping record.
    public static boolean covers(LocalTime envelopeStart, LocalTime envelopeEnd,
            Integer bandOffsetMinutes, Integer bandDurationMinutes,
            LocalTime slotStart, LocalTime slotEnd) {
        if (slotStart.isBefore(envelopeStart) || slotEnd.isAfter(envelopeEnd)) {
            return false;
        }
        if (bandOffsetMinutes == null || bandDurationMinutes == null || bandDurationMinutes <= 0) {
            return true;
        }
        LocalTime breakStart = envelopeStart.plusMinutes(bandOffsetMinutes);
        LocalTime breakEnd = breakStart.plusMinutes(bandDurationMinutes);
        boolean overlapsBreak = slotStart.isBefore(breakEnd) && slotEnd.isAfter(breakStart);
        return !overlapsBreak;
    }
}
```

New static method, same file, same shape (this is DRFT-03's literal requirement — "the ONE distance calculation"):
```java
/** The ONE distance calculation DRFT-03 requires — the usualShiftConsistency constraint and
 *  ScheduleOutputService.buildDriftReport both call this, never re-implementing the delta inline.
 *  D-03: compares the ASSIGNED ENVELOPE START (this.template().getStartTime()), never a seat-derived
 *  earliest-assignment time — a template with the same start and different duration reads zero drift
 *  (accepted D-01 blind spot, stated here in the javadoc, not just in CONTEXT.md). */
public static int startDeviationMinutes(LocalTime assignedEnvelopeStart, LocalTime usualStartTime) {
    if (assignedEnvelopeStart == null || usualStartTime == null) return 0;
    return (int) Math.abs(java.time.temporal.ChronoUnit.MINUTES.between(usualStartTime, assignedEnvelopeStart));
}
```

Callers:
- Constraint: `sa.getShiftBandPair().template().getStartTime()` vs `ResolvedUsualShiftTarget.usualStartTime()`.
- Report: `resolveShiftDescriptor(sa).startTime()` (or equivalent) vs `UsualShiftResolutionService.resolve(...).get().getStartTime()`.

---

### `src/main/java/com/wfm/model/ConstraintWeights.java` (+3 fields)

**Analog:** itself — existing 22-field pattern, `shiftWorkContiguityWeight` as the most recent addition's javadoc-documentation model

```java
// Source: src/main/java/com/wfm/model/ConstraintWeights.java:1-40 (verified this session, full header + first fields)
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
    // ... existing @Id, tenantId, deskId ...

    @ConstraintWeight("Unassigned assignment")
    @Convert(converter = HardSoftScoreConverter.class)
    @Column(name = "unassigned_assignment_weight")
    private HardSoftScore unassignedAssignmentWeight = HardSoftScore.ofSoft(1000);
    // ... 21 more identical-shape fields ...
}
```

New fields to add, following this exact shape for the two `@ConstraintWeight` score fields, and a genuinely new (D-04-flagged, code-commented) plain-int field for the tolerance band:
```java
@ConstraintWeight("Usual shift consistency")
@Convert(converter = HardSoftScoreConverter.class)
@Column(name = "consistent_start_weight")
private HardSoftScore consistentStartWeight = HardSoftScore.ofSoft(<D-06 benchmark value>);
// D-07: hard component MUST stay 0 — enforced at ConstraintWeightsService save time (see below),
// not merely documented. A hard rule here forces SHORTER shifts, not consistent starts (V38's
// own comment). This field adopts the V38 orphan column that existed in the DB with no Java field.

@Column(name = "consistency_tolerance_minutes")
private int consistencyToleranceMinutes = <planner's discretion default>;
// D-04: DELIBERATE CONVENTION BREAK — every other non-key column in this @ConstraintConfiguration
// is a HardSoftScore; this is a plain int. Kept on constraint_weights (not Desk, not
// Schedule/ScheduleConfig) so the tolerance band and the weight acting on it are one row, one
// screen, one API call (CONS-02). See 17-CONTEXT.md D-04 for full rationale.

@ConstraintWeight("Preferred start (shift mode)")
@Convert(converter = HardSoftScoreConverter.class)
@Column(name = "<planner-chosen column name>")
private HardSoftScore preferredStartShiftModeWeight = HardSoftScore.ofSoft(<D-06 benchmark value>);
// D-08: must stay strictly below consistentStartWeight's soft score — enforced at save time
// (ConstraintWeightsService), not merely by convention. See D-08/D-10.
```

---

### `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (+2 constraints)

**Analog 1 (stream-ordering contract):** `shiftEnvelopeCompliance` (`:422`, verified in full with javadoc)

```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:422-434 (verified)
// Javadoc states the measured cost of getting stream order wrong: leading with AgentAssignment
// instead of AgentShiftAssignment cost ~3x construction-heuristic throughput
// (1049ms -> 347ms for the same 480 steps). "Do not reorder these joins."
Constraint shiftEnvelopeCompliance(ConstraintFactory factory) {
    return factory.forEachIncludingUnassigned(AgentShiftAssignment.class)
            .join(ScheduleConfig.class)
            .filter((sa, cfg) -> cfg.schedulingMode() == SchedulingMode.SHIFT)
            .join(AgentAssignment.class,
                    equal((sa, cfg) -> sa.getAgent().getId(), a -> a.getAgent().getId()),
                    equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getDate()))
            .filter((sa, cfg, a) -> sa.getShiftBandPair() == null
                    || !sa.getShiftBandPair().covers(a.getTimeslot()))
            .penalizeConfigurable()
            .asConstraint(SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME);
}
```

**Analog 2 (mode-gating shape + the idea to reuse, not the patch):** `honourPreferredStartTime` (`:813`, verified)

```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:813-829 (verified)
// CURRENT (unpatched) form — confirms it is mode-gated OFF for SHIFT and still isBefore-only.
// Its own javadoc: "Phase 17's CONS-05 use of preferredStartTime at shift granularity is a new
// use, not a reason to leave this per-slot constraint on." DO NOT PATCH THIS METHOD.
Constraint honourPreferredStartTime(ConstraintFactory factory) {
    return factory.forEach(AgentAssignment.class)
            .filter(a -> a.getAgent() != null)
            .join(AgentPreference.class,
                    equal(a -> a.getAgent().getId(), p -> p.getAgent().getId()),
                    equal(a -> a.getTimeslot().getDate(), AgentPreference::getDate))
            .ifExists(ScheduleConfig.class,
                    filtering((a, p, cfg) -> cfg.schedulingMode() != SchedulingMode.SHIFT))
            .filter((a, p) -> {
                if (p.getPreferredStartTime() == null) return false;
                return a.getTimeslot().getStartTime().isBefore(p.getPreferredStartTime());
            })
            .penalizeConfigurable((a, p) -> 1)
            .asConstraint("Honour preferred start time");
}
```

**Reverted-commit idea to port (not the patch itself)** — `7861b83`'s anchor-style absolute deviation, verified via `git show`:
```java
// Historical reference only — do NOT apply this patch (target method is now mode-gated off).
// The reusable idea: absolute deviation, both directions, round up so no deviation is free.
private int startDeviationIncrements(List<AgentAssignment> assignments, LocalTime preferredStart) {
    long deviationMinutes = Math.abs(ChronoUnit.MINUTES.between(preferredStart, shiftStart));
    return BigDecimal.valueOf(deviationMinutes)
            .divide(BigDecimal.valueOf(increment), 0, RoundingMode.CEILING).intValue();
}
```

**Constraint registration array** (`:75-95`, add both new methods at the end, before/alongside `minimumStaffing`):
```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:75-95 (verified, tail)
            honourPreferredStartTime(factory),
            honourPreferredBreakTime(factory),
            breakClustering(factory),
            // ...
            minimumStaffing(factory),
            // NEW:
            usualShiftConsistency(factory),
            preferredStartShiftMode(factory),
        };
```

**Recommended new constraint shapes (from RESEARCH.md, cross-checked against verified analogs above):**
```java
// Lead with AgentShiftAssignment (Pattern 3 / shiftEnvelopeCompliance's stream-order contract) —
// never lead with AgentPreference or AgentAssignment. Rounding: pick ONE of HALF_UP or CEILING
// already used elsewhere in this file (do not introduce a third) and say which/why in the javadoc.
Constraint usualShiftConsistency(ConstraintFactory factory) {
    return factory.forEachIncludingUnassigned(AgentShiftAssignment.class)
            .join(ScheduleConfig.class)
            .filter((sa, cfg) -> cfg.schedulingMode() == SchedulingMode.SHIFT)
            .join(ResolvedUsualShiftTarget.class,
                    equal((sa, cfg) -> sa.getAgent().getId(), ResolvedUsualShiftTarget::agentId),
                    equal((sa, cfg) -> sa.getDate(), ResolvedUsualShiftTarget::date))
            .join(ConstraintWeights.class)   // per-desk tolerance band, already a problem fact (see Resolved Open Question 2)
            .filter((sa, cfg, target, weights) -> sa.getShiftBandPair() != null
                    && ShiftBandPair.startDeviationMinutes(
                            sa.getShiftBandPair().template().getStartTime(), target.usualStartTime())
                        > weights.getConsistencyToleranceMinutes())
            .penalizeConfigurable((sa, cfg, target, weights) -> {
                int excessMinutes = ShiftBandPair.startDeviationMinutes(
                        sa.getShiftBandPair().template().getStartTime(), target.usualStartTime())
                        - weights.getConsistencyToleranceMinutes();
                return (int) Math.ceil((double) excessMinutes / cfg.incrementMinutes()); // or HALF_UP, pick one existing mode
            })
            .asConstraint("Usual shift consistency");
}
```

---

### `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` (+2 rows)

**Analog:** itself (verified, full header read)

```java
// Source: src/test/java/com/wfm/solver/ScheduleConstraintClassification.java:1-60 (verified)
// ScheduleConstraintClassificationTest derives the constraint set BY REFLECTION from both
// ConstraintWeights's @ConstraintWeight annotations and ScheduleConstraintProvider's
// Constraint-returning builder methods, and asserts this map's key set agrees with both.
// Adding a 23rd/24th constraint fails the build until rows are added HERE.
public enum ModeClassification {
    MODE_AGNOSTIC,
    MODE_GATED,          // <- both new constraints belong here (SHIFT-only, structurally inert on SLOT)
    NEEDS_SHIFT_VARIANT,
    OPEN_RESOLVE_IN_PHASE_15
}
```
Add two `Entry(MODE_GATED, "<basis text>", "")` rows for `"Usual shift consistency"` and the D-08
constraint's exact `asConstraint(...)` name string — the string must match byte-for-byte or the
reflective test fails.

---

### `src/test/java/com/wfm/solver/UsualShiftConsistencyConstraintTest.java` (NEW) / `PreferredStartShiftModeConstraintTest.java` (NEW)

**Analog:** `src/test/java/com/wfm/solver/BandCapacityConstraintTest.java` (verified, full setup section)

```java
// Source: src/test/java/com/wfm/solver/BandCapacityConstraintTest.java:1-50 (verified)
package com.wfm.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
// ... other model imports as needed for fixtures ...
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BandCapacityConstraintTest {
    private final ConstraintVerifier<ScheduleConstraintProvider, Schedule> verifier =
            ConstraintVerifier.build(new ScheduleConstraintProvider(), Schedule.class,
                    AgentAssignment.class, AgentShiftAssignment.class);

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    // ... fixture builder helpers (agent(), template()) ...
}
```

Use `verifier.forConstraint(ScheduleConstraintProvider::usualShiftConsistency)` /
`::preferredStartShiftMode`, `.given(...)`, `.penalizesBy(...)`. Required cases per RESEARCH.md's
Wave-0 gap list: tolerance-band boundary (exactly at band = zero penalty, CONS-02's "genuine dead
zone, not tapered"), per-agent-day charging, `SchedulingMode.SHIFT` gate (silent on SLOT desk),
unassigned-shift null-safety (mirror `shiftEnvelopeCompliance`'s `forEachIncludingUnassigned`
handling), and D-09's "fires independently of stored usual shift" for the preference constraint.

---

### `src/main/java/com/wfm/service/SolverService.java` (+resolveUsualShiftTargets)

**Analog:** `resolvePreferences` (`:567`, verified in full) and its call site (`:225-260`, verified)

```java
// Source: src/main/java/com/wfm/service/SolverService.java:567-610 (verified this session)
private List<AgentPreference> resolvePreferences(List<AgentPreference> allPreferences,
                                                 Schedule schedule,
                                                 Map<UUID, Set<LocalDate>> agentDaysOffMap) {
    Map<UUID, Map<DayOfWeek, AgentPreference>> standingByAgent = new HashMap<>();
    Map<UUID, Map<LocalDate, AgentPreference>> weeklyByAgent = new HashMap<>();
    for (AgentPreference p : allPreferences) {
        UUID agentId = p.getAgent().getId();
        if (p.isStanding()) {
            standingByAgent.computeIfAbsent(agentId, k -> new HashMap<>()).put(p.getDayOfWeek(), p);
        } else if (p.getDate() != null) {
            weeklyByAgent.computeIfAbsent(agentId, k -> new HashMap<>()).put(p.getDate(), p);
        }
    }
    // ... for each agent, for each date in schedule period, skip days off, resolve
    // weekly-overrides-standing precedence, emit date-specific AgentPreference (isStanding=false) ...
}
```

```java
// Call site — Source: src/main/java/com/wfm/service/SolverService.java:225-260 (verified)
List<AgentPreference> allPreferences = agentPreferenceRepository.findByTenantIdAndDeskId(tenantId, deskId);
ConstraintWeights weights = constraintWeightsRepository.findByTenantIdAndDeskId(tenantId, deskId)
        .orElseGet(() -> { ConstraintWeights cw = new ConstraintWeights(); /* ... */ return cw; });
Map<UUID, Set<LocalDate>> agentDaysOffMap = buildAgentDaysOffMap(allDaysOff);
List<AgentPreference> resolvedPreferences = resolvePreferences(allPreferences, schedule, agentDaysOffMap);
runPreSolveValidation(schedule, allAgents, timeslots, staffingRequirements,
        eligibleAgents, allDaysOff, exceptions, agentDayHours, resolvedPreferences);
```

New method, same file, same shape — insert its call in the same pre-solve block, right after
`resolvedPreferences` is built:
```java
private List<ResolvedUsualShiftTarget> resolveUsualShiftTargets(
        List<AgentUsualShift> allUsualShifts, Schedule schedule,
        Map<UUID, Set<LocalDate>> agentDaysOffMap, List<AgentDayConfig> agentDayConfigs) {
    // Index AgentUsualShift by (agentId, dayOfWeek).
    // For each AgentDayConfig with a working day (effectiveHours > 0 — mirrors
    // AgentShiftAssignment's own creation gate; non-working days emit no target, USHF-04/D-02's
    // "no penalty" case falls out with zero special-casing):
    //   usualShiftResolutionService.resolve(stored, date) -> Optional<ShiftTemplate>
    //   if present: emit ResolvedUsualShiftTarget(agentId, date, template.getStartTime())
    // NEVER duplicate UsualShiftResolutionService's era logic here — call it, don't reimplement it
    // (its own javadoc: "Do not create a second copy of this method").
    // XCUT-02: this method is read-only against AgentUsualShiftRepository — no .save(...) call,
    // ever. Prove this with a structural test (see ushf-05-write-paths.md discipline).
}
```
Then wire onto `Schedule` the same way `resolvedPreferences` presumably feeds the constraint
provider (check how `resolvedPreferences` is attached to `schedule` before solving — likely
`schedule.setAgentPreferences(resolvedPreferences)` or similar nearby; mirror that exact call for
`schedule.setResolvedUsualShiftTargets(...)`).

---

### `src/main/java/com/wfm/service/ConstraintWeightsService.java` (+D-07/D-08 save-time validation)

**Analog:** itself — partial-update merge shape (verified, full `updateWeights` header)

```java
// Source: src/main/java/com/wfm/service/ConstraintWeightsService.java:35-75 (verified)
@Transactional
public ConstraintWeightsDto updateWeights(UUID deskId, ConstraintWeightsDto updates) {
    long tenantId = TenantContext.getTenantId();
    ConstraintWeights weights = constraintWeightsRepository
            .findByTenantIdAndDeskId(tenantId, deskId)
            .orElseThrow(() -> new EntityNotFoundException("ConstraintWeights not found for desk " + deskId));

    // Partial update: only non-null fields in the DTO are applied
    if (updates.getUnassignedAssignmentWeight() != null) {
        weights.setUnassignedAssignmentWeight(toScore(updates.getUnassignedAssignmentWeight()));
    }
    // ... one if-block per field ...
}
```

**Pitfall 5 (RESEARCH.md), load-bearing:** validate against the **merged** `weights` entity, not
the raw `updates` DTO, and validate **after** all `if (x != null)` blocks have applied, **before**
`constraintWeightsRepository.save(weights)`:
```java
if (updates.getConsistentStartWeight() != null) {
    weights.setConsistentStartWeight(toScore(updates.getConsistentStartWeight()));
}
if (updates.getConsistencyToleranceMinutes() != null) {
    weights.setConsistencyToleranceMinutes(updates.getConsistencyToleranceMinutes());
}
if (updates.getPreferredStartShiftModeWeight() != null) {
    weights.setPreferredStartShiftModeWeight(toScore(updates.getPreferredStartShiftModeWeight()));
}

// D-07: reject, don't clamp — mirrors DeskAgentService.setDayHours's inclusive-bound pattern.
if (weights.getConsistentStartWeight().hardScore() != 0) {
    throw new IllegalArgumentException(
        "Usual Shift Consistency's hard score must be 0 — a hard score would risk making an "
        + "otherwise-feasible schedule infeasible.");
}
// D-08: precedence ordering, checked against the merged result.
if (weights.getPreferredStartShiftModeWeight().softScore() >= weights.getConsistentStartWeight().softScore()) {
    throw new IllegalArgumentException(
        "Preferred Start (Shift Mode) weight must be lower than Usual Shift Consistency's weight.");
}

constraintWeightsRepository.save(weights);
```

**Analog for the reject-don't-clamp shape itself:** `DeskAgentService.setDayHours` (`:432-437`, verified):
```java
// Source: src/main/java/com/wfm/service/DeskAgentService.java:432-437 (verified)
if (normalized != null
        && (normalized.signum() < 0 || normalized.compareTo(new BigDecimal("24")) > 0)) {
    throw new IllegalArgumentException("Contracted hours per day must be between 0 and 24");
}
```

**Error mapping (unchanged, reused as-is):** `src/main/java/com/wfm/controller/GlobalExceptionHandler.java:31-34` (verified, full):
```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), List.of());
}
```

---

### `src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java` (NEW — no existing file)

No direct analog exists (confirmed, Resolved Open Question 1). Build a standard Mockito-based
service unit test: mock `ConstraintWeightsRepository`, construct a `ConstraintWeightsService`
directly, and assert (a) D-07's hard!=0 rejection throws `IllegalArgumentException` with the exact
message, tested against the **merged** entity (a partial update touching only the tolerance field
must not spuriously trip the hard-score check unless the *merged* hard score is nonzero), and (b)
D-08's ordering rejection, same merged-result requirement. Mirror `ConstraintWeightsController`'s
existing tenant-scoping call pattern if a controller-level `@WebMvcTest` is also added.

---

### `src/main/java/com/wfm/service/ScheduleOutputService.java` (+buildDriftReport)

**Analog:** `buildPreferenceReport` (`:289-412`, verified in full) plus its shared helper `buildShiftDescriptorsByAgentDate`

```java
// Source: src/main/java/com/wfm/service/ScheduleOutputService.java:288-296 (verified)
public PreferenceReport buildPreferenceReport(Schedule schedule) {
    Map<UUID, Map<LocalDate, ShiftDescriptor>> shiftDescriptorsByAgentDate =
            buildShiftDescriptorsByAgentDate(schedule);   // reuse this exact helper
    // ... group AgentAssignment by (agentId, date) ...
}
```

```java
// Source: src/main/java/com/wfm/service/ScheduleOutputService.java:395-412 (verified, tail)
entries.sort(Comparator.comparing(PreferenceReportEntry::agentName)
        .thenComparing(PreferenceReportEntry::date));
// ... summary computation with BigDecimal.divide(..., RoundingMode.HALF_UP) ...
return new PreferenceReport(entries, summary);
```

New method, iterate `schedule.getShiftAssignments()` (`AgentShiftAssignment`) instead of
`schedule.getAgentPreferences()`; resolve each row's target via
`UsualShiftResolutionService.resolve(...)` (the sole implementation — its own javadoc names this
phase's report as an intended caller); classify via `ShiftBandPair.startDeviationMinutes(...)`
against `schedule.getConstraintWeights().getConsistencyToleranceMinutes()`. Sort **date ascending,
then agent name ascending** per UI-SPEC's server-order expectation (not alphabetical-by-agent like
`PreferenceReport` — a deliberate difference from the analog, state it in a comment). Also compute
the D-13 popularity ranking by reading `AgentUsualShift` directly (not solve results) — a second,
independent read inside the same method, distinct from the per-entry loop.

---

### `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` (+DriftReport records)

**Analog:** `PreferenceReport`/`PreferenceReportEntry`/`PreferenceSummary` (`:115-135`, verified in full)

```java
// Source: src/main/java/com/wfm/dto/ScheduleDetailResponse.java:115-135 (verified)
public record PreferenceReport(
        List<PreferenceReportEntry> entries,
        PreferenceSummary summary
) {}

public record PreferenceReportEntry(
        UUID agentId,
        String agentName,
        LocalDate date,
        String preferenceSource,
        LocalTime preferredStartTime,
        LocalTime actualStartTime,
        boolean startTimeHonoured,
        LocalTime preferredBreakTime,
        LocalTime actualBreakTime,
        boolean breakTimeHonoured
) {}

public record PreferenceSummary(
        int totalPreferences,
        int startTimeHonouredCount,
        // ...
) {}
```

New records, matching UI-SPEC's exact field contract (D-12's three-state field, not a null-inference):
```java
public record DriftReport(
        List<DriftReportEntry> entries,
        DriftSummary summary,
        List<ShiftPopularityEntry> popularity
) {}

public record DriftReportEntry(
        UUID agentId,
        String agentName,
        LocalDate date,
        DriftStatus status,                 // enum: NO_USUAL_SHIFT, HONOURED, DRIFTED
        LocalTime usualStartTime,            // null iff status == NO_USUAL_SHIFT
        LocalTime actualStartTime,           // always present for a working agent-day
        Integer deltaMinutes                 // populated ONLY when status == DRIFTED
) {}

public record DriftSummary(
        int workingAgentDays,
        int noUsualShiftCount,
        int honouredCount,
        int driftedCount
) {}

public record ShiftPopularityEntry(String templateName, int agentCount) {}

public enum DriftStatus { NO_USUAL_SHIFT, HONOURED, DRIFTED }
```

---

### `src/main/java/com/wfm/service/ScheduleService.java` (+wire+date-filter driftReport)

**Analog:** itself — `:200-228`'s existing `preferenceReport` date filter (verified in full)

```java
// Source: src/main/java/com/wfm/service/ScheduleService.java:200-228 (verified)
if (dateFilter != null && !dateFilter.isBlank()) {
    LocalDate filterDate;
    try {
        filterDate = LocalDate.parse(dateFilter);
    } catch (DateTimeParseException e) {
        throw new IllegalArgumentException("Invalid date format: " + dateFilter);
    }
    // ... staffingSummary, agentSchedule filters ...
    if (response.getPreferenceReport() != null) {
        var filteredEntries = response.getPreferenceReport().entries().stream()
                .filter(e -> e.date().equals(filterDate)).toList();
        response.setPreferenceReport(new ScheduleDetailResponse.PreferenceReport(
                filteredEntries, response.getPreferenceReport().summary()));
    }
}
```

Copy this exact `if (response.getDriftReport() != null) { ... }` block, filtering `entries` by
`filterDate` and preserving `summary`/`popularity` unfiltered (summary should reflect the
date-filtered entry set per UI-SPEC — recompute, don't just carry through the whole-schedule
summary object verbatim, unlike the `PreferenceReport` analog which does carry `summary()`
through unfiltered — **this is a deliberate divergence from the copied pattern, state it in a
code comment**, since UI-SPEC explicitly requires the summary bar counts to "always agree with
what the operator can see on screen").

---

### `src/main/java/com/wfm/service/ScheduleExportService.java` (+writeDriftReport)

**Analog:** `writePreferenceReport` (`:172-215`, verified in full)

```java
// Source: src/main/java/com/wfm/service/ScheduleExportService.java:172-215 (verified)
private void writePreferenceReport(XSSFWorkbook workbook, CellStyle headerStyle,
                                    PreferenceReport report) {
    Sheet sheet = workbook.createSheet("Preference Report");
    Row header = sheet.createRow(0);
    String[] cols = {"Agent", "Date", "Source", "Preferred Start", "Actual Start",
                     "Start Honoured", "Preferred Break", "Actual Break", "Break Honoured"};
    for (int i = 0; i < cols.length; i++) {
        Cell cell = header.createCell(i);
        cell.setCellValue(cols[i]);
        cell.setCellStyle(headerStyle);
    }
    if (report == null || report.entries() == null) return;   // guard-then-header-only pattern
    int rowNum = 1;
    for (PreferenceReportEntry e : report.entries()) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(e.agentName());
        row.createCell(1).setCellValue(e.date().toString());
        // ... one createCell per column ...
    }
    autoSizeColumns(sheet, cols.length);
}
```

New method, same shape, sheet name `"Drift Report"`. **Headers must be byte-identical to the UI
table per D-14/UI-SPEC:** `"Agent", "Date", "Status", "Usual Start", "Actual Start", "Delta (min)"`.
Status column writes the enum's UI label string (`"No usual shift"` / `"Honoured"` / `"Drifted"`),
not the raw enum name — mirror `writePreferenceReport`'s `e.startTimeHonoured() ? "Yes" : "No"`
label-mapping precedent. Register the call alongside the existing `writePreferenceReport(...)` call
site (`:28`).

---

### `frontend/src/pages/ScheduleResults.tsx` (+Drift Report tab)

**Analog:** `PreferenceTab` (`:989-1040`, verified in full) and the tab bar (`:254-269`, verified)

```tsx
// Source: frontend/src/pages/ScheduleResults.tsx:989-1000 (verified)
function PreferenceTab({ schedule, dateFilter }: { schedule: ScheduleDetail; dateFilter: string }) {
  const report = schedule.preferenceReport
  if (!report || !report.entries || report.entries.length === 0) {
    return <p style={{ color: '#6b7280' }}>No preference report data available.</p>
  }
  const entries = dateFilter ? report.entries.filter(e => e.date === dateFilter) : report.entries
  return (
    <>
      {report.summary && (
        <div style={{ display: 'flex', gap: '1.5rem', marginBottom: '1rem', fontSize: '0.85rem',
          background: '#f9fafb', padding: '0.75rem', borderRadius: '6px' }}>
          <div>Total preferences: <strong>{report.summary.totalPreferences}</strong></div>
          {/* ... */}
        </div>
      )}
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
        {/* header row, body rows */}
      </table>
    </>
  )
}
```

```tsx
// Source: frontend/src/pages/ScheduleResults.tsx:254-269 (verified)
{(['staffing', 'agents', 'allocation', 'preferences', 'violations', 'pto'] as const).map(tab => (
  <button key={tab} onClick={() => setActiveTab(tab)}
    style={{ background: activeTab === tab ? '#3b82f6' : '#e5e7eb', ... }}>
    {tab === 'staffing' ? 'Staffing Summary' : /* ... */ : 'PTO'}
  </button>
))}
{/* ... */}
{activeTab === 'preferences' && <PreferenceTab schedule={schedule} dateFilter={dateFilter} />}
```

New `DriftTab({ schedule, dateFilter })` follows this shape exactly, plus:
- Top-level branch on `schedule.schedulingMode !== 'SHIFT'` (per UI-SPEC, locked design decision — mirror `AgentAllocationTab`'s existing branch-at-the-top discipline, not copied verbatim here but referenced by UI-SPEC).
- Status column color/weight per UI-SPEC §1 table (`#16a34a`/`#dc2626`/`#d1d5db`), reusing `PreferenceTab`'s exact `startTimeHonoured ? '#16a34a' : '#dc2626'` conditional shape (`ScheduleResults.tsx:1030`) extended to a third branch.
- Add `'drift'` to the `activeTab` union (~`:16`), a new tab button between `'preferences'` and `'violations'` in both the array literal and label ternary, and `{activeTab === 'drift' && <DriftTab schedule={schedule} dateFilter={dateFilter} />}` alongside the existing branches.

---

### `frontend/src/pages/ConstraintWeightsPage.tsx` (+3 rows, 1 non-Score)

**Analog:** itself — `CONSTRAINTS`/`DEFAULTS` arrays and row-render loop (verified, full file read this session, 120 lines)

```tsx
// Source: frontend/src/pages/ConstraintWeightsPage.tsx:6-24 (verified)
const CONSTRAINTS: Array<{ key: string; label: string; description: string }> = [
  { key: 'unassignedAssignmentWeight', label: 'Unassigned Assignment', description: '...' },
  // ... 21 more rows ...
  { key: 'minStaffingWeight', label: 'Minimum Staffing', description: '...' },
]

const DEFAULTS: Record<string, Score> = {
  unassignedAssignmentWeight: { hardScore: 0, softScore: 1000 },
  // ... 21 more entries ...
}
```

```tsx
// Source: frontend/src/pages/ConstraintWeightsPage.tsx:99-113 (verified) — the cast that BREAKS
// on a non-Score field (Pitfall 3)
{CONSTRAINTS.map(({ key, label, description }) => {
  const score = (weights as Record<string, Score>)[key] || DEFAULTS[key]
  const level = score.hardScore > 0 ? 'Hard' : 'Soft'
  return (
    <tr key={key}>
      <td style={{ fontWeight: 500 }}>{label}</td>
      <td style={{ fontSize: '0.8rem', color: '#6b7280' }}>{description}</td>
      <td><span style={{ padding: '0.15rem 0.5rem', borderRadius: '4px', fontSize: '0.75rem',
        fontWeight: 600, background: level === 'Hard' ? '#fef2f2' : '#f0fdf4',
        color: level === 'Hard' ? '#dc2626' : '#16a34a' }}>{level}</span></td>
      <td><input type="number" value={score.hardScore}
        onChange={e => setWeights({ ...weights, [key]: { ...score, hardScore: Number(e.target.value) } })}
        style={{ width: '70px' }} /></td>
      <td><input type="number" value={score.softScore}
        onChange={e => setWeights({ ...weights, [key]: { ...score, softScore: Number(e.target.value) } })}
        style={{ width: '70px' }} /></td>
    </tr>
  )
})}
```

**Add three rows to `CONSTRAINTS`**, placed consecutively, immediately before `minStaffingWeight`
per UI-SPEC §3: `consistentStartWeight`, then the tolerance-band row (own render branch, NOT
iterated by the `Record<string, Score>` cast above), then the new preference weight. The two
`Score` rows (`consistentStartWeight`, D-08's weight) render through the existing `.map(...)` loop
unchanged. **The tolerance-band row needs a separate, hand-written `<tr>`** outside the
`CONSTRAINTS.map(...)` loop (or a conditional branch inside it keyed on the row's kind) reading a
new top-level state field `consistencyToleranceMinutes: number`, rendering a `Minutes` badge
(`background: '#e5e7eb', color: '#374151'`) instead of Hard/Soft, and a single `<td colSpan={2}>`
cell with one `<input type="number" min="0">` + `" min"` suffix — per UI-SPEC Component
Specifications §3's table.

`DEFAULTS` gains three matching entries (two `Score`, D-06-benchmark-derived; one plain number,
planner's discretion) — `handleReset`'s existing `setWeights({ ...DEFAULTS })` needs no code
change since it already spreads the whole map.

Add the D-10 precedence note as a full-width `<tr><td colSpan={5}>` row directly beneath the third
new row, styled `background: '#f9fafb', padding: '0.75rem', borderRadius: '6px', fontSize:
'0.85rem'` (matches the informational-box token already used elsewhere on this page's siblings).

---

### `frontend/src/api/client.ts` (+DriftReport* interfaces)

**Analog:** existing `PreferenceReport`-family TS interfaces already in this file (mirror their
shape 1:1 with the `DriftReportEntry`/`DriftSummary`/`ShiftPopularityEntry`/`DriftReport`
interfaces specified verbatim in `17-UI-SPEC.md`'s Component Specifications §1 — copy those
interface bodies directly, they are already the approved contract). Add `driftReport:
DriftReport | null` to `ScheduleDetail`, and `consistentStartWeight: Score`,
`consistencyToleranceMinutes: number`, `<newPrefWeightKey>: Score` to `ConstraintWeightsData`.

## Shared Patterns

### One computation, multiple callers (DRFT-03)
**Source:** `src/main/java/com/wfm/model/ShiftBandPair.java`'s `covers(...)` static-overload precedent.
**Apply to:** `ShiftBandPair.startDeviationMinutes(...)` — the single distance function called by
`usualShiftConsistency` (constraint) and `buildDriftReport` (report). No second implementation
anywhere, including the frontend (the React component renders `status`/`deltaMinutes` as given,
never recomputes them — UI-SPEC's explicit instruction).

### Pre-solve resolution into a dated problem-fact record
**Source:** `SolverService.resolvePreferences` (`SolverService.java:567`).
**Apply to:** `SolverService.resolveUsualShiftTargets`, producing `List<ResolvedUsualShiftTarget>`
fed to `Schedule` as a new `@ProblemFactCollectionProperty`. Never feed raw `AgentUsualShift` to the
constraint stream directly (it has no date, no era resolution).

### Reject, don't clamp
**Source:** `DeskAgentService.setDayHours` (`:432-437`).
**Apply to:** `ConstraintWeightsService.updateWeights`'s new D-07 (hard-must-be-0) and D-08
(precedence-ordering) checks — `IllegalArgumentException`, checked against the **merged** entity,
mapped to 400 `VALIDATION_FAILED` by `GlobalExceptionHandler.handleIllegalArgument` (`:31-34`).

### Structural / reflection completeness guard
**Source:** `ScheduleConstraintClassification.java` + `ScheduleConstraintClassificationTest`.
**Apply to:** the build fails until both new constraints get a `MODE_GATED` row — not optional,
not deferrable to a later plan wave.

### Derived-on-read reporting (no new table)
**Source:** `ScheduleOutputService.buildPreferenceReport` / `ScheduleDetailResponse.PreferenceReport`
/ `ScheduleService`'s date-filter block / `ScheduleExportService.writePreferenceReport`.
**Apply to:** the whole drift-report chain (`buildDriftReport`, `DriftReport` DTO records,
`ScheduleService`'s filter block, `writeDriftReport`). No migration, no snapshot table — resolved
fresh from `UsualShiftResolutionService` at read time, exactly like preference resolution.

### Stream-ordering performance contract (SHIFT-mode-only constraints)
**Source:** `shiftEnvelopeCompliance`'s javadoc (`ScheduleConstraintProvider.java:390-420`).
**Apply to:** both `usualShiftConsistency` and `preferredStartShiftMode` — lead with
`AgentShiftAssignment`/`ResolvedUsualShiftTarget`/`AgentPreference` joined onto it, gate on
`SchedulingMode.SHIFT` before touching anything else. Never lead with a bare `AgentAssignment`
stream — measured ~3x throughput cost for getting this backwards.

## No Analog Found

None — every file in this phase's scope has a strong, same-codebase analog (see table above).
`ConstraintWeightsServiceTest` is the only file with no direct sibling test to copy from (see
Resolved Open Question 1); it still has clear role-model analogs (`ConstraintWeightsService`
itself, `DeskAgentService.setDayHours`'s validation shape) to build the test's assertions against.

## Metadata

**Analog search scope:** `src/main/java/com/wfm/{model,service,solver,dto,controller}/`,
`src/test/java/com/wfm/{solver,service,controller}/`, `src/main/resources/db/migration/`,
`frontend/src/{pages,api}/`.
**Files read directly this session (not taken on trust from RESEARCH.md/CONTEXT.md):**
`ConstraintWeights.java` (header + first fields), `ConstraintWeightsService.java` (full merge
block), `GlobalExceptionHandler.java` (full), `ScheduleConstraintProvider.java` (constraint array,
`shiftEnvelopeCompliance` full javadoc+method, `honourPreferredStartTime` full javadoc+method),
`ScheduleConstraintClassification.java` (header/enum), `BandCapacityConstraintTest.java` (header),
`SolverService.java` (`resolvePreferences` full method, pre-solve call site), `DeskAgentService.java`
(`setDayHours` validation block), `ShiftBandPair.java` (record header, `covers` grep), `Schedule.java`
(`@ProblemFactCollectionProperty`/`@ConstraintConfigurationProvider` grep), `ScheduleConfig.java`
(full file), `ScheduleOutputService.java` (`buildPreferenceReport` full method),
`ScheduleDetailResponse.java` (`PreferenceReport`-family records), `ScheduleService.java`
(date-filter block), `ScheduleExportService.java` (`writePreferenceReport` full method),
`ConstraintWeightsPage.tsx` (full file, 120 lines), `ScheduleResults.tsx` (`PreferenceTab`, tab bar).
**Migration head confirmed:** V47 on disk (`ls db/migration | grep -oE '^V[0-9]+' | sort -n | tail -1` → 47) — **V48 is correct**.
**Pattern extraction date:** 2026-09-04
