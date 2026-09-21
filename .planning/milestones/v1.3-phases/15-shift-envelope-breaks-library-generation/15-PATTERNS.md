# Phase 15: Shift Envelope, Breaks & Library Generation - Pattern Map

**Mapped:** 2026-08-26
**Files analyzed:** ~24 (new + modified, across backend model/service/solver, migration, and frontend)
**Analogs found:** 18 / 24 (several with only partial/no analog — flagged explicitly below)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/resources/db/migration/V40__shift_template_break_bands.sql` | migration | batch (data-migrating DDL) | `V39__add_shift_template_and_scheduling_mode.sql` (structural shape) + **no analog** for the drop-after-migrate step (see below) | partial |
| `src/main/java/com/wfm/model/ShiftTemplateBreakBand.java` | model | CRUD | `src/main/java/com/wfm/model/AgentDayHours.java` (flat FK-scoped child row, not a collection) | role-match, not exact |
| `src/main/java/com/wfm/model/ShiftTemplate.java` (modified) | model | CRUD | itself (Phase 14 version) — no in-repo `@OneToMany` collection precedent exists anywhere | **no analog for the collection mapping itself** |
| `src/main/java/com/wfm/model/AgentShiftAssignment.java` | model + solver | CRUD + planning-entity | `src/main/java/com/wfm/model/AgentAssignment.java` | exact |
| `src/main/java/com/wfm/model/ShiftBandPair.java` (problem-fact record) | model | transform | `src/main/java/com/wfm/model/AgentDayConfig.java`, `TimeslotDemandConfig.java`, `ScheduleConfig.java` | exact |
| `src/main/java/com/wfm/model/ScheduleConfig.java` (modified, +schedulingMode field) | model | transform | itself — single-call-site record, see Schedule.java:255 | exact |
| `src/main/java/com/wfm/model/ConstraintWeights.java` (modified, +shiftEnvelopeComplianceWeight) | config | CRUD | itself — `breakClusteringWeight` field/column pattern | exact |
| `src/main/java/com/wfm/service/ShiftTemplateService.java` (modified) | service | CRUD | itself (Phase 14 version) | exact |
| `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (modified — `covers()` generalised) | service | request-response | itself | exact |
| `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` (NEW, SHLB-07) | service | batch/transform | `ShiftLibraryValidationService.java` (shape + `covers()` reuse + `ErrorDetail` contract) | role-match, strong |
| `src/main/java/com/wfm/controller/ShiftTemplateController.java` (modified) + generation endpoint | controller | request-response | itself (Phase 14 version); generation endpoint analog is the existing `POST .../shift-library/validate`-style read-only endpoint | exact |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — `shiftEnvelopeCompliance` (NEW) | solver constraint | event-driven (constraint stream) | two-stream positive-join shape: `bulkUnderallocationHard` (line 423) or `exactlyOneBreak`'s `AgentDayConfig` join (line 188) | exact |
| `ScheduleConstraintProvider.java` — `breakClustering` real body (line 573) | solver constraint | event-driven, cross-agent aggregation | `bulkUnderallocationHard` (423) / `minimumStaffing`'s `groupBy(timeslot,...)` shape | role-match, strong |
| `ScheduleConstraintProvider.java` — mode-gate 4 break constraints + 2 preference constraints | solver constraint | event-driven | `minimumStaffing`'s documented "config-driven, not code-driven" precedent (javadoc ~456-461); literal top-of-stream filter template already sketched against `exactlyOneBreak` (188) | exact |
| `src/main/resources/solverConfig.xml` (modified) | config | n/a | itself (Phase-12-era two-`<entityClass>` two-phase-CH XML, reverted at `299c42c` but recoverable) | role-match (own history, not currently live) |
| `src/main/java/com/wfm/service/SolverService.java` — `buildShiftAssignments` (NEW) + populate | service | batch/transform | `computeAgentDayConfigs` (line 559) | exact |
| `src/main/java/com/wfm/service/ScheduleService.java` — `acceptSchedule` snapshot extension | service | CRUD (snapshot/remap) | itself — existing `StaffingRequirement`/`AgentAssignment` snapshot-and-remap block (lines 255-294) | exact |
| `shift_envelope_compliance_weight` column migration | migration | batch | `V37__add_min_staffing_weight.sql` / `V38__add_consistent_start_weight.sql` | exact |
| `frontend/src/pages/ShiftLibrary.tsx` — break band editor | component | CRUD (form) | itself (Phase 14 single-break-field form) | exact |
| `frontend/src/pages/ShiftLibrary.tsx` — Suggested Library draft panel | component | request-response | `CoveragePanel` (line 98) + the existing `err.details`-driven refusal rendering (203-366) | exact |
| `frontend/src/pages/ScheduleResults.tsx` — `AgentAllocationTab` shift grouping (~285) | component | transform (client-side grouping) | itself (existing per-date rendering block) | exact |
| `frontend/src/api/client.ts` — new SHLB-07 + band DTOs, `ScheduleDetail.schedulingMode`, `AgentScheduleEntry.shift` | utility (types) | transform | itself — `ShiftTemplate`/`ShiftTemplateBody` (317-318), `AgentScheduleEntry` (362-371) | exact |
| `src/test/java/com/wfm/solver/SolverConfigBuildTest.java` (NEW, XCUT-03) | test | n/a | **no analog** — no existing test under `solver/` builds a `SolverFactory` from XML | none |
| `src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java` (NEW) | test | n/a | `MinimumStaffingConstraintTest.java`, `BulkUnderallocationSoftConstraintTest.java` | exact |
| `src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java` (NEW, ENVL-07) | test | n/a | **no analog** — pattern only exists in `SPIKE-COUPLING.md`'s throwaway `SpikeMain.java`, not in `src/test` | partial (research-doc only) |
| `src/test/java/com/wfm/solver/ShiftModelBenchmarkTest.java` (NEW, XCUT-04) | test | batch/benchmark | `AtomicShiftMoveBenchmarkTest.java` — **deleted** at commit `299c42c`, recoverable at `299c42c^` | partial (recoverable via git, not live) |

