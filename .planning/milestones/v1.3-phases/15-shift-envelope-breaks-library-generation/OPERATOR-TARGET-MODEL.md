# Operator Target Model — Shift Scheduling

**Stated by the operator during Phase 15 UAT, 2026-09-01.** Verbatim:

> "Shifts that can be easily calculated by demand curve. Same constraints on breaks that
> non-shift solves have. 9 hours contiguous work (8 working hours + 1 break) no exceptions,
> try to place breaks in the middle of the shift. Try and use preferences. Continuity of
> agents working at or near the same times each day they work should be a thing."

This is **forward scope, not a Phase 15 defect list**. Phase 15 delivered the shift envelope,
break bands and library generation; this describes the model the operator actually wants shift
mode to obey. Most of it is not missing so much as **deliberately switched off** — which makes it
cheaper than it looks, and also means the switch-off decisions need revisiting rather than simply
reversing.

Every "current state" line below was verified against the code or the live desk on 2026-09-01.

---

## R1 — Shifts derivable from the demand curve

**Current state:** the capability EXISTS. `ShiftLibraryGenerationService` powers the "Suggested
Library" feature (UAT test 9, passing). It clusters demand, greedily covers it, and expands spans
by supply.

**Gap:** quality, not existence. G-15-23 records two defects found in the live suggestion:
1. duplicate templates — `greedyCover` legitimately picks two same-span candidates with different
   band offsets (D-02 self-cover), and multi-band expansion collapses them into an exact duplicate;
2. **band placement ignores the demand peak** — it proposed breaking at 11:00 on the 10:00-19:00
   weekend template, the busiest hour of the weekend (44 FTE Sat, 32 Sun).

Defect 2 is the same failure this session hit by hand: break OFFSET quality is not modelled
anywhere, by the generator or by validation.

**Needs:** dedupe after expansion; bias offsets away from peak hours (the demand curve is already
loaded in that method); and see R4, which the generator must also satisfy.

---

## R2 — Same break constraints as non-shift solves

**Current state:** SIX constraints are gated off in SHIFT mode via
`filtering(... cfg.schedulingMode() != SchedulingMode.SHIFT)`:

| Constraint | Line | Live weight |
|---|---|---|
| `exactlyOneBreak` | :218 | ofHard(100) |
| `breakDuration` | :275 | ofHard(10) |
| `breakBlockedWindow` | :308 | ofHard(10) |
| `breakStartAlignment` | :352 | ofSoft(10) |
| `honourPreferredStartTime` | :689 | **0/0** |
| `honourPreferredBreakTime` | :722 | **0/0** |

**Do not simply un-gate them.** Phase 15's own diagnosis warns twice that naive restoration
"fights the envelope model and could make under-supplied desks unsolvable", and the warning is
sound for the first two: in shift mode the BAND defines the break, so `exactlyOneBreak` and
`breakDuration` are satisfied structurally by band semantics and re-adding them duplicates a
guarantee the model already provides.

The other four are the real gap:
- `breakBlockedWindow` — **D5, already a known live hole.** Nothing stops a band sitting at an
  envelope edge, which is exactly the defect the operator hit this session (see R4).
- `breakStartAlignment` — grid alignment of break starts.
- both preference constraints — see R5.

**Needs:** re-express as band-level rules rather than seat-level ones. `breakBlockedWindow`
belongs in `ShiftTemplateService` band validation (save-time refusal) AND as a solver constraint.

---

## R3 — 9 hours contiguous: 8 worked + 1 break, no exceptions

The operator's strongest statement, and currently the most violated.

**Current state — THREE separate holes:**

1. **No contiguity constraint exists in SHIFT mode.** G-15-27 (blocker). Measured on accepted
   schedule 709fd8b4: 24 of 138 agent-days (17%) contained a non-break hole; some fragmented the
   day into three pieces. All 23 of 24 were on the one template carrying slack.
