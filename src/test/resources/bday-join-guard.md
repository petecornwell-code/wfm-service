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

**History.** Before plans 20-05/20-06 migrated `ScheduleConstraintProvider` and `ScheduleOutputService`/
`ShiftLibraryGenerationService`, this guard was RED: fourteen key positions across
`ScheduleConstraintProvider` (7) and `ScheduleOutputService` (6), plus one in
`ShiftLibraryGenerationService`, were still on calendar date. Do not add allowlist entries to make it
green prematurely — an allowlist entry records a site that legitimately keeps calendar-date
semantics, never a site this phase is going to migrate.

**Current state, as of plan 20-07 (completed).** All matched lines in all three of
`ScheduleConstraintProvider`, `ScheduleOutputService` and `ShiftLibraryGenerationService` are
migrated, so this guard's headline set-equality test is GREEN -- it went green at plan 20-06,
one plan ahead of this guard's own original prediction. **That green never meant SOLV-07 was fully
delivered, and still does not prove it today.** `StaffingRequirementService`'s D-15
destructive-delete defect (`:172-184`) contributes ZERO matched lines to this guard regardless of
migration state, because its range derivation uses `.map(Timeslot::getBusinessDate).min()/.max()`
and neither `.min(` nor `.max(` is among the four scanned verbs (see "Known scope boundaries"
below). Plan 20-07 fixed that defect — the demand-upload replace path now derives its range from
`getBusinessDate()` and calls a business-date-filtering delete
(`deleteLiveByDeskAndBusinessDateRange`) — but it did so by direct code review and its own
survivor-observing test (`StaffingRequirementBusinessDateDeleteTest`), not because this guard
turned red and then green again. This guard's green has never been, and still is not, evidence
about this file; treat its silence on `StaffingRequirementService` as a structural blind spot, not
as a clean bill of health.

## Known scope boundaries — deliberate, not gaps

Every site below is either genuinely out of this guard's four-verb scope by design, or a measured
blind spot this guard's own textual technique cannot close. Recording them here means the next
reader files each as a decision rather than rediscovering it as a missed migration.