## Pattern Assignments

### `src/main/java/com/wfm/model/AgentShiftAssignment.java` (model, dual @Entity+@PlanningEntity)

**Analog:** `src/main/java/com/wfm/model/AgentAssignment.java` (verbatim, full file read this session — 66 lines)

**Full shape to mirror:**
```java
@PlanningEntity(difficultyComparatorClass = AgentAssignmentDifficultyComparator.class)  // consider an analogous comparator, or omit
@Entity
@Table(name = "agent_assignment")
public class AgentAssignment {

    @PlanningId
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeslot_id", nullable = false)
    private Timeslot timeslot;

    @PlanningVariable(valueRangeProviderRefs = "agentRange", nullable = true)   // AgentShiftAssignment uses allowsUnassigned() instead — D-06
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    public AgentAssignment() {}
    // plain getters/setters, no Lombok anywhere in this codebase
}
```

**Deviations required by D-04/D-05/D-06 (do not carry forward verbatim):**
- `@PlanningVariable(valueRangeProviderRefs = "shiftBandRange", allowsUnassigned = true)` — NOT `nullable = true` (deprecated form `AgentAssignment` still uses; D-06 explicitly rejects copying this one line).
- Identity is `(agent, date)` not `(timeslot)` — the FK is `agent_id` + a plain `date` column, no `@ManyToOne` to a per-slot entity.
- The planning-variable field type is `ShiftBandPair` (a `@Transient` problem-fact-typed field, not `@ManyToOne` to a persisted entity) — persistence of the *resolved* choice happens only via denormalised scalar columns (template name, start, end, band offset/duration, nullable `source_template_id`) per D-07, not via an FK to the pair itself.
- No `difficultyComparatorClass` proven necessary — sketch it without one unless CH benchmarking (D-08) shows a need.

### `src/main/java/com/wfm/model/ShiftBandPair.java` (problem-fact record)

**Analog:** `src/main/java/com/wfm/model/ScheduleConfig.java` (full file, 22 lines) and `AgentDayConfig`/`TimeslotDemandConfig` (record declarations only, not fully read this session, but same `record` idiom confirmed at `AgentDayConfig.java:13`, `TimeslotDemandConfig.java:9`).

