---
phase: 22-minimum-rest
reviewed: 2026-10-04T00:00:00Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - frontend/src/api/client.ts
  - frontend/src/pages/DeskManagement.tsx
  - frontend/src/pages/ScheduleResults.tsx
  - src/main/java/com/wfm/controller/ScheduleController.java
  - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
  - src/main/java/com/wfm/repository/AgentRestWaiverRepository.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/test/java/com/wfm/service/RestWaiverDisclosureTest.java
  - src/test/java/com/wfm/service/ScheduleSummaryConstructionSiteGuardTest.java
  - src/test/java/com/wfm/service/ScheduleSummaryReadTest.java
  - src/test/resources/schedule-summary-construction-site.md
findings:
  critical: 1
  warning: 2
  info: 1
  total: 4
status: issues_found
---

# Phase 22 (plans 22-11/22-12): Code Review Report

**Reviewed:** 2026-10-04
**Depth:** standard
**Files Reviewed:** 12
**Status:** issues_found

## Summary

This is the incremental review of plans 22-11 and 22-12, which closed three findings from the
prior review (WR-01, WR-02, IN-01). All three are verified genuinely closed:

- **WR-01** — `ScheduleOutputService.buildRestWaiverDisclosure` (line 971) now reads
  `schedule.getDayStart() != null ? schedule.getDayStart() : LocalTime.MIDNIGHT`, the same
  null-coalescing shape used elsewhere in the codebase, instead of a raw `getDayStart()` call that
  could NPE through `DayWindow.anchoredAt`. Confirmed.
- **WR-02** — `ScheduleController.toSummary` is gone; `grep -rn "new ScheduleSummary("
  src/main/java/` returns exactly one hit, in `ScheduleService.toSummary`, matching the registry
  `schedule-summary-construction-site.md` parses and `ScheduleSummaryConstructionSiteGuardTest`
  enforces. Confirmed.
- **IN-01** — `DeskManagement.tsx`'s `hoursStringToMinutes` now returns `undefined` (not `null`)
  for a non-finite parse, and `handleUpdate` checks `editedMinimumRestMinutes !== undefined`
  *before* the change-comparison, so a stray non-numeric keystroke can no longer clear a desk's
  configured minimum rest via `JSON.stringify(NaN) === null`. Confirmed.

However, tracing the project invariant about `spring.jpa.open-in-view: false` and
`AgentRestWaiver.agent` being `FetchType.LAZY` past the files this plan touched surfaced a live,
unfixed instance of exactly the class of defect this phase exists to close — in the one production
call site plan 22-12's own repository javadoc claims is already migrated. That is CR-01 below, and
it is the most important finding in this review.

## Critical Issues

### CR-01: The live (RUNNING, in-memory) schedule path still loads rest waivers without fetching `agent`, so polling a schedule with configured rest throws `LazyInitializationException`

**File:** `src/main/java/com/wfm/repository/AgentRestWaiverRepository.java:47-56` (the claim), corroborated by `src/main/java/com/wfm/service/SolverService.java:225-226` (the unmigrated call site) and exercised by `src/main/java/com/wfm/service/ScheduleOutputService.java:1016-1049` (`buildRestWaiverDisclosure`'s `waiver.getAgent().getName()` read)

**Issue:**

`AgentRestWaiverRepository.findWithAgentByTenantIdAndDeskIdAndDateBetween`'s javadoc (added by this
phase) states the non-fetching sibling `findByTenantIdAndDeskIdAndDateBetween` is:

> "superseded as a waiver-loading call site ... **on every production path** (including this
> method's own former caller, `ScheduleService.loadSnapshotData`)"

That claim is false. `grep -rn "findByTenantIdAndDeskIdAndDateBetween\b" src/main/java/` still shows
`SolverService.java:225` calling the old, non-fetching finder:

```java
List<AgentRestWaiver> restWaivers = agentRestWaiverRepository.findByTenantIdAndDeskIdAndDateBetween(
        tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
```

This runs inside `startSolve`'s `@Transactional(readOnly = true)` block, whose own javadoc says:
"the persistence context closes after this method, detaching all entities." The loaded
`AgentRestWaiver` rows are set onto the live, in-memory `Schedule` (`schedule.setAgentRestWaivers`
elsewhere in that method) with their `@ManyToOne(fetch = FetchType.LAZY) agent` association still
an uninitialized Hibernate proxy.

That `Schedule` instance then lives in `InMemoryScheduleStore` for the duration of the solve (often
minutes), polled by two endpoints that both end up calling
`ScheduleOutputService.buildRestWaiverDisclosure`:

- `GET /schedules/{id}/summary` → `ScheduleService.getScheduleSummary` → for the in-memory branch
  (`fromDb == false`), calls `toSummary(schedule, deskName)` directly with **no re-fetch at all**.
- `GET /schedules/{id}` → `ScheduleService.getScheduleDetail` → `if (fromDb) loadSnapshotData(...)`
  is skipped entirely for the in-memory branch, so the stale waiver rows from `startSolve`'s now-closed
  transaction are exactly what `buildRestWaiverDisclosure` reads.

Inside `buildRestWaiverDisclosure`, for every waiver whose date falls in the schedule's period:

```java
Agent agent = waiver.getAgent();
if (agent == null || agent.getId() == null) continue;
...
RestWaiverEntry entry = new RestWaiverEntry(agentId, agent.getName(), ...);
```

