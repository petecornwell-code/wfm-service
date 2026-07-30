# Phase 9: Agent Data Model Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-30
**Phase:** 9-agent-data-model-foundation
**Areas discussed:** Migration day mapping, Per-day resolution semantics, Name split heuristic, Per-day storage shape

---

## Migration day mapping (MDL-03, highest risk)

| Option | Description | Selected |
|--------|-------------|----------|
| All 7 days (Mon–Sun) | Write scalar to all 7 slots — exactly reproduces today's resolution; no regression | ✓ |
| Mon–Fri only | Assume standard workweek; risks weekend-working agents | |
| Derive from BambooHR field 4517 | Parse working days at migration; only ~24% parseable → regression for unparseable agents | |

**User's choice:** All 7 days (Mon–Sun)
**Notes:** Behaviour-preserving choice — the scalar already applies to any non-day-off date, so all-7-days reproduces `getEffectiveHours` exactly (Success Criterion 4).

### Follow-up: NULL-scalar agents

| Option | Description | Selected |
|--------|-------------|----------|
| Leave all 7 empty | Keep resolving via schedule-default fallback | ✓ |
| Backfill 8.00 to all 7 | Snapshot current default onto agent; diverges if schedule default changes | |

**User's choice:** Leave all 7 empty

---

## Per-day resolution semantics (MDL-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Absent = fallback, 0 = not worked | No record → schedule default; explicit 0 → not worked. Satisfies both migration and Phase 10 | ✓ |
| Any missing-or-0 = not worked | Breaks null-scalar migrated agents (zero worked days) | |
| Any missing-or-0 = fallback | Can't express "not worked" via hours; contradicts Phase 10 | |

**User's choice:** Absent = fallback, 0 = not worked
**Notes:** User first asked a clarifying question — "How is mandatory vs PTO work in this model?" Answered inline: `AgentDayOff` (MANDATORY from field 4517, or PTO from dated time-off) removes a date from solving entirely; `AgentException` overrides hours for a worked date (highest precedence); the scalar/per-day gives base hours. Per-day hours replace the scalar only; "not worked" becomes the union of MANDATORY/PTO day-offs and per-day `0`. Redundancy with field-4517 MANDATORY rows is intentional and left for Phase 11 to consolidate.

---

## Name split heuristic (MDL-01/03)

| Option | Description | Selected |
|--------|-------------|----------|
| First token / rest | First whitespace token → firstName, remainder → lastName | ✓ |
| All-but-last / last token | Last token → lastName, rest → firstName | |
| Don't re-split on refresh | Split once at migration only | |

**User's choice:** First token / rest
**Notes:** User initially clarified that separate first/last columns is the required outcome (MDL-01). Codebase check revealed BambooHR provides only a combined `displayName` (`BambooRefreshService:211`), so the split rule is reused on every refresh — not throwaway. First-token/rest fits "First Last" display names.

### Follow-up: API/DTO compatibility

| Option | Description | Selected |
|--------|-------------|----------|
| Add first/last, keep combined name | New fields + derived `name`; nothing breaks | ✓ |
| Replace name with first/last | Breaking change to API/export in a no-regression phase | |

**User's choice:** Add first/last, keep combined name

---

## Per-day storage shape (MDL-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Child table (agent_day_hours) | agent_id, day_of_week, hours; @ManyToOne to Agent; absent = no row, not-worked = 0.00. Consistent with existing conventions | ✓ |
| 7 nullable columns on agent | Simpler joins; widens agent table; less natural for Phase 10/11 writes | |

**User's choice:** Child table (agent_day_hours)
**Notes:** Matches existing AgentException/AgentDayOff/AgentPreference child-table pattern; naturally represents "absent" (no row) vs "not worked" (0.00 row), which D-04 requires.

---

## Claude's Discretion

- `day_of_week` representation (DayOfWeek enum vs smallint), FK/unique-constraint layout, `hours` precision/scale — planner/researcher, mirroring existing `contracted_hours_per_day` (precision 5, scale 2).
- Flyway version number and one-vs-split migration scripts.

## Deferred Ideas

- Consolidating per-day `0` vs MANDATORY `AgentDayOff` (field 4517) into a single working-days authority — Phase 11 (MRG).
- Spreadsheet population of per-day hours + first/last + day-off/PTO columns — Phase 10 (UPL).
- BambooHR↔spreadsheet per-field precedence and merge report — Phase 11 (MRG).
