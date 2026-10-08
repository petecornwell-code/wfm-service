---
phase: "19"
slug: "daywindow-re-anchoring"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
block_on: high
created: "2026-10-01"
register_authored_at_plan_time: true
---

# Phase 19 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> Built in State B from the eight PLAN.md `<threat_model>` blocks; every mitigation below was
> re-verified against the implementation at L1 (grep) depth by `/gsd-secure-phase 19`.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| operator HTTP → `DeskService.setDayStart` | The only write path for a desk anchor. Unchanged by this phase; still refuses any value other than `00:00`. | `LocalTime` day-start, operator-supplied |
| operator HTTP → `ShiftTemplateService.validate` | The only write path for a shift-template envelope. Null and forward-interval checks preserved verbatim (D-11). | shift envelope start/end, operator-supplied |
| operator HTTP → `ShiftTemplateController` → `ShiftTemplateService.dayWindowFor(deskId)` | New internal call. `deskId` is the same already-validated `@PathVariable`; resolves via the tenant-scoped `findByIdAndTenantId`. | desk id (tenant-scoped) |
| operator HTTP → `FteUploadService` spreadsheet parse | Existing untrusted-file boundary. Only the receiver computing an interval changed; no new parsed field. | uploaded spreadsheet |
| solved `Schedule` → `schedule.day_start` column | New persisted value. Written only by `SolverService.buildSchedule` from an already tenant-scoped `Desk`. | desk anchor, internal |
| stored `Schedule` → `ScheduleConfig.dayStart` → solver / export paths | The anchor travels with the schedule it was solved under; originates from the `NOT NULL` column, never request input. | desk anchor, internal |
| `src/main/java` source tree → `MidnightTimeArithmeticGuardTest` scan | Build-time only, no runtime surface. | source text + committed allowlist |

No new external input surface, no new endpoint, no new authentication or authorisation decision, and
no new cryptographic or injection-relevant path. V2/V3/V4/V6 remain not-applicable; V5 Input
Validation is **preserved, not changed** — same checks, same messages, same order.

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-19-01 | Tampering | `schedule.day_start` write path | low | mitigate | `SolverService.buildSchedule` sole writer; V54 column is `TIME NOT NULL DEFAULT '00:00'` — **verified** | closed |
| T-19-02 | Information Disclosure | `ScheduleConfig.dayStart` as `@ProblemFactProperty` | low | accept | Value already returned by the Phase 18 desk-configuration endpoint; adds no reader | closed |
| T-19-03 | Denial of Service | `DayWindow.anchoredAt` allocation in constraint lambdas | low | accept | One immutable single-field wrapper per match; no unbounded allocation or retention | closed |
| T-19-04 | Tampering | `DayWindowTest.FrozenOracle` as sole record of pre-migration behaviour | low | mitigate | Committed wave 1 before any `DayWindow` edit; proven red on a single-character edit — **verified** | closed |
| T-19-05 | Elevation of Privilege | `ShiftTemplateService.dayWindowFor(UUID)` | medium | mitigate | Resolves via `deskRepository.findByIdAndTenantId(deskId, TenantContext.getTenantId())`, not a bare `findById` — **verified at `ShiftTemplateService.java:74`** | closed |
| T-19-06 | Information Disclosure | desk day start in a shift-template response | low | accept | Returns a `DayWindow`, not a `LocalTime`; no response field added | closed |
| T-19-07 | Tampering | duration-throw removal as accidental validation relaxation | medium | mitigate | `validate()`'s forward-interval refusal preserved and still on an **earlier line** than the duration read — **verified: refusal at `:249`, duration at `:253`** | closed |
| T-19-08 | Denial of Service | `ScheduleExportService` binding a window from a caller-influenced DTO | low | accept | Field populated from the `NOT NULL` persisted column, never request input | closed |
| T-19-09 | Tampering | a null anchor silently defaulting to midnight in an export | low | mitigate | `ScheduleExportService:54` passes the anchor straight to the factory with no null coalescing; `anchoredAt` is `requireNonNull` — **verified** | closed |
| T-19-10 | Tampering | an unlisted midnight anchor silently restoring pre-migration semantics | medium | mitigate | Third allowlist section + set-equality scan over `src/main/java`, failing on unlisted **and** stale entries — **verified: `staleEntries` logic present, guard 12/12 green** | closed |
| T-19-11 | Repudiation | the pending solver anchor becoming permanent by being forgotten | medium | mitigate | One named `private static final PENDING_DESK_ANCHOR`, javadoc'd with `SOLV-01` as owner of removal, carrying an allowlist entry — **verified** | closed |
| T-19-12 | Tampering | the guard degrading to a subset check | low | mitigate | Set equality, not containment; stale-entry direction proven red — **verified** | closed |
| T-19-13 | Tampering | the `00:00`-only gate removed while its javadoc is edited | **high** | mitigate | Gate intact at `DeskService.java:236-237` (`!dayStart.equals(LocalTime.MIDNIGHT)` → refusal), refusal message count == 1, `DeskServiceDayStartTest` **11/11 green** — **verified three ways** | closed |
| T-19-14 | Repudiation | a stale Phase 18 verification fingerprint hiding later source drift | medium | mitigate | `19-08-SUMMARY.md` records that `18-VERIFICATION.md`'s `covered_digest` is stale and that a deliberate refresh with a control is owed at milestone close — **verified** | closed |
| T-19-15 | Tampering | a future convenience overload reintroducing the midnight-implicit shape | medium | mitigate | `DayWindowTest.NoPublicStaticTakesABareSchedulingTime` reflectively asserts no midnight-implicit name is public and every public static takes the anchor first; proven red — **verified** | closed |
| T-19-16 | Tampering | removal of the `durationMinutes` throw as accidental validation relaxation | medium | mitigate | Same control as T-19-07; `anchoredIsForwardWithinDay` semantics unchanged; no duration call site catches the old exception | closed |
| T-19-17 | Tampering | a surviving midnight fallback replacing the deleted transitional form | medium | mitigate | **Partially met — see Accepted Risks R-19-01.** Every `covers`-related midnight literal is gone and both `SolverService` bindings read real anchors (`desk.getDayStart()`, `scheduleConfig.dayStart()`), but `ShiftBandPair.netHours()` retains a `@Deprecated` midnight delegate | closed — residual accepted (R-19-01) |
| T-19-18 | Tampering | `covers` diverging into two implementations | medium | mitigate | Exactly 2 declared forms remain, both routing to one body — **verified** | closed |

