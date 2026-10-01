# Business-Date Join Key-Position Allowlist (SOLV-02 / SOLV-07)

This file is parsed at test time by `BusinessDateJoinGuardTest`
(`src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java`) — editing this file changes what
the build enforces, not merely what a human reads.

## Why this guard exists

A `join`/`equal`/`groupBy`/`computeIfAbsent` key position that resolves a `Timeslot`'s **calendar**
date where **business** date is meant is a silent non-join: a constraint matching zero tuples
scores identically to a satisfied one (the schedule is still hard-0, just structurally wrong), and
a grouping key mismatch silently drops the affected rows from whatever map they were meant to
populate. Neither failure mode throws, and neither is visible to a behavioural test that only
checks the final score — the join simply never fires. SOLV-01's re-anchoring work (plan 20-05) and
SOLV-07's coverage/demand-upload work (plans 20-06/20-07) are what this guard polices: it fails the
build the moment a key position of this shape exists anywhere in the four files below without a
documented, reasoned allowlist entry.

### Allowlist

```
```

Zero entries is the EXPECTED steady state (D-08 predicted "plausibly empty for key positions"; this
guard is what proves that rather than assumes it). An empty fenced block makes a naive set-equality
assertion vacuously satisfiable the moment production is fully migrated — `BusinessDateJoinGuardTest`
carries two independent liveness proofs (a matcher-level proof against synthetic strings, and a
pipeline-level proof against a tracked synthetic offender under
`src/test/resources/bday-join-guard-offender/`) specifically so an empty allowlist here stays
honest rather than becoming decoration.

**Today**, before plans 20-05/20-06/20-07 migrate the four guarded files, this guard is EXPECTED to
be **RED**: fourteen key positions across `ScheduleConstraintProvider` (7) and `ScheduleOutputService`
(6), plus one in `ShiftLibraryGenerationService`, are still on calendar date. It goes green only
once the last of the four files is migrated, which is the honest end-of-migration signal for this
phase. Do not add allowlist entries to make it green prematurely — an allowlist entry records a site
that legitimately keeps calendar-date semantics, never a site this phase is going to migrate.

## Known scope boundaries — deliberate, not gaps

Every site below is either genuinely out of this guard's four-verb scope by design, or a measured
blind spot this guard's own textual technique cannot close. Recording them here means the next
reader files each as a decision rather than rediscovering it as a missed migration.

- **`ScheduleOutputService` lines 664 and 759** build an operator-facing timeslot label by string
  concatenation (`ts.getDate() + " " + startTime + "-" + endTime`, and the equivalent in
  `buildAcceptedConstraintViolations`). They keep the calendar date per D-10: a label answers "when
  does this happen", which is a calendar question — on a 21:00-anchored desk a 02:00 slot belongs to
  business day D but occurs on calendar day D+1, and the operator needs the latter to find the row
  on their calendar. Concatenation is not one of the four scanned verbs, so these are out of scope
  by construction and need no allowlist entry. All labelling change belongs to OVNT-07 (Phase 21).

- **`StaffingRequirementService` line 378** (`t.getDate()`, populating
  `StaffingRequirementResponse.Item`'s `date` field) keeps the calendar date for the same D-10
  display reason — it is a response-DTO read, not a join/group key, and uses no verb this guard
  scans.

- **`ShiftLibraryGenerationService` lines 226 and 616** are key positions in substance — a
  `.distinct()` identity (`new ShiftLibraryValidationService.Window(sr.getTimeslot().getDate(), ...)`)
  and a `Collectors.toCollection(TreeSet::new)` accumulation (`.map(sr -> sr.getTimeslot().getDate())`)
  — but `.distinct()` and `TreeSet`-collection are not among the four scanned verbs, so this guard
  structurally cannot discover them. They are migrated by explicit task instruction in plan 20-06;
  this guard's green does not cover them, and that is deliberate, not an oversight.

- **`StaffingRequirementRepository`'s calendar-date range delete has two callers beyond the
  demand-upload path** — the Erlang C and Erlang X staffing calculators — whose from/to range
  arrives from the request payload as calendar dates. Those two keep calendar-date semantics in
  this phase: SOLV-07's named surface is demand upload, coverage reporting and the solver, and no
  desk carries a non-midnight anchor until this phase's final commit, so both Erlang paths are
  unreachable instances of the same latent defect today, not live ones. A Phase 21 owner should
  pick them up alongside OVNT-01.

- **The three candidate tokens D-09 rejected** — `.getDayOfWeek()`, `.plusDays(`, and
  `ChronoUnit.DAYS` — are not scanned, because they fire overwhelmingly on types with no competing
  calendar-date meaning, which is the measured path to a decoration guard (Phase 18's D-03). `Timeslot`
  is the only type in this model carrying two dates.

### Two additional, measured blind spots (found while building this guard, not inherited)

These are genuine gaps in what this guard's single-line textual technique can see, surfaced by
running the scan against the actual tree rather than assumed from the plan's prose. Recorded here
so a later migration's correctness is verified by reading the diff directly, not by trusting this
guard to catch a regression at these specific sites.

- **`ScheduleConstraintProvider`'s shared `DATE` lambda (lines 91-92) and the seven
  `.groupBy(AGENT_ID, DATE, ...)` consumers it feeds (lines 299, 356, 389, 438, 692, 718, 1109) are
  INVISIBLE to this guard, in both directions.** The lambda's own declaration line
  (`a -> a.getTimeslot().getDate();`) holds a genuine chained-accessor receiver shape but no verb
  token. Each `groupBy(` consumer line holds the verb token but references the symbolic constant
  `DATE`, never a literal `.getDate()` call, so the receiver predicate finds nothing there either.
  This guard is a purely textual, single-line scan (matching every precedent in this codebase) and
  does not perform data-flow analysis to resolve what a named constant's value actually reads.
  Fixing the one lambda line correctly fixes all seven downstream consumers for free, but this
  guard will not see that fix (or a future regression undoing it) in any of those eight lines. This
  is the single largest blind spot this guard carries — it covers roughly half of SOLV-01's edit
  surface — and the correctness of plan 20-05's migration at these eight lines must be verified by
  direct code review, not by this guard's green.

- **`ShiftLibraryGenerationService` line 180**
  (`.filter(sr -> cluster.contains(sr.getTimeslot().getDate().getDayOfWeek()))`) is a weekday
  derivation of the identical shape and identical defect as the migrated line 486, and was named by
  D-13 as one of the phase's four `ShiftLibraryGenerationService` key positions — but `.filter(` and
  `.contains(` are not among the four scanned verbs, so this guard cannot discover it either,
  exactly like lines 226 and 616 above. It migrates in plan 20-06 by explicit task instruction, not
  by this guard's enforcement.

- **`StaffingRequirementService` lines 172-175** (the `minDate`/`maxDate` derivation feeding the
  destructive `deleteLiveByDeskAndDateRange` call, D-15's named site) uses `.map(Timeslot::getDate)`
  chained to `.min(...)`/`.max(...)` — neither `.min(` nor `.max(` is among the four scanned verbs,
  so despite `StaffingRequirementService` being one of the four files this guard's file list names,
  this specific site (the whole reason the file was added, D-15) is structurally unreachable by the
  scan. This means `StaffingRequirementService` currently contributes ZERO matched lines to this
  guard regardless of migration state — its presence in the four-file scope keeps the door open for
  any FUTURE key position in this file that genuinely uses one of the four verbs, but today's actual
  demand-upload defect must be verified by plan 20-07's own direct proof, not by this guard.
