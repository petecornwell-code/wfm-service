---
status: testing
phase: 14-shift-library-scheduling-mode
source: 14-01-SUMMARY.md, 14-02-SUMMARY.md, 14-03-SUMMARY.md, 14-04-SUMMARY.md, 14-05-SUMMARY.md, 14-06-SUMMARY.md
started: 2026-08-25T00:00:00Z
updated: 2026-08-26T15:15:00Z
---

## Current Test

number: 2
name: Coverage panel names specific uncovered demand windows (UI)
expected: |
  With seeded staffing demand and a partial shift library, the Coverage panel lists the
  specific uncovered `(date, timeslot)` windows by name — not a generic validation message.
awaiting: user response

## Tests

### 1. V39 migration applies cleanly to live Postgres
expected: Flyway migrates to version 39 without error; `\d shift_template` shows eleven columns and the `(tenant_id, desk_id, name, effective_from)` unique constraint; `SELECT DISTINCT scheduling_mode FROM desk;` returns only `SLOT`
result: pass
resolved_by: "fix(14) 9a98029 — V39 now declares valid_weekdays VARCHAR(7)"
retest: "Re-run end-to-end on live Postgres 18.4 after the fix. Staged a scratch DB to V38, seeded three desks, applied V39: all eleven columns present with valid_weekdays as `character varying(7)`, UNIQUE (tenant_id, desk_id, name, effective_from) present, all three pre-existing desks backfilled so `SELECT DISTINCT scheduling_mode FROM desk` returned only SLOT. Then booted the real application against a fresh empty DB and let Flyway drive: `Migrating schema \"public\" to version \"39 - add shift template and scheduling mode\"` -> `Successfully applied 38 migrations ... now at version v39`, Tomcat started, /actuator/health returned UP with db UP (so Hibernate ddl-auto=validate passed). Live round trip through the fixed column: POST /api/v1/desks returned schedulingMode SLOT; POST .../shift-templates with validWeekdays MON-FRI persisted and read back identically, with the raw column holding '1111100' as character varying, length 7. Incidentally confirmed the WR-03 fix — netHours came back as 7.50 at scale 2."
prior_result: issue
prior_reported: "Ran by Claude on live Postgres 18.4. The migration itself applies cleanly — Flyway logged `Migrating schema \"public\" to version \"39 - add shift template and scheduling mode\"` and `Successfully applied 38 migrations ... now at version v39`; `\\d shift_template` showed all eleven columns and the `shift_template_tenant_id_desk_id_name_effective_from_key` UNIQUE constraint; three pre-seeded desks all backfilled to `SLOT` and `SELECT DISTINCT scheduling_mode FROM desk` returned only `SLOT`. BUT the application then FAILS TO START: Hibernate `ddl-auto: validate` aborts with `Schema-validation: wrong column type encountered in column [valid_weekdays] in table [shift_template]; found [bpchar (Types#CHAR)], but expecting [varchar(7) (Types#VARCHAR)]`. V39 declares `valid_weekdays CHAR(7)` (Postgres bpchar) while `ShiftTemplate.validWeekdaysMask` is a String with `@Column(length = 7)` (varchar). Boot fails, `BUILD FAILED`, no server. This blocks tests 2-9."
prior_severity: blocker
verified_by: claude-automated
note: This is the phase's single most load-bearing unverified item. `src/test/resources/application-test.yml` sets `flyway.enabled: false` with `ddl-auto: create-drop`, so `./gradlew test` never executes V39 — a fully green 402-test suite says nothing about whether this migration applies. The SQL was reviewed line-by-line and the code reviewer independently confirmed the DDL and the `ShiftTemplate` entity mapping agree on every column, type, nullability and constraint, but syntactic soundness is not a confirmed `flyway migrate`.
note_outcome: The concern this note raised was justified, and the review claim it cites was wrong. The DDL and the entity mapping do NOT agree on `valid_weekdays` — CHAR(7) vs varchar(7). Because the test profile disables Flyway and uses H2 `create-drop`, Hibernate builds the test schema from the entity and the migration is never exercised, so no test in the 402-test suite could have caught this.