2. **Envelope length is not constrained to 9h.** Weekend Flex was 10:00-20:00 — a 10-hour envelope,
   9.0h net — and was holdable only because V44 bounded slack admits net hours ABOVE contracted.
   Under "9 hours, no exceptions" that template should never have been eligible.
3. **Bounded slack (V44) is the mechanism that breaks contiguity.** It was introduced to fix D1's
   zero-slack rigidity, and removed a contiguity guarantee that zero-slack had been providing BY
   ACCIDENT. This is the second time in this phase that an incidentally-held property was lost when
   its accidental guarantor was relaxed.

**Needs:** a hard contiguity constraint — an agent-day's worked slots form at most two contiguous
runs separated only by the assigned break. Slack must be spendable ONLY at the envelope boundary
(late start / early finish), never in the interior. With that in place, slack can be restored,
which matters because zero slack cost 5 hard points on the live desk (Run D -1 -> Run F -6).

**Interim lever, no code:** `shiftEnvelopeSlackSlots: 0` on SolveRequest restores contiguity by
construction — but it also makes any template whose net hours exceed contracted hours ineligible,
which removed Weekend Flex and 30 agent-days from the live solve.

---

## R4 — Breaks placed in the middle of the shift

**Current state:** NOTHING models break centrality — not the solver, not save-time validation, not
the generator. The system accepted a band at offset 0 (break in the FIRST hour of the envelope)
with no warning at all; that is a disguised late start producing an unbroken 8-hour day, and it is
what the operator observed.

**Needs:**
- save-time validation in `ShiftTemplateService` refusing (or at minimum warning on) a band in the
  first or last hour of the envelope — this is D5's missing enforcement point;
- a soft solver constraint biasing band choice toward central offsets;
- the generator (R1) must obey the same rule.

A workable definition: for a 9-hour envelope the break belongs at offset 180-300 (hours 4-6);
never offset 0, never the final hour.

---

## R5 — Use preferences

**Current state: doubly disabled.** Both preference constraints are gated off in SHIFT mode
(:689, :722) AND carry weight `0/0` on the live desk, so they are inert even in SLOT mode.
`breakClusteringWeight` is likewise `0/0`.

Preference data EXISTS — `src/main/resources/sample-data/preferences.xlsx` — and the preference
report already computes `actualBreakTime` / `breakTimeHonouredCount` KPIs, so the reporting half
is built against constraints that never fire.

**Needs:** un-gate both for SHIFT mode, then set non-zero weights. Note the reported KPIs are
currently measuring a preference system that is switched off, so any historical "preferences
honoured" figure for this desk is meaningless.

---

## R6 — Continuity of agents working similar times across days

**Current state: DOES NOT EXIST.** No constraint in `ScheduleConstraintProvider` references
consecutive days, previous-day assignment, shift stability or rotation. Nothing prevents an agent
holding Early on Monday, Late on Tuesday and Weekend Opening on Saturday.

This is the only requirement of the six that is genuinely new capability rather than something
disabled or half-built.

**Needs:** a new soft constraint over `AgentShiftAssignment` pairs for the same agent on nearby
dates, penalising divergence in shift start time. Design questions the operator must settle:
- Is it the same TEMPLATE, or merely a similar START TIME (the operator said "at or near")?
- Does it apply across a rest day, or only consecutive working days?
- How does it trade against coverage? Rigid stability on an under-supplied desk will fight the
  peak, and this desk is already short at the Saturday 11:00 peak (44 needed, 25 rostered).

---

## Sequencing note

R3's contiguity constraint should come FIRST. It is the operator's hardest requirement, it is a
live blocker, and it unblocks restoring slack — which R2 and R6 both benefit from, since both add
pressure that the solver needs routing freedom to absorb.

R6 should come LAST. It is new capability, it is the least specified, and adding a stability
pressure to a model that still permits split shifts would entrench the split shifts.

**Guard first, in every case.** G-15-22 stands: this desk has no automated guard on solver
behaviour, and a tuning change that regressed the live desk sevenfold shipped with 580 green
tests. Each item above changes solver behaviour. Add the benchmark-shaped test before, not after.