*Status: open · closed · open — below `high` threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `block_on: high` count toward `threats_open`*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-19-01 | T-19-17 | `ShiftBandPair.netHours()` retains `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` (javadoc + body, 2 occurrences), so plan 19-05's "zero midnight literals across five files" criterion is unmet by exactly those two lines. The method is `@Deprecated` and transitional; its 4 remaining production callers (`AgentShiftAssignment:250`, `ShiftLibraryGenerationService:741/781/976`) do not yet reach a desk anchor. The residual is **not hidden**: it carries an explicit entry in the D-07 two-directional allowlist (`src/test/resources/midnight-time-arithmetic.md:183,205`), so the set-equality guard tracks it and will fail if it is removed without updating the list. Disclosed in `19-05-SUMMARY.md` deviations. Severity `medium`, below the `high` block threshold. **Owner: Phase 20 (SOLV-01)** — the same work that retires `PENDING_DESK_ANCHOR` must give these callers a real desk anchor. | Pete Cornwell (orchestrated audit) | 2026-10-01 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-01 | 18 | 18 | 0 (at/above `high`) | `/gsd-secure-phase 19` (L1, orchestrator-verified) |

**Method.** State B — no prior SECURITY.md; register rebuilt from all eight PLAN.md `<threat_model>`
blocks (`register_authored_at_plan_time: true`). No `## Threat Flags` sections existed in any SUMMARY.
Each mitigation was re-verified against the implementation rather than taken from the plan text; the
three checks that carried the most weight were T-19-13's gate (three independent confirmations),
T-19-05's tenant-scoped lookup, and T-19-07's refusal-before-duration line ordering.

**Scope note.** No package-manager install occurs in this phase, so no supply-chain row (`T-19-SC`)
and no package-legitimacy checkpoint apply — matching `19-RESEARCH.md` § Package Legitimacy Audit.
The latent correctness defect this phase closes (silent negative durations, `false` from `isAfter` at
a midnight boundary) is a correctness defect, not a STRIDE category, and is deliberately not listed
as one.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed — no open threat at or above `block_on: high`