### 2. Coverage panel names specific uncovered demand windows
expected: With seeded staffing demand and a partial shift library, the Coverage panel lists the specific uncovered `(date, timeslot)` windows by name — not a generic validation message
result: [pending]

### 3. Mode switch is refused with the same named windows
expected: Clicking "Shift-scheduled" leaves the toggle on "Slot-scheduled" and shows the same named uncovered windows, with no duplicate error surface
result: pass
verified_by: claude-automated (API level)
evidence: "Live backend on Postgres. Seeded a desk, a Mon-Fri 09:00-17:00/60min timeslot grid, and Monday-only demand at 2 FTE per hour, then a deliberately partial library (Morning 09:00-13:00). GET /shift-library/validation reported exactly 4 uncovered windows (2026-09-07 13:00-14:00, 14:00-15:00, 15:00-16:00, 16:00-17:00). PUT /desks/{id}/scheduling-mode {mode:SHIFT} returned HTTP 400 VALIDATION_FAILED, message '4 demand window(s) have no covering shift template', with details[] carrying one field:coverage entry per window naming the SAME four windows verbatim, plus one field:contractedHours entry. Desk mode re-read as SLOT after the refusal. The report and the refusal agree exactly, which is the D-08 'can never disagree' claim."
ui_remaining: "That the panel renders these as the single error surface with no duplicate toast is still a visual check."
note: ⚠ The code review found a Critical bug that likely breaks this test — see `14-REVIEW.md`. `ShiftLibrary.tsx:355-372` drops the `demand` and `grid` ErrorDetail fields and unconditionally forces `hasLiveDemand: true`, so a refusal for "no staffing demand loaded" or a grid-misaligned template can render "✓ All staffing-demand windows are covered" immediately after being refused, with no toast. Test this case deliberately.

### 4. Adding covering templates makes the switch succeed
expected: After adding templates that cover the previously-uncovered windows, the same click succeeds and the toggle moves to "Shift-scheduled"
result: pass
verified_by: claude-automated (API level)
evidence: "Added Afternoon 13:00-17:00 (netHours 4.00) covering the 4 gaps, and seeded 3 agents with 4h Monday contracted hours so the weekday was satisfiable. Validation then returned uncoveredWindows [] and unsatisfiableWeekdays []. The identical PUT {mode:SHIFT} that had returned 400 now returned HTTP 200 with schedulingMode SHIFT."

### 5. SHIFT → SLOT switches back immediately, with no dialog
expected: Clicking "Slot-scheduled" switches back at once. No confirmation dialog appears — D-12 rejects one deliberately, citing audit I-3
result: pass
verified_by: claude-automated (API level)
evidence: "PUT {mode:SLOT} from SHIFT returned HTTP 200 -> SLOT with no gate. Confirmed the direction is genuinely unconditional: DeskService.switchSchedulingMode only calls requireShiftModeReady when target == SHIFT, so SHIFT->SLOT never validates. Frontend handleModeSwitch (ShiftLibrary.tsx:346) has no confirm()/dialog in either direction — both directions take the identical code path, per D-12."

### 6. Mode switch during a RUNNING solve returns a readable 409
expected: With a solve running for the desk, clicking either mode option shows a single-line 409 toast, the toggle does not move, and the in-flight solve is NOT stopped
result: pass
verified_by: claude-automated (API level)
evidence: "Started a real 60s solve (HTTP 202, status RUNNING). PUT {mode:SHIFT} mid-solve returned HTTP 409 CONFLICT, message 'This desk has a schedule currently solving. Wait for it to finish before changing scheduling mode.', details[] EMPTY (so it renders as a single-line toast, not a detail list). Re-read afterwards: schedule still status RUNNING (the refusal did not stop the solve) and desk still SLOT."
note_wording: "The expected text says 'clicking either mode option' 409s. At API level the same-mode call (SLOT->SLOT while already SLOT) returns 200, because DeskService.switchSchedulingMode early-returns on `desk.getSchedulingMode() == target` BEFORE the running-solve check. This is not reachable from the UI: handleModeSwitch (ShiftLibrary.tsx:347) guards on `desk.schedulingMode === target` and never issues the request. Substantive claim holds; noted so the divergence is on record rather than silently passed."

