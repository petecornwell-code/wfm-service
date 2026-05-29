# Schedule Builder Guide

This guide walks you through creating a schedule from scratch — from initial desk setup through to accepting a published schedule.

---

## Overview

Building a schedule has four stages:

1. **Setup** — configure your desk, define skills, and assign agents
2. **Demand** — tell the system how many staff you need per timeslot
3. **Preferences** — capture what agents want and any exceptions to their normal hours
4. **Solve** — run the optimiser, review the output, and accept or reject

Each stage persists immediately to the database. You can return to any earlier stage at any time before solving.

---

## Stage 1: Setup

### 1.1 Create or select a desk

A desk represents one team or queue (e.g. "Inbound Sales", "Technical Support"). All scheduling is done within a single desk.

- Go to **Desk Management** and create a new desk, or select an existing one from the Desk Selector.
- Set the **default contracted hours per day** for this desk. This becomes the fallback for any agent whose individual hours have not been set.

### 1.2 Define specializations

Specializations are the skills agents need to work in this desk (e.g. "Billing", "Tier-2 Tech", "Spanish Support").

- Go to **Specializations** and add one entry per skill.
- Assign a colour to each — this makes the schedule grid easier to read.
- Every agent must have at least one specialization before a solve can run.

> **At minimum you need one specialization.** If you do not distinguish skills, create a single "General" specialization and assign every agent to it.

### 1.3 Assign agents

- Go to **Desk Agents**.
- **Option A — BambooHR sync**: click **Refresh from BambooHR**. The system pulls employees whose BambooHR `project` field matches this desk's name, creates agent records, and imports their approved time-off. Newly added agents are given a default "Basic" specialization automatically.
- **Option B — manual assignment**: search the tenant agent pool and add agents individually.

For each agent you should then set:

| Field | Where to set it | Notes |
|---|---|---|
| Primary specialization | Desk Agents → agent row | The skill the solver favours when assigning this agent |
| Secondary specializations | Desk Agents → agent row | Backup skills the solver can use if needed |
| Contracted hours/day | Desk Agents → agent row | Leave blank to inherit the desk default |

---

## Stage 2: Define staffing demand

Staffing requirements tell the solver how many full-time equivalents (FTEs) you need covered per timeslot per specialization.

### 2.1 Choose your timeslot increment

Go to **Staffing Requirements** and generate timeslots for your target date range. You will be asked for:

- **Start date / end date** — the period you want to schedule (max 31 days per solve)
- **Daily coverage window** — the earliest and latest times the desk is open
- **Increment** — how long each timeslot is: **15**, **30**, or **60 minutes**

The increment you choose here must match the increment you use when you run the solve.

> Contracted hours per agent must be a whole multiple of the increment. If you use 15-minute slots, contracted hours must be set in 0.25-hour steps (e.g. 7.75 h, 8.0 h, 8.25 h).

### 2.2 Enter demand

For each timeslot, enter the number of FTEs required per specialization. Two methods are available:

**Direct entry** — type FTE values directly into the grid. Use this if you already have a staffing model or headcount plan.

**Erlang X calculation** — provide call-centre traffic data per timeslot and the system calculates the FTEs required to hit your service level target. Inputs include:

| Input | Description |
|---|---|
| Call volume | Expected calls arriving in this timeslot |
| Average handle time (AHT) | Seconds per call |
| Patience | Average time a caller will wait before abandoning |
| Retry rate | Fraction of abandoned callers who call back |
| Service level target | e.g. 80% of calls answered within 20 s |

The calculated FTE value is written back into the grid exactly as if you had typed it manually. You can edit it afterwards.

Demand is saved to the database as you enter it.

---

## Stage 3: Preferences and exceptions

This stage is optional but improves schedule quality and agent satisfaction.

### 3.1 Agent preferences

A preference tells the solver what an agent would like — it is a soft target, not a guarantee.

Go to **Agent Preferences** for the relevant agent. Two types exist:

| Type | What it means |
|---|---|
| **Standing preference** | A recurring default, set per day of week (e.g. "Mondays: prefer 9:00 start") |
| **Weekly preference** | A one-off override for a specific date (e.g. "25 Feb: prefer 10:00 start") |

When both exist for the same date, the weekly preference takes effect.

Preferences can be entered individually or bulk-uploaded from a spreadsheet.

### 3.2 Agent exceptions

An exception overrides an agent's contracted hours for one specific date (e.g. an agent works 4 h instead of 8 h on a given day for a medical appointment).

Go to **Agent Exceptions**, select the agent, and enter:

- **Date**
- **Contracted hours** for that day
- **Reason** (required)

> An agent cannot have both an exception and a day-off on the same date. The system will block this combination during solve validation.

### 3.3 Days off

Days off (annual leave, public holidays, sick leave) are read from BambooHR automatically after a refresh. They are visible in the agent's profile but cannot be edited here. If a day off is wrong, correct it in BambooHR and refresh.

---

## Stage 4: Solve, review, and accept

### 4.1 Configure the solve

Go to **Schedule Setup**. Fill in:

| Setting | Description |
|---|---|
| **Date range** | Must match the period you generated timeslots for |
| **Daily coverage window** | Start and end time each day |
| **Timeslot increment** | Must match the increment set in Stage 2 |
| **Break duration** | Length of each agent's break (must be a multiple of the increment) |
| **Break blocked hours** | Hours at the start and end of a shift where breaks are not allowed (default: 1 h each end) |
| **Minimum shift for break** | Agents with contracted hours at or below this threshold get no break (default: 4 h) |
| **Break start alignment** | Whether breaks must start on the hour, half-hour, or quarter-hour |
| **Break clustering threshold** | Maximum percentage of on-shift agents who can be on break in the same timeslot before a soft penalty applies (default: 20%) |
| **Over/under allocation limits** | Hard limits on how much total supply can exceed demand (default: 130%) or fall short (default: 70%) |
| **Solve time** | Optional override; default is 10 minutes total or 3 minutes without improvement |

Click **Solve**. The system runs pre-solve validation (see below) then starts the optimiser asynchronously. The page shows a live status and score.

#### Pre-solve validation

The system checks these conditions before starting. If any fail, you will see a specific error message:

- Timeslots exist and match the period and increment
- Every agent has at least one specialization
- Every specialization has at least one eligible agent
- Contracted hours for all agents are multiples of the increment
- At least one staffing requirement exists
- At least one agent is available (not off every day)
- No agent has both an exception and a day-off on the same date
- Break preferences comply with the break start alignment setting
- The daily coverage window is wide enough to fit agent shifts

Fix any flagged issues and re-submit.

### 4.2 While the solver runs

The solver status cycles through:

| Status | Meaning |
|---|---|
| `RUNNING` | Optimiser is working |
| `COMPLETED` | Solver finished within the time limit |
| `STOPPED` | Manually stopped by the user |
| `FAILED` | An error occurred |

You can click **Stop** at any time to take the best solution found so far.

The score displayed is a `Hard / Soft` pair:
- **Hard score 0** — no hard constraints violated; the schedule is feasible
- **Hard score < 0** — constraint violations exist; review before accepting
- **Soft score** — lower (more negative) means more preferences were overridden; this is normal

### 4.3 Review the output

Four views are available once the solve is not `RUNNING`:

**Staffing Summary** — predicted vs actual FTEs per day per specialization, shown as a coverage percentage. Look for days where coverage is significantly above or below 100%.

**Agent Schedule** — per-agent, per-day view showing assigned timeslots and break slot. Use this to check shifts look reasonable and breaks fall at sensible times.

**Preference Report** — shows which start-time and break-time preferences were honoured and which were overridden. A high override rate may indicate demand requirements are too tight for preferences to be met.

**Constraint Violations** — lists every violated constraint with the affected agent and timeslot. If the hard score is not 0, all violations are shown here. Common causes:

| Violation | Likely cause |
|---|---|
| Contracted hours over/under | Staffing demand is very mismatched to available agent hours |
| Specialization match | Demand exists for a specialization with no eligible agents on that day |
| Break blocked window | Break duration + blocked hours exceed available shift window |
| Bulk underallocation hard | Total demand significantly exceeds total contracted hours for the period |

You can filter all four views to a single day using the date filter.

### 4.4 Accept or reject

**Accept** — writes the schedule to the database. Any previously accepted schedule for this desk and overlapping date range is replaced. Once accepted, the schedule is available for export.

**Reject** — discards the schedule from memory. If the schedule has a non-zero hard score a confirmation is shown. Rejecting is non-destructive — your agents, preferences, and demand data are unchanged and you can solve again.

**Export** — downloads an Excel file with three tabs: Staffing Summary, Agent Schedule, and Preference Report. Available for accepted schedules.

---

## Adjusting constraints

If your solved schedules are consistently sub-optimal you can tune the 18 constraint weights in **Constraint Weights**. Each constraint can be:

- Increased in magnitude to make it more important
- Decreased or set to zero to relax it
- Moved from soft to hard to make it non-negotiable

The defaults are designed for a typical contact-centre environment. Common adjustments:

| Scenario | Adjustment |
|---|---|
| Preferences are frequently overridden and you want them honoured more strictly | Increase "Honour start time preference" and "Honour break time preference" weights |
| The solver is leaving many seats unfilled | Increase "Unassigned assignment" weight or loosen allocation hard limits |
| Too many breaks cluster at the same time | Increase "Break clustering" weight or lower the clustering threshold percentage |
| You want primary specialization to be strictly enforced | Promote "Prefer primary specialization" from soft to hard |

Changes to constraint weights apply to the next solve only; they do not retroactively affect accepted schedules.

---

## Practical tips

**Start with a short date range.** When configuring a new desk for the first time, run a 1-week solve first to validate your setup before committing to a full month.

**Check coverage before solving.** Sum your staffing requirements for a representative day and compare to the total contracted hours of available agents. If demand far exceeds supply, a feasible schedule is impossible regardless of solver settings.

**Contracted hours must be multiples of your increment.** A common error is setting contracted hours to 8.0 h with 15-minute slots — this is fine (8.0 = 32 × 15 min). But 7.5 h with 30-minute slots is also fine (7.5 = 15 × 30 min). 7.5 h with 15-minute slots is fine (7.5 = 30 × 15 min). What fails is a non-whole multiple, e.g. 7.75 h with 60-minute slots (7.75 × 60 = 465 min, not divisible by 60).

**BambooHR time-off is live.** Run a refresh close to the solve date to pick up the latest approved leave. Stale time-off data is a common source of incorrect schedules.

**One active solve per desk.** You cannot start a second solve while one is `RUNNING`. Stop the current solve first if you need to re-run with different settings.

**Non-accepted schedules live in memory only.** If the server restarts, any `RUNNING` or `COMPLETED` (but not yet accepted) schedule is lost. Accept schedules promptly or re-run after a restart.
