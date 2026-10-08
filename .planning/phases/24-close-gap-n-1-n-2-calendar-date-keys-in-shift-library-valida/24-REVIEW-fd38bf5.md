---
phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
reviewed: 2026-10-08T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - src/test/java/com/wfm/service/CallIsolation.java
  - src/test/java/com/wfm/service/ScheduleConsistencyRepairServiceTest.java
  - src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java
  - src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java
findings:
  critical: 0
  warning: 2
  info: 2
  total: 4
status: issues_found
---

# Phase 24: Code Review Report (incremental, fd38bf5 since cd2839ac)

**Reviewed:** 2026-10-08
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

The increment adds `CallIsolation` and one `holdsNoStateAcrossCalls_*` test per service. The three target services (`ScheduleEnvelopeRepairService`, `ScheduleConsistencyRepairService`, `ShiftStartMixTargetService`) have no instance fields, no non-final statics and no caches, so the D7 claim holds. The three test classes pass (`./gradlew --offline test --tests ...`). The fixtures are rebuilt per run, the guard that the two desks differ is sound, and the new `seating` helpers are deterministic. There are no correctness or security defects. The weaknesses are in how much the concurrent half of the proof can detect, so it can pass without exercising what it claims to prove.

## Warnings

### WR-01: Concurrent phase mostly races fixture construction, not the service call

**File:** `src/test/java/com/wfm/service/CallIsolation.java:50-60`
**Issue:** Each `Supplier` builds a fresh fixture and then calls the service. The latch releases the threads together, but the work after release is dominated by fixture construction: `saferide()` builds 57 agents plus templates, bands and seats. The service call is a small window inside that, and the threads drift out of phase almost at once. The tests prove "no state across calls" only if the service calls actually overlap. As written, a shared mutable field in a service could pass this test most of the time. The test claims "run all at once from several threads released together", which overstates what it checks. The successive-rounds half does catch state that persists between calls, which is the more likely failure. The concurrent half is probabilistic at best.
**Fix:** Split each desk into build and run so only the call is timed against the latch:
```java
static <T> void assertNoStateSurvivesACall(Supplier<Callable<T>> deskA, Supplier<Callable<T>> deskB)
// per thread: pre-build all RUNS_PER_THREAD fixtures (Callable<T> closes over the fixture),
// then after start.await() invoke only the pre-built callables.
```
Also add a `CyclicBarrier` per iteration so each round of calls begins together.

### WR-02: Worker failures surface late and unclearly

**File:** `src/test/java/com/wfm/service/CallIsolation.java:61-63`
**Issue:** The futures are awaited one by one with a 60 s timeout each, so the worst case is 8 x 60 s before the suite gives up. A failure in a later thread is only noticed after all earlier futures complete. An `AssertionError` from a worker is wrapped in `ExecutionException`, so the report shows "ExecutionException: java.lang.AssertionError" rather than the AssertJ description. The `.as("desk %s, concurrent run")` message does not include the thread or iteration, which makes a flaky concurrent failure hard to reproduce.
**Fix:** Use an `ExecutorCompletionService`, or unwrap the cause. For example, catch `ExecutionException e` and rethrow `e.getCause()` when it is an `AssertionError`. Add `t` and `i` to the `.as(...)` message, and use one overall deadline instead of a per-future timeout.

## Info

### IN-01: Desk pairs share no date key in two of the three tests

**File:** `src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java:239-247`, `src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java:230-239`
**Issue:** The envelope desks use 2026-10-05 and 2026-09-27, and the mix desks use 2026-08-31 and 2026-10-05. A leak keyed by date is therefore only exercised between repeated runs of the same desk, never from one desk into the other. The consistency pair does share `DAY`. Agent and seat ids are random per run, so any state keyed by id can never collide. The `seating` Javadoc acknowledges this, but the file-level claim is stronger than the check.
**Fix:** Either move one desk of each pair onto the other's business date, or state in the Javadoc that cross-desk isolation is only asserted for the consistency pair.

### IN-02: Magic concurrency constants, and a Javadoc that overstates the fixture

**File:** `src/test/java/com/wfm/service/CallIsolation.java:26-28`, `src/test/java/com/wfm/service/ScheduleConsistencyRepairServiceTest.java:261-265`
**Issue:** The `THREADS = 8` and `RUNS_PER_THREAD = 10` constants are not justified against the machine's core count. On a 2-core CI runner they give little real overlap, which compounds WR-01. The consistency test Javadoc calls desk B "the 06:00 overnight swap", but `swapNeeded` never sets a 06:00 `dayStart` on the schedule. It only models post-midnight seats through `businessDate`.
**Fix:** Size the pool from `Runtime.getRuntime().availableProcessors()` (minimum 2) and reword the Javadoc to say "post-midnight seats".

---

_Reviewed: 2026-10-08_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

---

## Disposition (2026-10-08)

Kept as a separate file so the full-phase review in `24-REVIEW.md` and its ledger in
`24-REVIEW-DISPOSITION.md` stay intact (this incremental pass reuses the IDs WR-01/WR-02).

| ID | Disposition | Note |
|----|-------------|------|
| WR-01 | fixed | Fixtures are built before the latch; threads meet at a `CyclicBarrier` before every call. A clear-at-start shared-map mutant in `ShiftStartMixTargetService` (invisible to the successive half) failed 3 of 3 runs. |
| WR-02 | fixed | One overall deadline; the cause is unwrapped and the first `AssertionError` is rethrown; failing thread resets the barrier; message names desk, thread and iteration. |
| IN-01 | accepted | The consistency pair shares `DAY`; the envelope and start-mix pairs differ in desk shape, which is what a field leak would mix. |
| IN-02 | fixed | `THREADS` sized from `availableProcessors()` (min 4); consistency Javadoc no longer claims a 06:00 `dayStart`. |