`agent.getId()` on an uninitialized Hibernate proxy typically does not trigger a load (the
identifier is embedded in the proxy handle), but `agent.getName()` does — and the proxy's bound
session was closed when `startSolve` returned. A brand-new `@Transactional` block opened later (as
`getScheduleDetail` does) does **not** help: a Hibernate proxy is permanently bound to the session
that created it, not to whatever session happens to be active on the current thread later. The
result is `org.hibernate.LazyInitializationException: could not initialize proxy ... no Session`,
surfaced as an uncaught 500 to the operator who is simply watching the 2-second score poll on a desk
that has `minimumRestMinutes` configured and at least one `AgentRestWaiver` row dated inside the
running solve's period.

This is reachable on any desk that has rest configured and has ever had an operator record a rest
waiver for a date inside the period currently being solved — not a rare combination, since REST-06
waivers exist specifically to be set before a solve that is expected to need one.

No existing test would catch this: every test that exercises this path
(`RestWaiverDisclosureTest`, `ScheduleSummaryReadTest`) builds its `Schedule` fixtures with mocked
repositories returning plain POJOs, never real Hibernate proxies, so the lazy-initialization failure
mode is structurally invisible to the current suite regardless of how thorough the fixtures are.

**Fix:** Point `SolverService`'s rest-waiver load at the same
`findWithAgentByTenantIdAndDeskIdAndDateBetween` finder this phase already introduced and already
re-pointed `ScheduleService.loadSnapshotData` at:

```java
// src/main/java/com/wfm/service/SolverService.java:225
List<AgentRestWaiver> restWaivers = agentRestWaiverRepository
        .findWithAgentByTenantIdAndDeskIdAndDateBetween(
                tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
```

Then correct `AgentRestWaiverRepository`'s javadoc to stop claiming universal coverage until it is
actually universal, and add an integration-style test (a real `@DataJpaTest`/Spring context, not a
mocked repository) that starts a solve, polls `/summary` or `/{id}` while `RUNNING`, and asserts no
exception — the class of test this bug proves the suite is currently missing.

## Warnings

### WR-03: `hydrateRestWaiverInputsFromDb`'s pre-horizon branch is untested through the two new call sites it was built for

**File:** `src/main/java/com/wfm/service/ScheduleService.java:675-682`
**Issue:** `needsPreHorizon` (true when a waiver lands on the schedule's own `periodStartDate`) is
the branch that calls `restPredecessorService.resolvePriorSpans(...)`. It is well covered for the
pre-existing `loadSnapshotData` path (`waiverOnFirstBusinessDateWithAcceptedPredecessor_matchesAgainstThePreHorizonSpan`
in `RestWaiverDisclosureTest.java:197`, using a `D1` waiver), but every new test this plan added for
the DB-fallback summary paths (`acceptedSchedule_dbFallbackSummary_reportsTheTrueCountsNotZero`,
`listSchedules_manyAcceptedSchedules_issuesExactlyOneWaiverQuery`,
`listSchedules_doesNotMutateTheInMemorySchedule`) only ever uses a `D2` waiver. The combination this
plan actually introduced — a period-start-date waiver reached through `listSchedules` or
`getScheduleSummary`, which hydrates via the new helper rather than `loadSnapshotData` — has no
test proving `resolvePriorSpans` is invoked correctly (right `tenantId`/`deskId`/`periodStartDate`)
or that the result lands in the right applied/unused bucket on these two specific paths.
**Fix:** Add a `D1`-dated waiver variant of `acceptedSchedule_dbFallbackSummary_reportsTheTrueCountsNotZero`
(or a dedicated test) that stubs `RestPredecessorService.resolvePriorSpans` and asserts it is
invoked with the expected arguments when reached via `getScheduleSummary`/`listSchedules`, mirroring
the existing `loadSnapshotData` coverage.

### WR-04: The IN-01 fix (`hoursStringToMinutes` / `handleUpdate`) has zero automated regression coverage

**File:** `frontend/src/pages/DeskManagement.tsx:27-32, 138-144`
**Issue:** The commit for IN-01 verifies the change only via `tsc -b --force` (a type-check). There
is no frontend test runner configured anywhere in this repository (`find frontend -iname
"*.test.ts*" -o -iname "*.spec.ts*"` is empty, and `package.json` has no `test` script), so the
exact bug this fix closes — a non-finite parse serialising as `null` and silently clearing a desk's
configured minimum rest — has no regression test backing it. A future edit to `hoursStringToMinutes`
or to the ordering of the `undefined`-check in `handleUpdate` could silently reintroduce the data-loss
bug and nothing in CI would catch it.
**Fix:** Out of scope for this plan alone (no frontend test infrastructure exists at all in this
repo), but worth flagging as a standing gap: this specific function is a good first candidate once a
frontend test runner (Vitest, etc.) is introduced, given it is the one function in this file proven
to have silently caused data loss before.

## Info

### IN-02: The reused inline-validation message is inaccurate for a non-finite parse

**File:** `frontend/src/pages/DeskManagement.tsx:236-249`
**Issue:** The inline warning under the Min Rest input always reads "Minimum rest must be less than
24 hours." — including when `hoursStringToMinutes` returns `undefined` because the operator typed
something non-numeric (e.g. "abc"), which has nothing to do with the 24-hour bound. This is a
deliberate reuse decision recorded in the surrounding comment ("the same existing inline text, never
a new message"), so it is not a defect in the sense of behaving incorrectly — `handleUpdate` does
correctly refuse to save in this case — but the message will read as confusing to an operator who
typed garbage rather than an out-of-range number.
**Fix:** If this surfaces in practice, consider a second short message (e.g. "Enter a number of
hours.") gated on the same `parsed === undefined` branch that already distinguishes this case from
the range check.

---

_Reviewed: 2026-10-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