**Pattern:**
```java
/** Immutable problem fact — a live (template, band) pair the solver may choose for one agent-day. */
public record ShiftBandPair(ShiftTemplate template, ShiftTemplateBreakBand band) {
    public boolean covers(Timeslot ts) { /* delegates to a generalised ShiftLibraryValidationService.covers()-equivalent predicate */ }
}
```
Every existing `@ProblemFactProperty`/value-range-supplying fact in this codebase is a plain immutable `record`, never a mutable class — follow that exactly.

### `src/main/java/com/wfm/model/ShiftTemplateBreakBand.java` (model, child table)

**Analog (partial — role-match only):** `src/main/java/com/wfm/model/AgentDayHours.java` (full file, 64 lines) — closest existing shape of "a child row FK'd to a parent, tenant-scoped, no collection mapping on the parent side."

**Imports/shape to copy:**
```java
@Entity
@Table(name = "shift_template_break_band")
public class ShiftTemplateBreakBand {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_template_id", nullable = false)
    private ShiftTemplate shiftTemplate;

    @Column(name = "offset_minutes", nullable = false)
    private int offsetMinutes;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "capacity")   // nullable — D-03: blank = unlimited
    private Integer capacity;
    // plain getters/setters
}
```

**IMPORTANT — no analog exists for a parent-side `@OneToMany` collection.** Confirmed by
`grep -rl "OneToMany" src/main/java/com/wfm/model/` returning **zero files**. Every existing
parent/child relationship in this codebase (`AgentDayHours`→`Agent`, `AgentAssignment`→`Timeslot`,
`StaffingRequirement`→`Timeslot`) is expressed as a **child-owns-the-FK, repository-queried-by-parent-id**
shape — never a mapped `@OneToMany` collection on the parent entity. **Recommendation: do not introduce
the codebase's first `@OneToMany`.** Instead give `ShiftTemplateBreakBand` a `shiftTemplateId` FK exactly
like `AgentDayHours.agent`, add a `ShiftTemplateBreakBandRepository.findByTenantIdAndShiftTemplateId(...)`
following every existing repository's naming convention, and have `ShiftTemplateService`/`ShiftLibraryValidationService`
load bands via that repository call rather than `shiftTemplate.getBands()`. This is a genuine architectural
choice point the planner must decide explicitly — it deviates from the "just find the nearest OneToMany" instinct
because there is no such precedent to follow, and introducing one here would be a new pattern this phase
would then own explaining.

### Migration: `V40__shift_template_break_bands.sql`

**Analog for the additive/structural part:** `V39__add_shift_template_and_scheduling_mode.sql` (full file, 55 lines) — copy its comment-block discipline (explain WHY, cite the audit/decision ID, explain portability choices) verbatim in style.

**Analog for weight-column addition:** `V37__add_min_staffing_weight.sql` / `V38__add_consistent_start_weight.sql` (both full files, read this session) — the `ALTER TABLE constraint_weights ADD COLUMN ... NOT NULL DEFAULT '...'` shape for `shift_envelope_compliance_weight`.