### 7. Desk Management shows mode as read-only
expected: The Scheduling Mode column shows the correct value per desk, with no control to change it there (D-14)
result: pass
verified_by: claude-automated (API + source inspection)
evidence: "API: DeskRequest has no schedulingMode field, and PUT /api/v1/desks/{id} with schedulingMode:SHIFT smuggled into the body returned the desk still SLOT — mode is unreachable from the desk-update path, changeable only via the dedicated PUT /{deskId}/scheduling-mode. GET /api/v1/desks exposes schedulingMode per desk, which is the read-only column's data source. Source: DeskManagement.tsx renders mode as a plain text <td> at BOTH line 114 (display row) and line 103 (edit row) — in the edit row name/description/hours are <input> elements while mode stays text, so there is no control to change it there even mid-edit."

### 8. Contracted-hours mismatch is advisory, not blocking
expected: A template whose net duration matches no agent's contracted hours shows the amber warning glyph with the correct tooltip and still saves successfully (D-06 — advisory on save, blocking only at the mode switch in the fatal case)
result: [pending]

### 9. Visual legibility with realistic data (6 backstop claims)
expected: Multiple eras of one template name read as legible eras rather than accidental duplicates (D-11); a long template name does not break the table or the input layout; a realistic count of uncovered windows stays readable; the advisory tooltip is legible
result: [pending]
note: These are the plan's own six `verification: backstop` truths. No frontend test framework exists in this codebase (`frontend/package.json` has no test script, no vitest/jest/testing-library), so these cannot be proven automatically by design, not by omission.

## Summary

total: 9
passed: 6
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps

<!-- Populated as issues are reported during /gsd-verify-work 14 -->

- gap_id: G-14-1
  truth: "V39 migration applies cleanly to live Postgres and the application starts against the migrated schema"
  status: resolved
  resolved_by: "fix(14) 9a98029"
  resolved_at: 2026-08-26
  reason: "User reported: migration applies and backfills correctly, but the app then fails to boot — Hibernate ddl-auto=validate rejects shift_template.valid_weekdays: V39 declares CHAR(7) (bpchar) while ShiftTemplate.validWeekdaysMask maps to varchar(7)."
  severity: blocker
  test: 1
  root_cause: "Type mismatch between V39__add_shift_template_and_scheduling_mode.sql (`valid_weekdays CHAR(7)`) and ShiftTemplate.validWeekdaysMask (`@Column(name = \"valid_weekdays\", nullable = false, length = 7)` on a String, which Hibernate maps to varchar(7)). Not caught by any test because src/test/resources/application-test.yml sets flyway.enabled=false and ddl-auto=create-drop against H2, so the test schema is generated from the entity and the migration SQL is never executed."
  artifacts:
    - path: "src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql"
      issue: "Declares valid_weekdays CHAR(7); Postgres reports bpchar, which fails Hibernate schema validation against the varchar(7) the entity expects"
    - path: "src/main/java/com/wfm/model/ShiftTemplate.java"
      issue: "validWeekdaysMask is a String with @Column(length = 7) — maps to varchar(7), not CHAR(7)"
  missing:
    - "Align the column type — change V39 to `valid_weekdays VARCHAR(7) NOT NULL` (V39 is unreleased, so editing it in place is viable), or annotate the entity with columnDefinition = \"char(7)\""
    - "Add coverage that executes the real migrations against a real Postgres (e.g. a Testcontainers-backed boot test) so migration-vs-entity drift fails the suite rather than surfacing at first startup"
  debug_session: ""