- **`SolverService` is deliberately not one of the four files in `TARGET_FILES` above.** Adding it
  would contribute ZERO matched lines: this guard requires one of the four verb tokens
  (`join(`/`equal(`/`groupBy(`/`computeIfAbsent(`) AND a `Timeslot`-receiver date read on the SAME
  line, and none of the four `Timeslot`-receiver calendar-date reads this file was found to carry
  (enumerated below) satisfies the verb half. Its presence would therefore make an empty allowlist
  appear to certify a ~1900-line date-sensitive file the scan structurally cannot see -- the
  decoration outcome D-08 and D-09 chose the four-verb scope to avoid, the identical failure
  already recorded below for `StaffingRequirementService`'s `.min(`/`.max(` blind spot.

  The audit (plan 20-11, prompted by `20-VERIFICATION.md` advisory 1: "is CR-02 the only remaining
  instance, or one of several?") found all four of this file's `Timeslot`-receiver calendar-date
  reads. Two were found by the phase's own code review and fixed by plan 20-10; two more were found
  by the audit advisory 1 itself prompted and fixed by plan 20-11 -- one careful pass found half of
  them, which is the measured case for treating this file's absence from the guard as a real
  residual risk rather than a formality.

  - `runPreSolveValidation` lines 1104 and 1127 (pre-solve checks 2 and 3; migrated by plan
    20-11). Check 2 built its period-coverage set with `.map(Timeslot::getDate)` -- a `.map(` with
    a method reference, not a scanned verb. Check 3 selected the last timeslot of a day with
    `.filter(t -> t.getDate().equals(first.getDate()))` -- a `.filter(` with a lambda over locals
    named `t` and `first`, not the bare `ts` variable the receiver heuristic recognises, and
    `.equals(` is not a scanned verb either.
  - `expandMinimumStaffingSeats` lines 1984 and 1995 (shift-template weekday eligibility and the
    `workingAgentDaysByDate` count lookup; migrated by plan 20-10). The eligibility read was a bare
    local assignment, `LocalDate tsDate = ts.getDate();` -- the receiver (`ts`) IS the bare variable
    the heuristic recognises, but the line itself carries no verb token at all. The count lookup was
    `workingAgentDaysByDate.getOrDefault(ts.getDate(), 0)` -- a `.getOrDefault(` lookup, also not a
    scanned verb.

  Correctness at all four sites was established by direct reading plus two proving test classes --
  `MinimumStaffingSeatsBusinessDateTest` (the seat-expansion pair) and
  `PreSolveValidationBusinessDateTest` (the pre-solve pair), each anchored at 21:00 with a
  midnight-anchored control -- never by this guard turning green, because it cannot see any of the
  four regardless of migration state. That remains true after this plan: a future regression at any
  of these four lines will not fail `BusinessDateJoinGuardTest`, so correctness there must keep
  being verified by reading the diff, not by trusting this guard's silence.

  What would change this decision: a guard that reaches these shapes needs a different predicate --
  non-join key positions (`.map(`, `.filter(`, `.getOrDefault(`, bare local assignments) and a
  type-aware receiver test rather than a bare-variable name match -- with its own allowlist-cost
  measurement taken before it is adopted (18-CONTEXT.md D-03 measured the naive token scan at 100+
  entries). That is a later-phase guard-design item, not a gap in this one.

- **`TimeslotRepository`'s `...AndDateBetween...` derived-query method name and
  `StaffingRequirementRepository`'s `t.date BETWEEN :from AND :to` JPQL literal are a THIRD shape
  this guard cannot see, continuing the `SolverService` audit immediately above.** Both finders
  are fed BUSINESS-date arguments (`schedule.getPeriodStartDate()`/`getPeriodEndDate()`,
  `18-CONTEXT.md` D-22) at four call sites -- `SolverService.startSolve`'s two problem-fact loads
  and `ScheduleService.acceptSchedule`'s two snapshot loads -- while both finders filter
  `Timeslot`'s CALENDAR `date` column. This guard's predicate requires one of the four verb tokens
  (`join(`/`equal(`/`groupBy(`/`computeIfAbsent(`) AND a `Timeslot`-receiver `.getDate()` call on
  the SAME line; a Spring Data derived-query method name encodes the date column in an identifier
  with no `.getDate()` call and no verb token at all, and a JPQL string literal
  (`t.date BETWEEN :from AND :to`) has neither either. This is a THIRD shape alongside the
  `.min(`/`.max(` and `.distinct()`/`TreeSet` shapes this file already documents -- and it is why
  these four sites survived plan 20-11's otherwise complete `SolverService` audit, which
  enumerated `Timeslot`-receiver reads inside that file and therefore could not reach a defect
  expressed as a repository method name one call frame away.

  Correctness at all four sites was established by a single shared fix, `BusinessDayPeriodLoader`
  (mirroring `TimeslotGeneratorService`'s BDAY-03 widen-then-derive read-back rather than adding a
  stored-column finder), and two proving test classes -- `ScheduleServiceShiftSnapshotTest`'s
  21:00 accept-path cases and `BusinessDayPeriodLoaderTest` -- each anchored at 21:00 with a
  midnight-anchored control. **This guard was green throughout and never turned red for any of
  these four sites** -- its silence here was a blind spot, not a clean bill of health.

  `TimeslotGeneratorService.listTimeslots` and `StaffingRequirementService`'s paginated
  `findLiveByDeskAndDateRangeAfterCursor`/`findLiveByDeskAndDateRange(..., Pageable)` overloads
  are deliberately left on calendar date -- both are operator-facing, fed request-payload calendar
  dates, the same D-10 reason already recorded above. **Plan 21-04 (OVNT-02) migrated the two
  Erlang calculator paths off this calendar-date JPQL-literal shape entirely** -- they no longer
  call `deleteLiveByDeskAndDateRange` at all, so they are no longer an instance of this shape and
  are removed from this list; see the "`StaffingRequirementRepository`'s calendar-date range
  delete's Erlang C and Erlang X callers" bullet later in this section for the migration itself.

  What would change this decision: a guard that reaches this shape needs to scan repository
  interfaces for derived-query identifiers containing `Date` and for `@Query` string literals
  naming `t.date`, with its own allowlist-cost measurement taken first -- the paginated
  operator-facing overloads and `TimeslotGeneratorService.listTimeslots` legitimately keep
  calendar-date semantics and would each need an entry. That is a later-phase guard-design item,
  consistent with how plan 20-11 dispositioned the same question for `SolverService`'s absence
  from `TARGET_FILES`.

- **`ScheduleOutputService` lines 670 and 771** (line numbers as of plan 20-06; originally 664 and
  759 before this plan's explanatory comments shifted them) build an operator-facing timeslot label
  by string
  concatenation (`ts.getDate() + " " + startTime + "-" + endTime`, and the equivalent in
  `buildAcceptedConstraintViolations`). They keep the calendar date per D-10: a label answers "when
  does this happen", which is a calendar question — on a 21:00-anchored desk a 02:00 slot belongs to
  business day D but occurs on calendar day D+1, and the operator needs the latter to find the row
  on their calendar. Concatenation is not one of the four scanned verbs, so these are out of scope
  by construction and need no allowlist entry. All labelling change belongs to OVNT-07 (Phase 21).

- **`StaffingRequirementService` line 388** (`t.getDate()`, populating
  `StaffingRequirementResponse.Item`'s `date` field; line number as of plan 20-07) keeps the
  calendar date for the same D-10 display reason, now with an explanatory comment stating so in
  production — it is a response-DTO read, not a join/group key, and uses no verb this guard scans.

- **`ShiftLibraryGenerationService` lines 226 and 616** are key positions in substance — a
  `.distinct()` identity (`new ShiftLibraryValidationService.Window(sr.getTimeslot().getBusinessDate(),
  ...)`) and a `Collectors.toCollection(TreeSet::new)` accumulation
  (`.map(sr -> sr.getTimeslot().getBusinessDate())`) — but `.distinct()` and `TreeSet`-collection are
  not among the four scanned verbs, so this guard structurally cannot discover them, migrated or not.
  They were migrated by explicit task instruction in plan 20-06 (completed); this guard's green never
  covered them and still does not — their correctness was verified by direct code review, not by this
  guard turning green.

- **`StaffingRequirementRepository`'s calendar-date range delete's Erlang C and Erlang X callers
  were migrated to the business-date twin in plan 21-04 (OVNT-02, the `migrate-now` decision).**
  Before plan 21-02 made a non-midnight-anchored desk reachable, both calculators' from/to range
  arrived from the request payload as calendar dates and the delete it fed was filtered on
  calendar date too, so the two readings produced identical rows on every desk that existed —
  "unreachable instances of the same latent defect today, not live ones," as this bullet put it
  until this plan. 21-02 ended that unreachability in the same phase it was created, so the
  operator decided at this plan's checkpoint to close the gap immediately rather than defer it:
  both calculators now derive their clear range from the business date, matching the demand-save
  path's existing use of `deleteLiveByDeskAndBusinessDateRange`, so all three write paths agree on
  one date system. The two remaining callers of the calendar-date twin —
  `TimeslotGeneratorService`'s generation path and `FteUploadService`'s FTE upload path — were
  deliberately left unchanged and keep calendar-date semantics; see this file's "Known scope
  boundaries" entry above and the repository comment at `StaffingRequirementRepository` for why.

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
  (`.filter(sr -> cluster.contains(sr.getTimeslot().getBusinessDate().getDayOfWeek()))`) is a weekday
  derivation of the identical shape and identical defect as the migrated line 486 (now line 497 after
  this plan's comments), and was named by D-13 as one of the phase's four `ShiftLibraryGenerationService`
  key positions — but `.filter(` and `.contains(` are not among the four scanned verbs, so this guard
  cannot discover it either, exactly like lines 226 and 616 above. It was migrated in plan 20-06
  (completed) by explicit task instruction, not by this guard's enforcement; verified by direct code
  review (`grep -c 'getTimeslot().getDate()'` over the file prints 0).

- **`StaffingRequirementService` lines 178-184** (the `minDate`/`maxDate` derivation feeding the
  destructive delete call, D-15's named site; lines 172-175 before plan 20-07's edit) uses
  `.map(Timeslot::getBusinessDate)` chained to `.min(...)`/`.max(...)` — neither `.min(` nor
  `.max(` is among the four scanned verbs, so despite `StaffingRequirementService` being one of
  the four files this guard's file list names, this specific site (the whole reason the file was
  added, D-15) is structurally unreachable by the scan, migrated or not. This means
  `StaffingRequirementService` contributes ZERO matched lines to this guard regardless of
  migration state — its presence in the four-file scope keeps the door open for any FUTURE key
  position in this file that genuinely uses one of the four verbs. **Plan 20-07 migrated this
  site**: the range is now derived from `getBusinessDate()` and passed only to
  `deleteLiveByDeskAndBusinessDateRange`, a new repository method whose query filters the
  timeslot's `businessDate` column rather than its `date` column. Verified by
  `StaffingRequirementBusinessDateDeleteTest`'s observed-survivor assertions at a 21:00 and a
  00:00 anchor and by direct code review, not by this guard — it still cannot see this site and
  never will, so a future regression here would also go unnoticed by this guard.