**NO ANALOG for the "migrate every row into a child table, then drop the source columns" combination.**
Confirmed: every existing `DROP COLUMN` migration (`V15`, `V14`, `V3`, `V8`, `V18`, `V2`) drops columns that
are either newly-redundant duplicates or being replaced by a *different existing* column — none of them
first fan a single row's data out into N new child-table rows before dropping the source columns. This is
the phase's most novel migration shape. Recommend structuring it as three explicit statements in one
migration file (Postgres/H2-portable, no PL/pgSQL procedural block — this codebase's migrations are pure
DDL/DML, confirmed by scanning all of V2-V39):
```sql
CREATE TABLE shift_template_break_band ( ... );

INSERT INTO shift_template_break_band (id, tenant_id, shift_template_id, offset_minutes, duration_minutes, capacity)
SELECT gen_random_uuid(), tenant_id, id, break_offset_minutes, break_duration_minutes, NULL
FROM shift_template
WHERE break_duration_minutes > 0;   -- D-15 discretion: zero-duration templates get ZERO bands, not a zero-duration band (preserves "zero bands = no break")

ALTER TABLE shift_template DROP COLUMN break_offset_minutes;
ALTER TABLE shift_template DROP COLUMN break_duration_minutes;
```
Flag `gen_random_uuid()` for portability check against H2 test config (`application-test.yml` uses
`ddl-auto: create-drop`, so this exact SQL never runs under test per the recorded G-14-1 blind spot —
the planner's discretion item on closing that blind spot becomes directly relevant to catching a bug here).

### `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` (NEW, SHLB-07)

**Analog:** `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (full file, 288 lines, read this session).

**Reuse verbatim, don't reimplement (D-08 discipline, per Research §"Don't Hand-Roll"):**
- `covers(ShiftTemplate, Window)` at line 161 — must call the (D-02-generalised) any-band version, not a new predicate.
- `anyHoursMatch(List<BigDecimal>, BigDecimal)` at line 267 — the exact `BigDecimals.normalize().compareTo()` pattern for contracted-hours filtering (D-10).
- The `ErrorDetail`/`PreSolveValidationException` response contract from `requireShiftModeReady` (lines 94-116) — D-12's partial-coverage response must emit the same `ErrorDetail("coverage", window, null)` shape.
- Constructor-injection style: plain `@Service`, fields `final`, no Lombok, `TenantContext.getTenantId()` at the top of every public method (line 67) — never a caller-supplied tenant.

### `ScheduleConstraintProvider.java` — `shiftEnvelopeCompliance` (NEW hard constraint)

**Analog:** `bulkUnderallocationHard` (lines 423-440, full body read this session) for the two-stream-join-then-filter-then-penalizeConfigurable shape; `exactlyOneBreak` (188-231) for the `AgentDayConfig`-join pattern this constraint will need to resolve the agent's chosen `ShiftBandPair`.

**Copy this shape** (positive-join form per Discretion recommendation, NOT `ifNotExists`):
```java
private Constraint shiftEnvelopeCompliance(ConstraintFactory factory) {
    return factory.forEach(AgentAssignment.class)
            .filter(a -> a.getAgent() != null)
            .join(AgentShiftAssignment.class,
                    equal(a -> a.getAgent().getId(), sa -> sa.getAgent().getId()),
                    equal(a -> a.getTimeslot().getDate(), AgentShiftAssignment::getDate))
            .filter((a, sa) -> sa.getShiftBandPair() == null
                    || !sa.getShiftBandPair().covers(a.getTimeslot()))
            .penalize(HardSoftScore.ONE_HARD)   // or penalizeConfigurable via the new weight column
            .asConstraint("Shift envelope compliance");
}
```

### `ScheduleConstraintProvider.java` — mode-gating (4 break + 2 preference constraints)

**Analog:** `minimumStaffing`'s own javadoc (~456-461, read this session) as the *documented precedent* for
"hard-vs-soft is a config row, not a code decision"; the literal mode filter shape is sketched in RESEARCH.md
§Pattern 3 against `exactlyOneBreak` (188) — add `.join(ScheduleConfig.class).filter((..., cfg) -> cfg.schedulingMode() == SchedulingMode.SLOT)` as the FIRST new operation after the existing joins, touching no other line of the body. Apply the identical one-line join+filter to `breakDuration` (238), `breakBlockedWindow` (264), `breakStartAlignment` (301), and the two `OPEN_RESOLVE_IN_PHASE_15` preference constraints (locate via `ScheduleConstraintClassification.java`).

### `ScheduleConstraintProvider.java` — `breakClustering` real body (line 573)

**Analog:** `bulkUnderallocationHard` (423-440) and `minimumStaffing`'s `groupBy(timeslot, ...)` shape for the cross-agent per-timeslot aggregation mechanics; the file's own header comment (29-47, cited by RESEARCH.md) documents the "hoist shared grouping lambdas to avoid duplicate stream nodes" discipline — measured 32,489/sec → 44,000+/sec improvement — which this new constraint must respect if it shares a grouping key with an existing constraint. RESEARCH.md §Pattern 4 has a full sketch; treat it as MEDIUM-confidence scaffolding, not verbatim-correct — the exact on-break derivation and penalty formula need the ENVL-09 single-band-vs-multi-band fixture to validate empirically.

### `src/main/java/com/wfm/service/SolverService.java` — `buildShiftAssignments` (NEW)

**Analog:** `computeAgentDayConfigs` (line 559, full method not re-read here but signature/purpose confirmed) — D-05 explicitly requires this new method to consume the *same* `List<AgentDayConfig>` output already computed at line 256/320, filtering to `effectiveHours > 0`, and emit one `AgentShiftAssignment` per surviving entry. Follow the existing call-site pattern: computed once in `buildSchedule`-adjacent flow (~line 255-320), then `schedule.setShiftAssignments(...)` mirroring `schedule.setAgentDayConfigs(agentDayConfigs)` at line 320.

### `src/main/java/com/wfm/service/ScheduleService.java` — accept-time snapshot

**Analog:** the existing `StaffingRequirement` and `AgentAssignment` snapshot-and-remap block, `acceptSchedule` lines 255-294 (read verbatim this session):
```java
for (StaffingRequirement live : liveRequirements) {
    UUID snapshotTimeslotId = timeslotRemap.get(live.getTimeslot().getId());
    if (snapshotTimeslotId == null) continue;
    Timeslot snapshotTs = entityManager.getReference(Timeslot.class, snapshotTimeslotId);
    StaffingRequirement snapshot = new StaffingRequirement();
    snapshot.setTenantId(tenantId);
    snapshot.setDeskId(deskId);
    snapshot.setScheduleId(saved.getId());
    snapshot.setTimeslot(snapshotTs);
    // ... copy remaining fields
    entityManager.persist(snapshot);
}
```
D-07 deviates deliberately: `AgentShiftAssignment`'s accept-time write denormalises scalars (template name,
start, end, actual band offset/duration, nullable `source_template_id`) rather than copying `ShiftTemplate` +
band rows into schedule-scoped copies — do NOT mirror the `Timeslot`/`StaffingRequirement` copy-the-whole-entity
pattern here; only mirror the *remap-and-persist-a-new-row* control flow shape.

### `src/main/resources/solverConfig.xml`

**Analog: the codebase's own prior two-`<entityClass>` configuration, reverted at commit `299c42c` (`revert(12): remove the atomic shift move ahead of hourly-slot trial`).** That revert removed a `unionMoveSelector` block, not a second `<entityClass>`/CH-phase structure per se — confirm by diffing `299c42c^:src/main/resources/solverConfig.xml` before assuming its shape transfers. RESEARCH.md §Pattern 1 supplies a verified-against-XSD example that should be treated as primary; use the git history only to confirm current file structure/comments to preserve, not to copy the actual CH-phase XML shape (Phase 12's second entity class was for a custom move, not a second `@PlanningEntity`).

### `frontend/src/pages/ShiftLibrary.tsx` — break band editor & Suggested Library panel

**Analog:** the page's own existing Phase 14 single-break-field form (`renderForm`) and `CoveragePanel` component (line 98, confirmed) plus the `err.details`-driven refusal-rendering block (lines 203-366, confirmed present). Reuse `CoveragePanel` verbatim for D-12's partial-coverage rendering — do not build a second uncovered-windows renderer (UI-SPEC explicit instruction, Component Spec §3).

**No exact repeatable-row-list analog found in `DeskAgents.tsx`** — that file's `.map()` usages (lines 49, 238-338, 444-751, confirmed via grep) are all rendering/selection maps over already-fixed collections (days, specs, paginated agents), not an "add a blank row to a growing form array" pattern. The band editor's "+ Add Break Band" appends-a-blank-row interaction has **no existing precedent in this codebase** — implement it as a plain `useState<BandRow[]>` array with `setBands([...bands, blankBand])`/`setBands(bands.filter((_, i) => i !== idx))`, following this codebase's general hooks-only, no-library convention (confirmed: zero UI-library deps, per UI-SPEC Design System section).

### `frontend/src/pages/ScheduleResults.tsx` — `AgentAllocationTab` shift grouping

**Analog:** itself — the existing per-date rendering block starting at line 285 (confirmed present, full body not re-read here per token budget; UI-SPEC's own required reading already quotes the branch-at-the-top instruction). Implementation must literally be `if (schedulingMode !== 'SHIFT') { /* existing render, untouched */ } else { /* new grouped render */ }` as the very first statement in the per-date block — mirrors the backend's own mode-gating discipline (filter first, body untouched).

### `frontend/src/api/client.ts`

**Analog:** `ShiftTemplate`/`ShiftTemplateBody` interfaces (lines 317-318, confirmed) for the new band-array field shape; `AgentScheduleEntry` (362-371, full interface read this session) for adding the optional `shift` descriptor field.

**Confirmed gaps requiring new fields (UI-SPEC flagged, verified this session):**
- `ScheduleDetail` (line 415, `extends ScheduleSummary`) has **no `schedulingMode` field today** — closest existing optional-field precedent for "a per-schedule scalar recorded at accept/solve time" is `preferenceReport: PreferenceReport | null` (line 418) — same optional/nullable-at-the-top-level idiom to copy for `schedulingMode: 'SLOT' | 'SHIFT'` (non-nullable per UI-SPEC — every schedule was solved under exactly one mode).
- `AgentScheduleEntry` (362-371, full interface) has **no shift descriptor** — add `shift: { templateName: string; startTime: string; endTime: string } | null`, following the exact nullable-nested-object idiom already used by `breaks: BreakDetail[]` (a sibling nested-shape field on the same interface, though that one is an array not a nullable object — the closest *nullable single object* precedent on this same interface class is absent; the nearest true analog in the file is `preferenceReport: PreferenceReport | null` on `ScheduleDetail`).

## Shared Patterns

### Tenant scoping
**Source:** `ShiftLibraryValidationService.java` line 67, every method starts `long tenantId = TenantContext.getTenantId();` — never a caller-supplied tenant (T-14-15 convention).
**Apply to:** `ShiftLibraryGenerationService`, any new repository method, `ShiftTemplateBreakBandRepository`.

### Record-based immutable problem facts
**Source:** `ScheduleConfig.java` (full file), `AgentDayConfig`, `TimeslotDemandConfig`.
**Apply to:** `ShiftBandPair`. Every value the solver reads that is NOT a genuine planning variable must be a plain immutable `record`, resolved once per solve.

### Configuration-driven constraint behaviour (mode-gating)
**Source:** `ScheduleConstraintProvider.minimumStaffing` javadoc (~456-461): "a per-desk configuration row, not a code decision."
**Apply to:** all six mode-gated constraints; the mode itself lives on `ScheduleConfig` exactly as `breakClusterThresholdPct` already does.

### Error/refusal contract
**Source:** `ErrorResponse.ErrorDetail`, `PreSolveValidationException`, used identically by `ShiftLibraryValidationService.requireShiftModeReady` (94-116).
**Apply to:** `ShiftLibraryGenerationService`'s D-12 refusal and partial-coverage responses — must emit the same `ErrorDetail` shape so `ShiftLibrary.tsx`'s existing `err.details` handling (203-366) and `CoveragePanel` work unmodified on the frontend.

### Accept-time snapshot / remap
**Source:** `ScheduleService.acceptSchedule` lines 255-294.
**Apply to:** `AgentShiftAssignment`'s accept-time persistence — control-flow shape only (remap live→snapshot ID, `entityManager.getReference`, `entityManager.persist`), NOT the copy-the-whole-entity field shape (D-07 deliberately denormalises instead).

### No component library, inline styles only
**Source:** `frontend/src/pages/ShiftLibrary.tsx` and `ScheduleResults.tsx` in full (confirmed zero UI-library deps).
**Apply to:** every new frontend element this phase adds — band editor, Suggested Library panel, Agent Allocation group headers.

## No Analog Found

| File / Concern | Role | Data Flow | Reason |
|---|---|---|---|
| Parent-side `@OneToMany` collection mapping (`ShiftTemplate` → bands) | model | CRUD | Confirmed via `grep -rl "OneToMany" src/main/java/com/wfm/model/` — zero hits anywhere in the codebase. Every existing parent/child relationship is child-FK + repository-query, never a mapped collection. Planner must decide explicitly whether to introduce the codebase's first `@OneToMany` or follow the repository-query convention (recommended: the latter, to avoid a first-of-its-kind pattern with no sibling to imitate). |
| The "migrate one row's data into N child rows, then drop the source columns" migration shape | migration | batch | Every existing `DROP COLUMN` migration (V2, V3, V8, V14, V15, V18) drops a column that is redundant or superseded by an *already-existing* different column — none first fans data into a brand-new child table. This is genuinely the first migration of its shape in this repo. |
| `SolverConfigBuildTest.java` (XCUT-03) | test | n/a | Confirmed by directory listing of `src/test/java/com/wfm/solver/` — no file builds a `SolverFactory` from the real XML today. RESEARCH.md §Code Example A supplies a full sketch; there is no in-repo test to imitate structurally beyond "a plain JUnit 5 class with one `@Test` method," which every test file in this codebase already follows. |
| `ShiftEnvelopeGroundTruthTest.java` (ENVL-07 walker) | test | n/a | No test-side walker outside the score director exists in `src/test`. The only precedent is `SPIKE-COUPLING.md`'s throwaway `SpikeMain.java`, which is research scaffolding, not shipped test code, and was not located in this session's search scope (referenced only in RESEARCH.md §Code Example B, not independently re-verified against a live file this session). |
| `ShiftModelBenchmarkTest.java` (XCUT-04 A/B benchmark harness) | test | batch | The direct analog, `AtomicShiftMoveBenchmarkTest.java` (547 lines per the revert commit's diffstat), was deleted in its entirety at commit `299c42c`. **It IS recoverable**: `git show 299c42c^:src/test/java/com/wfm/solver/AtomicShiftMoveBenchmarkTest.java` will retrieve the full file as it existed immediately before the revert. Recommend the planner run that retrieval as a first step of the XCUT-04 plan slice rather than re-deriving the harness shape from `12-BENCHMARK.md` prose alone. |
| Frontend "append a blank row to a growing form-array" interaction (band editor `+ Add Break Band`) | component | CRUD (form) | `DeskAgents.tsx`'s `.map()` calls are all over fixed/pre-populated collections (days-of-week, existing agents, pagination), never a client-only growable array seeded by a button click. No sibling pattern to imitate beyond the codebase's general hooks-only convention. |
| `AgentScheduleEntry.shift` nullable nested-object field | type/DTO | transform | `AgentScheduleEntry` today has no nullable single-object field (its `breaks` field is an array, not a nullable object) — the nearest true "nullable object field on a response DTO" precedent lives one level up on `ScheduleDetail.preferenceReport`, not on a sibling of the same interface. |

## Metadata

**Analog search scope:** `src/main/java/com/wfm/model/`, `src/main/java/com/wfm/service/`, `src/main/java/com/wfm/solver/`, `src/main/resources/db/migration/`, `src/test/java/com/wfm/solver/`, `frontend/src/pages/`, `frontend/src/api/client.ts`, plus `git log`/`git show` against commit `299c42c` for the reverted Phase 12 benchmark harness and solverConfig.xml shape.
**Files scanned:** ~40 read/grepped directly this session (full-file reads: `AgentAssignment.java`, `ScheduleConfig.java`, `ShiftTemplate.java`, `AgentDayHours.java`, `ShiftLibraryValidationService.java`, `MinimumStaffingConstraintTest.java` [partial], `V37`/`V38`/`V39` migrations; targeted greps/reads across `ScheduleConstraintProvider.java`, `SolverService.java`, `ScheduleService.java`, `ScheduleResults.tsx`, `client.ts`, `ShiftLibrary.tsx`, `DeskAgents.tsx`).
**Pattern extraction date:** 2026-08-26
