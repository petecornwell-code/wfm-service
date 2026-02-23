# WFM Service — System Specification

## 1. Overview

WFM Service is a workforce management system that allocates **agents** to **timeslots** using constraint-based optimisation. The solver is powered by [Timefold](https://timefold.ai/) (Java). The application exposes a REST API via Spring Boot and persists state in PostgreSQL. Agent data is sourced from **BambooHR** via its REST API and synchronised into the local database.

The service is **multi-tenant**. Tenant identity and authentication are managed by an external **AI service platform** (a separate project, out of scope for this document). Every API request includes a `tenant_id` (`BIGINT`) provided by the platform. All data is isolated per tenant at the database level — see section 3.1.

### Assumptions

1. Agents work a contiguous block of hours each day (their "shift"), which includes a break.
2. Each agent has exactly one primary specialization and one or more secondary specializations.
3. The time period to be scheduled is made up of a contiguous sequence of timeslots.
4. The solver will be configured to solve for a maximum of five minutes per run.
5. All times are in a single tenant-local time zone. No time zone conversion or storage is performed. Multi-zone tenants are out of scope.

## 2. Tech Stack

| Layer | Technology |
|---|---|
| UI | React |
| API / Controllers | Spring Boot (Java 21+) |
| Business Logic / Services | Spring Boot |
| ORM | Hibernate (via Spring Data JPA) |
| Database | PostgreSQL |
| Solver | Timefold Solver (Java) |
| Agent Data Source | BambooHR REST API |

## 3. Architecture

```
                    ┌─────────────────────────────────────────────┐
┌───────────┐       │              Spring Boot                    │       ┌────────────┐
│           │       │                                             │       │            │
│   React   │◄─JSON─┤  Controller ─► Service ─► Repository       │◄─JPA──┤ PostgreSQL │
│           │       │                  │                          │       │            │
└───────────┘       │            Timefold Solver                  │       └────────────┘
                    │                  │                          │
                    │         BambooHR Client (sync)              │
                    └──────────────────┼──────────────────────────┘
                                       │
                                       ▼
                                ┌─────────────┐
                                │  BambooHR   │
                                │  REST API   │
                                └─────────────┘
```

The backend is organised into three packages mirroring the standard layered pattern:

- **`model`** — JPA entities and Timefold planning model annotations.
- **`service`** — Business logic, solver lifecycle management, and transaction orchestration.
- **`controller`** — REST endpoints that accept and return JSON.

### 3.1 Multi-Tenancy

All data is scoped to a tenant via a `tenant_id` column (`BIGINT`) present on every tenant-owned table. The value is assigned and supplied by the external AI service platform — WFM Service never generates tenant ids itself.

- **Inbound requests** — The platform authenticates each request and forwards the resolved `tenant_id` to WFM Service (e.g. via a request header or token claim). The exact mechanism is owned by the platform and is out of scope for this document.
- **Data isolation** — Every query filters by `tenant_id`. An entity created by one tenant is never visible to another.
- **Database strategy** — Shared schema, shared tables, discriminated by `tenant_id`. No per-tenant schemas or databases.

### 3.2 CORS

The React frontend is served from a different origin to the Spring Boot API. A global CORS configuration is registered via a `WebMvcConfigurer` bean to allow cross-origin requests.

| Setting | Value | Notes |
|---|---|---|
| Allowed origins | Configurable via `cors.allowed-origins` | Comma-separated list. Defaults to `http://localhost:3000` for local development. |
| Allowed methods | `GET, POST, PUT, DELETE, OPTIONS` | All methods used by the API. |
| Allowed headers | `*` | Permits any request header (including `Authorization` and custom tenant headers). |
| Exposed headers | `Content-Disposition` | Required for the spreadsheet export download (section 8.5). |
| Allow credentials | `true` | Enables cookies and authorization headers. |
| Max age | `3600` (seconds) | Browsers cache preflight responses for 1 hour. |

In production the allowed origins must be restricted to the actual frontend domain(s). The wildcard `*` must **not** be used when `allowCredentials` is `true`.

## 4. Solver Inputs

Each solve run is configured by a set of inputs that define the problem space. These inputs are provided by the user before the solver is invoked.

### 4.1 Timeslot Increment

All timeslots in a given solution share a uniform duration. Supported values:

| Increment | Example slots (8 am–9 am) |
|---|---|
| 15 minutes | 08:00–08:15, 08:15–08:30, 08:30–08:45, 08:45–09:00 |
| 30 minutes | 08:00–08:30, 08:30–09:00 |
| 60 minutes | 08:00–09:00 |

### 4.2 Time Range

The contiguous window of time to be covered, expressed as a start time and end time (e.g. 08:00–18:00). Timeslots are **generated** by subdividing this range into intervals of the configured increment. For example, 08:00–18:00 at 15-minute increments produces 40 timeslots per day.

### 4.3 Specializations

Each agent has one primary and one or more secondary specialization assignments:

- **Primary specialization** — the agent's main area of expertise (exactly one).
- **Secondary specializations** — additional areas the agent can cover (one or more).

The set of available specializations is an input (e.g. "Billing", "Technical Support", "Sales"). Each timeslot may require coverage from multiple specializations simultaneously; the solver prefers assigning agents whose primary specialization matches the need, but may fall back to any of their secondary specializations.

### 4.4 Staffing Demand

The number of agents required **per timeslot, per specialization**. Because call volumes vary throughout the day, staffing demand is expressed at timeslot granularity — not as a flat daily total. This allows peak periods to be staffed more heavily than quiet periods.

| Field | Example |
|---|---|
| Timeslot | Monday 09:00–09:15 |
| Specialization | Billing |
| Required agents | 8 |

Demand can be:

1. **Directly input** — the customer provides agent counts per timeslot per specialization.
2. **Calculated via Erlang X** — derived from forecasted call volume per interval, average handle time (AHT), caller patience (abandonment), retry rate, and target service level. Erlang X accounts for caller abandonment and redials, producing more accurate staffing numbers than Erlang C. The calculation is performed per timeslot before the solver is invoked.

### 4.5 Agent Preferences

Agents may submit scheduling preferences. These are **soft inputs** — the solver tries to honour them but will override them when staffing demands require it. Two preferences are supported:

- **Preferred start time** — the time the agent would like their first assignment to begin (e.g. 09:00). The solver attempts to avoid assigning the agent to timeslots before this time.
- **Preferred break time** — the time the agent would like a break (e.g. 12:00). The solver attempts to leave the agent unassigned during timeslots that overlap this time.

Preferences are optional. An agent with no submitted preferences is scheduled purely based on staffing demand and hard constraints.

#### Standing vs weekly preferences

Every preference record has a date and a boolean `isStanding` flag:

- **Standing preference** (`isStanding = true`) — applies as the default for every day the agent is scheduled. At most **one** preference per agent may be standing at any time. When a different preference is marked as standing, the previous standing preference is **deleted** (not merely toggled to `false`).
- **Weekly preference** (`isStanding = false`) — applies only to its specific date and **overrides** the standing preference for that day. A weekly preference and a standing preference **may coexist on the same date** — the weekly preference takes priority for that date while the standing preference continues to serve as the default for all other days.

When the solver resolves preferences for a given agent-day: if a weekly (non-standing) preference exists for that date, use it; otherwise fall back to the standing preference (if one exists); otherwise the agent has no preference for that day. Resolution is **per-record** — a weekly override replaces the standing preference entirely for that day (individual fields are not merged).

| Field | Example (standing) | Example (weekly override) |
|---|---|---|
| Agent | Jane Smith | Jane Smith |
| Date | 2026-02-23 | 2026-02-25 |
| Is standing | `true` | `false` |
| Preferred start | 09:00 | 10:00 |
| Preferred break | 12:30 | *(null — no break preference this day)* |

### 4.6 Break Rules

Configurable rules that govern when an agent may take a break. Each agent receives **exactly one break** per shift — a contiguous block of unassigned timeslots. An agent's **shift** on a given day is defined as the span from the start time of their earliest assignment to the end time of their latest assignment.

#### 4.6.1 Blocked window (hard)

By default, the first **1 hour** and last **1 hour** of an agent's shift are blocked — no part of a break may fall within these periods. The blocked duration is configurable per schedule and supports fractional hours (e.g. 0.5 for 30 minutes, 1.5 for 90 minutes).

Example: an agent's shift runs 08:00–17:00. Breaks are forbidden before 09:00 and after 16:00; the eligible break window is 09:00–16:00.

#### 4.6.2 Minimum shift duration (hard)

Breaks are only permitted if an agent's contracted hours **exceed** the threshold (strictly greater than). The default threshold is **4 hours**, configurable per schedule. An agent with exactly 4 hours (or fewer) of contracted work does **not** receive a break.

#### 4.6.3 Break duration (hard)

The break duration is configurable per schedule, expressed in minutes. It must be a **multiple of the schedule's timeslot increment** (section 4.1). Typical values are 30, 45, or 60 minutes. The solver assigns exactly this many contiguous unassigned timeslots as the agent's break.

| Schedule increment | Valid break durations (examples) |
|---|---|
| 15 minutes | 15, 30, 45, 60, 75, … |
| 30 minutes | 30, 60, 90, … |
| 60 minutes | 60, 120, … |

The default break duration is **60 minutes**. Pre-solve validation rejects a break duration that is not a multiple of the increment.

#### 4.6.4 Break start alignment (hard)

The break start time must align to a configured boundary:

| Setting | Allowed break starts (examples) |
|---|---|
| `ON_HOUR` | 10:00, 11:00, 12:00 |
| `ON_HALF_HOUR` | 10:00, 10:30, 11:00, 11:30 |
| `ON_QUARTER_HOUR` | 10:00, 10:15, 10:30, 10:45 |

The default is `ON_HALF_HOUR`. Preferred break times (section 4.5) are stored **without** alignment validation — an agent may submit any valid time. Alignment is checked at **solve time**: if an agent's effective preferred break time does not conform to the schedule's active alignment, the pre-solve validation (section 7.8) flags the offending preferences and the solve is blocked until they are corrected. In practice, the alignment setting rarely changes for a given tenant.

#### 4.6.5 Break clustering penalty (soft)

When too many agents take their break during the same timeslot, coverage suffers. A soft penalty is applied when the number of agents on break in a single timeslot exceeds a configurable threshold (expressed as a percentage of agents **assigned during that same timeslot**, default **20%**). The penalty scales linearly with the number of agents over the threshold — e.g. if the threshold allows 8 agents on break and 10 are on break, the penalty is 2 × the constraint weight.

### 4.7 Constraint Weights

Each constraint (section 6) has an associated **weight** that controls how much a violation affects the solver score. Weights are stored per tenant and loaded as part of the planning solution via Timefold's `@ConstraintConfiguration` mechanism.

Weights allow per-tenant customisation without changing constraint code:

- **Disable a constraint** — set its weight to zero.
- **Prioritise constraints** — give one soft constraint a weight of 10 and another a weight of 1.
- **Promote soft to hard (or vice versa)** — change a weight from `HardSoftScore.ofSoft(n)` to `HardSoftScore.ofHard(n)`.

The constraint table in section 6 documents the **default** level and weight for each constraint. A tenant's saved weights override these defaults at solve time.

### 4.8 Agent Days Off

Agents have designated days off that are synced from BambooHR as explicit dates. The solver must not assign an agent to any timeslot on a day off. Two types are supported:

- **Mandatory day off** — the agent's regular non-working days (the equivalent of weekends; typically two per week). These recur weekly but are stored as explicit dates per scheduling period.
- **PTO (Paid Time Off)** — approved leave days. Only pre-approved PTO is considered; sick days are out of scope and not modelled.

Both types have the same effect on the solver: the agent is **completely unavailable** for the day. The distinction is informational — it appears in the UI and agent schedule output so managers can see *why* an agent is absent.

Days off are **read-only** within WFM Service — they originate from BambooHR and are synced alongside agent data (see section 9).

### 4.9 Agent Exceptions

Some agents may need to work different hours on specific days due to personal circumstances. Rather than changing the agent's contracted hours permanently, a scheduler can create an **exception** for a specific agent on a specific date. This overrides the agent's normal contracted hours for that day only.

Common reasons for exceptions:

- **Childcare** — agent must leave early for school pickup.
- **Part-time study** — agent attends classes on certain days.
- **Training** — agent is only available for a shortened shift.
- **Phased return** — agent returning from leave on reduced hours.

Exceptions are created before the solver runs and are treated as hard inputs — the solver schedules the agent for exactly the overridden number of hours on that day. Each exception includes a free-text reason so that schedule reviewers can see why an agent has non-standard hours.

An exception must not conflict with a day off on the same date — an agent cannot be both unavailable and working reduced hours on the same day.

## 5. Domain Model

```mermaid
classDiagram
    direction LR

    class Specialization {
        +UUID id
        +long tenantId
        +String name
    }

    class Agent {
        +UUID id
        +long tenantId
        +String bamboohrId
        +String name
        +String email
        +String department
        +String jobTitle
        +BigDecimal contractedHoursPerDay
        +boolean active
        +OffsetDateTime lastSyncedAt
    }

    class Timeslot {
        +UUID id
        +long tenantId
        +LocalDate date
        +LocalTime startTime
        +LocalTime endTime
    }

    class StaffingRequirement {
        +UUID id
        +long tenantId
        +int requiredAgents
        +Source source
    }

    class AgentAssignment {
        <<Planning Entity>>
        +UUID id
        +long tenantId
    }

    class AgentPreference {
        +UUID id
        +long tenantId
        +LocalDate date
        +boolean isStanding
        +LocalTime preferredStartTime
        +LocalTime preferredBreakTime
    }

    class AgentDayOff {
        +UUID id
        +long tenantId
        +LocalDate date
        +DayOffType type
    }

    class AgentException {
        +UUID id
        +long tenantId
        +LocalDate date
        +BigDecimal contractedHoursOverride
        +String reason
    }

    class ConstraintWeights {
        <<ConstraintConfiguration>>
        +UUID id
        +long tenantId
        +HardSoftScore specMatchWeight
        +HardSoftScore noOverlapWeight
        +HardSoftScore exactlyOneBreakWeight
        +HardSoftScore breakDurationWeight
        +HardSoftScore breakBlockedWindowWeight
        +HardSoftScore breakAlignmentWeight
        +HardSoftScore preferPrimaryWeight
        +HardSoftScore honourStartTimeWeight
        +HardSoftScore honourBreakTimeWeight
        +HardSoftScore breakClusteringWeight
        +HardSoftScore contractedHoursWeight
        +HardSoftScore bulkOverallocationLimitWeight
        +HardSoftScore agentDayOffWeight
    }

    class Schedule {
        <<PlanningSolution>>
        +UUID id
        +long tenantId
        +int incrementMinutes
        +LocalTime startTime
        +LocalTime endTime
        +LocalDate periodStartDate
        +LocalDate periodEndDate
        +double breakBlockedHours
        +int breakDurationMinutes
        +int breakMinShiftHours
        +BreakAlignment breakStartAlignment
        +int breakClusterThresholdPct
        +BigDecimal defaultContractedHoursPerDay
        +int overallocationHardLimitPct
        +HardSoftScore score
        +ScheduleStatus status
    }

    Agent "1" --> "1" Specialization : primarySpecialization
    Agent "1" --> "1..*" Specialization : secondarySpecializations

    StaffingRequirement "* " --> "1" Timeslot
    StaffingRequirement "* " --> "1" Specialization

    AgentAssignment "* " --> "1" Timeslot
    AgentAssignment "* " --> "1" Specialization : requiredSpecialization
    AgentAssignment "* " ..> "1" Agent : «planning variable»

    AgentPreference "* " --> "1" Agent
    note for AgentPreference "Unique on (agent, date, isStanding).\nAt most one isStanding=true per agent."

    AgentDayOff "* " --> "1" Agent
    note for AgentDayOff "Unique on (agent, date).\nSynced from BambooHR."

    AgentException "* " --> "1" Agent
    note for AgentException "Unique on (agent, date).\nOverrides contracted hours for that day."

    Schedule "1" --> "1" ConstraintWeights : «@ConstraintConfigurationProvider»
    Schedule "1" *-- "* " Specialization : specializations
    Schedule "1" *-- "* " Agent : agents
    Schedule "1" *-- "* " Timeslot : timeslots
    Schedule "1" *-- "* " StaffingRequirement : staffingRequirements
    Schedule "1" *-- "* " AgentPreference : agentPreferences
    Schedule "1" *-- "* " AgentDayOff : agentDaysOff
    Schedule "1" *-- "* " AgentException : agentExceptions
    Schedule "1" *-- "* " AgentAssignment : assignments
```

### 5.1 Specialization

A reference entity representing a named area of expertise (e.g. "Billing", "Technical Support", "Sales"). The set of specializations is configured as a solver input.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenantId` | `long` | Tenant identifier (from platform) |
| `name` | `String` | Unique specialization name (unique per tenant) |

### 5.2 Agent

An agent is a person who can be assigned to work during one or more timeslots. Agent records are imported from BambooHR and treated as **read-only** within this system, except for specialization assignments which are managed locally.

**Specialization requirement:** Every active agent must have a primary specialization and at least one secondary specialization assigned before the agent can participate in a solve run. Freshly synced agents from BambooHR arrive without specializations — an administrator must assign them via the UI or API before scheduling. The solver will refuse to start if any active agent lacks specializations (see section 7.8).

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key (internal) |
| `tenantId` | `long` | Tenant identifier (from platform) |
| `bamboohrId` | `String` | BambooHR employee id (unique per tenant, external key) |
| `name` | `String` | Display name (from BambooHR) |
| `email` | `String` | Work email (from BambooHR) |
| `department` | `String` | Department (from BambooHR) |
| `jobTitle` | `String` | Job title (from BambooHR) |
| `primarySpecialization` | `Specialization` | Main area of expertise (managed locally) |
| `secondarySpecializations` | `List<Specialization>` | Additional areas the agent can cover (managed locally, one or more) |
| `contractedHoursPerDay` | `BigDecimal` | The agent's contracted daily working hours **excluding break time** (e.g. 8.0 for full-time, 4.0 for part-time). A full-time agent with 8.0 contracted hours and a 60-minute break works a 9-hour shift. Defaults to the tenant-level `defaultContractedHoursPerDay` (see section 5.10) and can be overridden per agent. Managed locally via the API. |
| `active` | `boolean` | Whether the employee is active in BambooHR |
| `lastSyncedAt` | `OffsetDateTime` | Timestamp of last successful sync |

### 5.3 Timeslot

A timeslot is a single time interval within the coverage window. Timeslots are **generated** from the configured time range and increment — they are not created manually. A timeslot is specialization-agnostic; multiple agents with different specializations may be needed in the same timeslot.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenantId` | `long` | Tenant identifier (from platform) |
| `date` | `LocalDate` | The day this slot belongs to |
| `startTime` | `LocalTime` | Start of the interval |
| `endTime` | `LocalTime` | End of the interval |

### 5.4 StaffingRequirement

Represents the demand for a given specialization in a given timeslot. There is one StaffingRequirement per timeslot/specialization combination. Each row directly states how many concurrent agents are needed, enabling non-uniform staffing across the day.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenantId` | `long` | Tenant identifier (from platform) |
| `timeslot` | `Timeslot` | The specific interval |
| `specialization` | `Specialization` | Which specialization is needed |
| `requiredAgents` | `int` | Number of concurrent agents needed |
| `source` | `enum(DIRECT, ERLANG_X)` | How the value was determined |

**Generating seats:** For each StaffingRequirement, the system creates `requiredAgents` AgentAssignment instances for that timeslot and specialization. This is a direct 1-to-N expansion — no averaging or division needed.

Example: Monday 09:00–09:15 needs 8 Billing agents and 3 Tech Support agents → the system generates **8 + 3 = 11 AgentAssignment** instances for that single timeslot. Across 40 timeslots in a day, the total seat count varies per slot based on the individual StaffingRequirement values.

### 5.5 AgentAssignment (Planning Entity)

The central Timefold planning entity. Each instance represents one **seat** — a need for one agent with a particular specialization in a particular timeslot. The solver decides which agent fills each seat.

Multiple AgentAssignment instances may reference the same timeslot, each for a different (or the same) specialization. This is how a single timeslot is staffed by several agents across multiple specializations.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenantId` | `long` | Tenant identifier (from platform) |
| `timeslot` | `Timeslot` | The time interval to fill |
| `requiredSpecialization` | `Specialization` | The specialization this seat demands |
| `agent` | `Agent` | **Planning variable** (not nullable) — assigned by the solver. Every seat must be filled; the solver will never leave a seat unassigned. |

### 5.6 AgentPreference

An agent's scheduling preferences. Each record is tied to a specific date. The `isStanding` flag marks one preference as the agent's default that applies to every day unless overridden. These are loaded as problem facts and referenced by soft constraints during solving.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenantId` | `long` | Tenant identifier (from platform) |
| `agent` | `Agent` | The agent expressing the preference |
| `date` | `LocalDate` | The day the preference was set for |
| `isStanding` | `boolean` | `true` = this preference applies as the default for every day. At most one per agent. Default `false`. |
| `preferredStartTime` | `LocalTime` | Desired start of first assignment (nullable) |
| `preferredBreakTime` | `LocalTime` | Desired break time (nullable) |

**Keys and uniqueness constraints:**

- **Primary key:** `id` (UUID) — surrogate key. All references to a preference record use this key.
- **Business uniqueness:** Unique on (`agent`, `date`, `isStanding`) — at most one standing and one non-standing preference record per agent per date. This allows a standing preference and a weekly override to coexist on the same date.
- **Standing uniqueness:** At most one `isStanding = true` per agent (across all dates) — enforced by application logic (and optionally by a partial unique index on (`agent`) where `is_standing = true`). When a new preference is marked as standing, the previous standing preference is deleted.

**Solver resolution:** When building the problem facts for a solve run, the service resolves each agent-day to a single **effective** preference: if a non-standing preference exists for that date, use it; otherwise use the standing preference (if one exists); otherwise the agent has no preference for that day. Resolution is per-record — the entire standing record is replaced, not merged field-by-field. The solver receives only the resolved effective preferences.

### 5.7 AgentDayOff

A day on which an agent is unavailable for scheduling. Days off are synced from BambooHR (see section 9) and treated as read-only within WFM Service.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenantId` | `long` | Tenant identifier (from platform) |
| `agent` | `Agent` | The unavailable agent |
| `date` | `LocalDate` | The day the agent is off |
| `type` | `enum(MANDATORY, PTO)` | Reason for the day off — `MANDATORY` for regular non-working days (e.g. weekends), `PTO` for approved leave |

**Uniqueness constraint:** Unique on (`agent`, `date`) — an agent has at most one day-off record per date.

**Solver usage:** When building the planning solution, the service loads all `AgentDayOff` records that fall within the schedule's period. These are included as problem facts and referenced by the "Agent day off" hard constraint (section 6).

### 5.8 AgentException

A per-agent, per-day override that allows an agent to work different contracted hours than their standard `contractedHoursPerDay` on a specific date. Exceptions are used to accommodate individual circumstances — for example, an agent who is a part-time student and can only work 4 hours on certain days, or an agent with childcare responsibilities requiring a shorter shift.

Exceptions are **pre-solve inputs** — they must be configured before the solver is run. The solver uses the exception's `contractedHoursOverride` in place of the agent's normal contracted hours for that day when evaluating the "Contracted hours" constraint (section 6).

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenantId` | `long` | Tenant identifier (from platform) |
| `agent` | `Agent` | The agent the exception applies to |
| `date` | `LocalDate` | The day the override applies to |
| `contractedHoursOverride` | `BigDecimal` | The number of working hours (excluding break time) for this agent on this day. Replaces the agent's `contractedHoursPerDay` for this date only. Must be positive. |
| `reason` | `String` | A free-text explanation of why the exception exists (e.g. "Childcare — school pickup", "Part-time student", "Training day — short shift"). Required. |

**Uniqueness constraint:** Unique on (`agent`, `date`) — an agent has at most one exception per date.

**Interaction with days off:** An exception and a day off must not coexist for the same agent on the same date — pre-solve validation (section 7.8) rejects this as contradictory.

**Solver resolution:** When building the planning solution, the service loads all `AgentException` records that fall within the schedule's period. For each agent-day, if an exception exists the solver uses `contractedHoursOverride`; otherwise it uses the agent's `contractedHoursPerDay` (or the schedule's `defaultContractedHoursPerDay` if not set). The pre-solve coverage window check (section 7.8) also accounts for exceptions — an agent with a 4-hour exception does not require a 9-hour window.

### 5.9 ConstraintWeights

A Timefold `@ConstraintConfiguration` class that holds a `@ConstraintWeight` field for every constraint defined in section 6. One row per tenant (identified by `tenantId`) so each tenant can tune solver behaviour independently.

| Field | Type | Default | Notes |
|---|---|---|---|
| `id` | `UUID` | — | Primary key |
| `tenantId` | `long` | — | Tenant identifier (from platform); unique — one row per tenant |
| `agentDayOffWeight` | `HardSoftScore` | `hard(1)` | Agent day off |
| `specMatchWeight` | `HardSoftScore` | `hard(1)` | Specialization match |
| `noOverlapWeight` | `HardSoftScore` | `hard(1)` | One assignment per timeslot |
| `exactlyOneBreakWeight` | `HardSoftScore` | `hard(1)` | Exactly one break per shift |
| `breakDurationWeight` | `HardSoftScore` | `hard(1)` | Break duration |
| `breakBlockedWindowWeight` | `HardSoftScore` | `hard(1)` | Break blocked window |
| `breakAlignmentWeight` | `HardSoftScore` | `hard(1)` | Break start alignment |
| `preferPrimaryWeight` | `HardSoftScore` | `soft(1)` | Prefer primary specialization |
| `honourStartTimeWeight` | `HardSoftScore` | `soft(1)` | Honour preferred start time |
| `honourBreakTimeWeight` | `HardSoftScore` | `soft(1)` | Honour preferred break time |
| `breakClusteringWeight` | `HardSoftScore` | `soft(2)` | Break clustering |
| `contractedHoursWeight` | `HardSoftScore` | `hard(1)` | Contracted hours (hard — every agent must work exactly their contracted hours) |
| `bulkOverallocationLimitWeight` | `HardSoftScore` | `hard(1)` | Bulk over-allocation limit (hard — total staffing hours must not exceed predicted demand by more than `overallocationHardLimitPct`, default 130%) |


The "One agent per seat" constraint is structural (enforced by the planning variable) and has no configurable weight.

### 5.10 Schedule (Planning Solution)

The top-level Timefold `@PlanningSolution` that aggregates all facts and planning entities for a single solve run.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `tenantId` | `long` | Tenant identifier (from platform) |
| `incrementMinutes` | `int` | 15, 30, or 60 |
| `startTime` | `LocalTime` | Coverage window start |
| `endTime` | `LocalTime` | Coverage window end |
| `periodStartDate` | `LocalDate` | First day of the schedule period |
| `periodEndDate` | `LocalDate` | Last day of the schedule period (inclusive). The period must be contiguous and can span any range of days (e.g. Mon–Fri, Mon–Thu, Sat–Sun, or a full Mon–Sun week). Timeslots are generated for every day from `periodStartDate` to `periodEndDate`. |
| `breakBlockedHours` | `double` | Hours blocked at the start and end of an agent's shift where breaks are forbidden (default 1.0). Fractional values are supported (e.g. 0.5 for 30 minutes). |
| `breakDurationMinutes` | `int` | Length of each agent's break in minutes (default 60). Must be a multiple of `incrementMinutes`. |
| `breakMinShiftHours` | `int` | Contracted hours must strictly exceed this threshold for a break to be assigned (default 4). An agent with exactly this many hours or fewer gets no break. |
| `breakStartAlignment` | `enum(ON_HOUR, ON_HALF_HOUR, ON_QUARTER_HOUR)` | Required alignment for break start times (default `ON_HALF_HOUR`) |
| `breakClusterThresholdPct` | `int` | Max percentage of on-shift agents on break per timeslot before soft penalty applies (default 20) |
| `defaultContractedHoursPerDay` | `BigDecimal` | Tenant-level default contracted daily hours excluding break time (default 8.0). Applied to any agent whose `contractedHoursPerDay` is not explicitly set. |
| `overallocationHardLimitPct` | `int` | Maximum percentage by which total assigned staffing hours across all agents may exceed total predicted demand hours before triggering a hard constraint violation (default 130) |
| `constraintWeights` | `ConstraintWeights` | `@ConstraintConfigurationProvider` — per-tenant weights applied at solve time |
| `specializations` | `List<Specialization>` | Problem facts |
| `agents` | `List<Agent>` | Problem facts — **only active agents with specializations assigned** are loaded (inactive agents are excluded at input time, not by constraint) |
| `staffingRequirements` | `List<StaffingRequirement>` | Problem facts |
| `agentPreferences` | `List<AgentPreference>` | Problem facts |
| `agentDaysOff` | `List<AgentDayOff>` | Problem facts — days off within the schedule period |
| `agentExceptions` | `List<AgentException>` | Problem facts — contracted hours overrides within the schedule period (section 5.8) |
| `timeslots` | `List<Timeslot>` | Generated problem facts |
| `assignments` | `List<AgentAssignment>` | Planning entities |
| `score` | `HardSoftScore` | Populated by solver |
| `status` | `enum(RUNNING, COMPLETED, STOPPED, ACCEPTED)` | Current solver/lifecycle status (see lifecycle rules below) |

**Schedule lifecycle.** A schedule progresses through the following states:

1. **`RUNNING`** — the solver is actively working. The schedule can be stopped (`PUT /schedules/{id}/stop`) but not accepted or rejected.
2. **`COMPLETED`** — the solver has finished (or was stopped). The scheduler reviews the results. The schedule can be **accepted** or **rejected**.
3. **`STOPPED`** — the solver was terminated early. Treated the same as `COMPLETED` for accept/reject purposes.
4. **`ACCEPTED`** — the scheduler has accepted this schedule as the active schedule for its period. At most **one** schedule may be accepted per overlapping date range per tenant. Accepting a new schedule for the same period automatically replaces the previously accepted one (the old schedule is deleted).

**Rejection and persistence:** Rejecting a schedule **deletes** it and all its associated data (timeslots, assignments, staffing requirements generated for the solve). Rejected schedules are not retained. Only accepted schedules are persisted long-term.

**Replacement:** If the solver is re-run for the same period and the new schedule is accepted, it replaces the previously accepted schedule — the old accepted schedule is deleted. There is no archive of superseded schedules.

## 6. Constraints

Constraints are defined in a `ConstraintProvider` implementation. The **Level** column shows the default; per-tenant `ConstraintWeights` (section 5.9) can override levels and magnitudes at solve time.

| Constraint | Default Level | Description |
|---|---|---|
| Agent day off | Hard | An agent must not be assigned to any timeslot on a day they have a day off (mandatory or PTO). |
| Specialization match | Hard | An agent's primary specialization or one of their secondary specializations must match the assignment's required specialization. |
| One assignment per timeslot | Hard | An agent cannot be assigned to more than one seat in the same timeslot. Since timeslots are non-overlapping by construction (section 4.2), this reduces to: at most one seat per agent per timeslot. |
| One agent per seat | Hard | Each AgentAssignment (seat) is filled by exactly one agent (enforced by the planning variable). |
| Exactly one break | Hard | An agent whose contracted hours **strictly exceed** the minimum shift threshold (`breakMinShiftHours`, default 4) must have exactly one contiguous break of the configured duration. An agent whose contracted hours are equal to or less than the threshold must have **no break** — their shift consists entirely of assigned work. |
| Break duration | Hard | An agent's break must be exactly the configured number of contiguous timeslots (`breakDurationMinutes / incrementMinutes`). |
| Break blocked window | Hard | No part of an agent's break may fall within the first or last N hours of their shift (configurable, default 1.0 hour, fractional values supported). The entire break must be contained within the eligible window between the blocked periods. |
| Break start alignment | Hard | A break must start on a timeslot boundary that matches the configured alignment (hour, half-hour, or quarter-hour). |
| Prefer primary specialization | Soft | Prefer assigning agents to seats matching their primary specialization over any of their secondary specializations. |
| Honour preferred start time | Soft | Penalise assigning an agent to a timeslot that starts before their preferred start time on that day. |
| Honour preferred break time | Soft | Penalise assigning an agent to a timeslot that overlaps their preferred break time on that day. |
| Break clustering | Soft | Penalise when the number of agents on break in a single timeslot exceeds the configured threshold percentage of agents **assigned during that same timeslot** (not the whole day). Penalty scales linearly with the number of agents over the threshold. |
| Contracted hours | Hard | Every agent must be assigned exactly their contracted hours per day (`contractedHoursPerDay`, or the schedule's `defaultContractedHoursPerDay` if not set). Contracted hours count **assigned (non-break) time only** — break time is additional. For example, an agent with 8.0 contracted hours and a 60-minute break has a 9-hour shift (8 hours working + 1 hour break). The solver must not leave an agent with fewer or more assigned hours than their contract specifies. |
| Bulk over-allocation limit | Hard | The total assigned staffing hours across all agents for the schedule period must not exceed the total predicted demand hours (derived from staffing requirements) by more than the configured `overallocationHardLimitPct` (default 130%). For example, if staffing requirements predict 200 total hours of demand, the solver must not assign more than 460 total hours across all agents. |


## 7. API

All endpoints are served under the base path `/api/v1`. Every request is scoped to a single tenant — the `tenant_id` is extracted from the authenticated context provided by the AI service platform (see section 3.1). Responses only include data belonging to the requesting tenant.

**Pagination.** List endpoints that can return unbounded or large result sets support cursor-based pagination via the following query parameters and response envelope:

| Query parameter | Type | Default | Description |
|---|---|---|---|
| `limit` | `int` | `50` | Maximum number of items to return (1–200). |
| `cursor` | `String` | *(none)* | Opaque cursor returned by a previous response. When omitted the server returns the first page. |

Paginated responses use a standard envelope:

```json
{
  "data": [ ... ],
  "nextCursor": "eyJpZCI6MTAwfQ",
  "hasMore": true
}
```

- `data` — array of items for the current page.
- `nextCursor` — opaque cursor to pass as the `cursor` query parameter to fetch the next page. `null` when there are no more results.
- `hasMore` — `true` if additional pages exist beyond this one.

Endpoints with small, bounded result sets (e.g. per-agent filtered by date range) return a plain JSON array and do not use the pagination envelope.

**Error responses.** All endpoints use a standard error envelope for non-2xx responses:

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Human-readable summary of what went wrong.",
    "details": [
      {
        "field": "breakDurationMinutes",
        "message": "Must be a positive multiple of incrementMinutes (15).",
        "value": "25"
      }
    ]
  }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `error.code` | `String` | Yes | A stable, machine-readable error code. Clients should switch on this value rather than parsing `message`. See table below for defined codes. |
| `error.message` | `String` | Yes | A human-readable description of the error. Not guaranteed to be stable across versions. |
| `error.details` | `Array` | No | Optional list of field-level or item-level errors. Primarily used for validation failures. |
| `error.details[].field` | `String` | No | The request field or entity that caused the error (e.g. `"breakDurationMinutes"`, `"agent.primarySpecialization"`). |
| `error.details[].message` | `String` | Yes | Human-readable explanation of the specific issue. |
| `error.details[].value` | `String` | No | The rejected value, if applicable. |

**Standard error codes and HTTP status mappings:**

| HTTP Status | Error Code | When Used |
|---|---|---|
| `400` | `VALIDATION_FAILED` | Request body or query parameters fail validation (e.g. missing required fields, invalid values, pre-solve validation failures). The `details` array lists each failing check. |
| `404` | `NOT_FOUND` | The requested entity does not exist or does not belong to the requesting tenant. |
| `409` | `CONFLICT` | The request conflicts with current state (e.g. a solve is already running for the tenant). |
| `422` | `UNPROCESSABLE_ENTITY` | The request is syntactically valid but semantically invalid (e.g. a staffing requirement references a non-existent specialization). |
| `500` | `INTERNAL_ERROR` | An unexpected server error occurred. The `message` field contains a generic description; details are logged server-side. |

### 7.1 Agents

Agent records originate from BambooHR. The API is read-only except for local specialization assignments and contracted hours.

| Method | Path | Description |
|---|---|---|
| `GET` | `/agents` | List agents. Paginated. Optional query parameter `search` filters by name (case-insensitive substring match). |
| `GET` | `/agents/{id}` | Get agent by id |
| `PUT` | `/agents/{id}/specializations` | Set primary and secondary specializations for an agent |
| `PUT` | `/agents/{id}/contracted-hours` | Set the agent's contracted hours per day. Accepts `{ "contractedHoursPerDay": 8.0 }`. If not set, the tenant-level default is used. |
| `POST` | `/agents/sync` | Trigger an on-demand sync from BambooHR |

### 7.2 Agent Days Off

Days off are synced from BambooHR (section 9) and are read-only.

| Method | Path | Description |
|---|---|---|
| `GET` | `/agents/{id}/days-off` | List days off for an agent. Optionally filtered by date range via query parameters (`from`, `to`). Returns each record with `date` and `type` (MANDATORY or PTO). |
| `GET` | `/days-off` | List all agent days off, optionally filtered by date range (`from`, `to`). Paginated. Useful for the schedule setup page to show availability across all agents for a given period. |

### 7.3 Specializations

| Method | Path | Description |
|---|---|---|
| `GET` | `/specializations` | List all specializations |
| `POST` | `/specializations` | Create specialization |
| `DELETE` | `/specializations/{id}` | Delete specialization |

### 7.4 Agent Preferences

| Method | Path | Description |
|---|---|---|
| `GET` | `/agents/{id}/preferences` | List **raw** preference records for an agent. Optionally filtered by date range via query parameters. Always includes the standing preference (if one exists) alongside any date-specific preferences in the range. Each record includes `isStanding`. The client is responsible for computing the effective preference per day (standing-vs-weekly resolution described in section 5.6). |
| `PUT` | `/agents/{id}/preferences` | Create or update preferences for an agent (batch). Each entry includes `date`, `preferredStartTime`, `preferredBreakTime`, and `isStanding`. If `isStanding` is set to `true` on a record, the server sets the previous standing preference (if any) to `false`. |
| `DELETE` | `/agents/{id}/preferences/{date}` | Delete a specific day's preference. If the deleted preference was standing, the agent will have no standing preference until one is set. |

### 7.5 Agent Exceptions

Exceptions allow an agent's contracted hours to be overridden on specific dates, with a mandatory reason (section 5.8).

| Method | Path | Description |
|---|---|---|
| `GET` | `/agents/{id}/exceptions` | List exception records for an agent. Optionally filtered by date range via query parameters (`from`, `to`). Returns each record with `date`, `contractedHoursOverride`, and `reason`. |
| `PUT` | `/agents/{id}/exceptions` | Create or update exceptions for an agent (batch). Each entry includes `date`, `contractedHoursOverride`, and `reason`. Existing exceptions for the same agent and date are replaced. Returns `400` (error code `VALIDATION_FAILED`) if an exception conflicts with a day off on the same date. |
| `DELETE` | `/agents/{id}/exceptions/{date}` | Delete the exception for the specified date. The agent reverts to their standard contracted hours for that day. |

### 7.6 Constraint Weights

| Method | Path | Description |
|---|---|---|
| `GET` | `/constraint-weights` | Get the current tenant's constraint weights |
| `PUT` | `/constraint-weights` | Update constraint weights (partial updates allowed; omitted fields keep defaults) |

### 7.7 Staffing Requirements

| Method | Path | Description |
|---|---|---|
| `GET` | `/staffing-requirements` | List staffing requirements. Paginated. Optional query parameters `from` and `to` filter by date range. |
| `POST` | `/staffing-requirements` | Create or replace requirements for a schedule period. The payload contains the complete set of requirements for the specified date range — any existing requirements for that range not present in the payload are **deleted**. This is a full replace, not a merge. Returns `400` (error code `VALIDATION_FAILED`) if any referenced timeslot or specialization does not exist. |
| `POST` | `/staffing-requirements/erlang-x` | Calculate per-timeslot requirements from Erlang X inputs (call volume forecast, AHT, patience, retry rate, service level) |

### 7.8 Solver

| Method | Path | Description |
|---|---|---|
| `POST` | `/schedules/solve` | Start a solve run (async). Returns schedule id. Request body contains schedule configuration (see below). |
| `GET` | `/schedules` | List schedules. Paginated. Returns summary records (id, period, status, score, feasibility, creation timestamp) without the full output views. Used by the "Past schedules list" in the Schedule Setup page. |
| `GET` | `/schedules/{id}` | Get schedule with output views: staffing summary, agent schedule, preference report, and constraint violations (section 8). |
| `PUT` | `/schedules/{id}/stop` | Terminate a running solve early. |
| `PUT` | `/schedules/{id}/accept` | Accept this schedule as the active schedule for its period. Only allowed when status is `COMPLETED` or `STOPPED` — returns `409 Conflict` (error code `CONFLICT`) otherwise. If another accepted schedule exists for an overlapping date range, it is deleted and replaced by this one. Sets status to `ACCEPTED`. Returns `200` with the updated schedule. |
| `PUT` | `/schedules/{id}/reject` | Reject and delete this schedule. Only allowed when status is `COMPLETED` or `STOPPED` — returns `409 Conflict` (error code `CONFLICT`) otherwise. The schedule and all its associated data (timeslots, assignments) are permanently deleted. Returns `204 No Content`. |
| `GET` | `/schedules/{id}/export` | Download schedule as a multi-tab `.xlsx` spreadsheet (section 8.5). |

**Request body for `POST /schedules/solve`:**

```json
{
  "periodStartDate": "2026-02-23",
  "periodEndDate": "2026-02-27",
  "startTime": "08:00",
  "endTime": "18:00",
  "incrementMinutes": 15,
  "breakBlockedHours": 1.0,
  "breakDurationMinutes": 60,
  "breakMinShiftHours": 4,
  "breakStartAlignment": "ON_HALF_HOUR",
  "breakClusterThresholdPct": 20,
  "defaultContractedHoursPerDay": 8.0,
  "overallocationHardLimitPct": 130
}
```

All fields with defaults (section 5.10) are optional in the request — omitted fields use their default values. The server assembles the `Schedule` by loading agents, specializations, staffing requirements, preferences, and days off from the database for the specified date range.

**Concurrent solves:** Only one solve may be running per tenant at a time. If a tenant attempts to start a solve while another is already running, the endpoint returns `409 Conflict` using the standard error envelope (error code `CONFLICT`). To start a new solve the running one must first be stopped via `PUT /schedules/{id}/stop`.

**Transaction scopes.** The solve lifecycle is divided into two transactional phases, each with independent rollback semantics:

1. **Pre-solve transaction** — covers everything from receiving the `POST /schedules/solve` request through to the point where the solver is ready to start. This includes: validating the request, loading agents/specializations/preferences/days off/exceptions from the database, generating timeslots, expanding staffing requirements into `AgentAssignment` entities, creating the `Schedule` record with status `RUNNING`, and persisting all generated entities. This entire phase executes within a **single database transaction**. If any step fails (validation error, database constraint violation, unexpected exception), the transaction is **rolled back** — no Schedule, no timeslots, no assignments are persisted. The client receives an error response and the system state is unchanged.

2. **Solve transaction** — once the pre-solve transaction commits successfully, the solver is started asynchronously on a separate thread. The solver operates on the in-memory planning solution and periodically updates the best score. When the solver terminates (either by completing, reaching the time limit, or being stopped via the API), a **second transaction** commits the final assignments and score back to the database and sets the status to `COMPLETED` or `STOPPED`. If this commit fails, the schedule remains in `RUNNING` status and is cleaned up by the stale-run recovery mechanism (see below).

**Failure recovery.** If the server crashes or restarts while a solve is in progress, the schedule will be left in `RUNNING` status with no active solver thread. On application startup, the service queries for any schedules with status `RUNNING` and transitions them to `STOPPED` with a null score — the solver result is lost and the user must re-run the solve. This prevents orphaned `RUNNING` schedules from permanently blocking the concurrent-solve check.

**Solver time limit.** Each solve run is subject to a configurable time limit (default: 5 minutes, set via `solver.time-limit` application property). When the limit is reached, Timefold terminates gracefully and the best solution found so far is persisted. This is functionally equivalent to the user calling `PUT /schedules/{id}/stop` — the schedule transitions to `COMPLETED` with the best-effort result.

**Pre-solve validation:** `POST /schedules/solve` performs the following validation before starting the solver. If any check fails, the endpoint returns `400 Bad Request` using the standard error envelope (error code `VALIDATION_FAILED`). Each failing check is represented as an entry in the `details` array so that the client can display all issues at once:

- Every active agent must have a primary specialization and at least one secondary specialization assigned. *(`details[].field`: `"agent.specializations"`, with the affected agent identified.)*
- At least one staffing requirement must exist for the target period.
- At least one active agent must be available (i.e. not on a day off for every day of the period).
- `breakDurationMinutes` must be a positive multiple of `incrementMinutes`. *(`details[].field`: `"breakDurationMinutes"`.)*
- Every agent with an effective preferred break time for a day in the schedule period must have that time conform to the schedule's `breakStartAlignment`. Non-conforming preferences are listed in the `details` array (one entry per agent-day) so they can be corrected.
- The coverage window (`timeRangeEnd − timeRangeStart`) must be at least as long as each agent's **effective** contracted hours (accounting for exceptions) plus the configured break duration where applicable (since contracted hours exclude break time). Specifically, for every active agent-day whose shift would include a break (effective contracted hours **strictly greater than** `breakMinShiftHours`), the check verifies that `coverageWindowHours ≥ effectiveContractedHours + (breakDurationMinutes / 60)`. For agents at or below the threshold (no break), the check verifies `coverageWindowHours ≥ effectiveContractedHours`. For example, an agent with 8.0 contracted hours and a 60-minute break requires a coverage window of at least 9 hours; an agent with a 4-hour exception requires only a 4-hour window (no break). Agents failing this check are listed in the `details` array.
- No agent may have both an exception and a day off on the same date within the schedule period. *(`details[].field`: `"agentException.date"`, with the conflicting agent and date identified.)*

## 8. Schedule Output

When a solve completes (or while in progress), `GET /schedules/{id}` returns the full schedule along with derived output views. These views are also available as a multi-tab spreadsheet export.

### 8.1 Staffing Summary

A per-day comparison of **predicted** staffing hours (derived from staffing requirements) versus **actual** staffing hours (derived from agent assignments).

| Field | Type | Description |
|---|---|---|
| `date` | `LocalDate` | Day |
| `specialization` | `String` | Specialization name |
| `predictedHours` | `BigDecimal` | Sum of `requiredAgents × incrementMinutes / 60` across all timeslots for that day and specialization |
| `actualHours` | `BigDecimal` | Sum of `(assigned agents) × incrementMinutes / 60` across all timeslots for that day and specialization |
| `deltaHours` | `BigDecimal` | `actualHours − predictedHours` (positive = overstaffed, negative = understaffed) |
| `coveragePct` | `BigDecimal` | `actualHours / predictedHours × 100` |

A `totals` row per day aggregates across all specializations. A grand-total row aggregates across all days.

### 8.2 Agent Schedule

A per-agent, per-day view of every assignment, the specialization used, and break periods.

Each entry in the list represents one agent-day:

| Field | Type | Description |
|---|---|---|
| `agent` | `Agent` | The assigned agent |
| `date` | `LocalDate` | Day |
| `shiftStart` | `LocalTime` | Start time of earliest assignment |
| `shiftEnd` | `LocalTime` | End time of latest assignment |
| `totalHours` | `BigDecimal` | Total assigned hours (excluding breaks) |
| `assignments` | `List<SlotDetail>` | Ordered list of timeslot assignments |
| `breaks` | `List<BreakDetail>` | Gaps within the shift where the agent is unassigned |

**SlotDetail:**

| Field | Type | Description |
|---|---|---|
| `timeslot` | `Timeslot` | The assigned timeslot |
| `requiredSpecialization` | `String` | Specialization the seat demanded |
| `matchType` | `enum(PRIMARY, SECONDARY)` | Whether the agent's primary or secondary specialization was used |

**BreakDetail:**

| Field | Type | Description |
|---|---|---|
| `startTime` | `LocalTime` | Break start |
| `endTime` | `LocalTime` | Break end |
| `durationMinutes` | `int` | Break length |

### 8.3 Preference Report

A per-agent, per-day report showing which preferences were honoured and which were overridden. Preference values shown are the **effective (resolved)** values after standing-vs-weekly resolution (see section 5.6), not the raw stored records.

| Field | Type | Description |
|---|---|---|
| `agent` | `Agent` | The agent |
| `date` | `LocalDate` | Day |
| `preferenceSource` | `enum(WEEKLY, STANDING, NONE)` | Whether the effective preference came from a date-specific record, the standing default, or no preference existed |
| `preferredStartTime` | `LocalTime` | Effective preferred start time (null if none) |
| `actualStartTime` | `LocalTime` | Earliest assignment start time |
| `startTimeHonoured` | `boolean` | `true` if `actualStartTime >= preferredStartTime` (or no preference was set) |
| `preferredBreakTime` | `LocalTime` | Effective preferred break time (null if none) |
| `actualBreakTime` | `LocalTime` | Start of actual break closest to the preferred time (null if no break) |
| `breakTimeHonoured` | `boolean` | `true` if the agent's break overlaps the preferred break timeslot (or no preference was set) |

Summary counters are included at the schedule level:

| Field | Type | Description |
|---|---|---|
| `totalPreferences` | `int` | Total agent-day preferences submitted |
| `startTimeHonouredCount` | `int` | Number where start time was respected |
| `breakTimeHonouredCount` | `int` | Number where break time was respected |
| `overallHonouredPct` | `BigDecimal` | Percentage of all preference fields honoured |

### 8.4 Constraint Violations

A breakdown of every constraint violation in the current best solution, grouped by constraint and score level.

| Field | Type | Description |
|---|---|---|
| `constraint` | `String` | Constraint name (matches section 6 names) |
| `level` | `enum(HARD, SOFT)` | Score level of the violation |
| `weight` | `HardSoftScore` | Active weight from ConstraintWeights |
| `violationCount` | `int` | Number of times this constraint was violated |
| `totalPenalty` | `HardSoftScore` | Sum of individual match scores across all violations of this constraint. For fixed-penalty constraints this equals `weight × violationCount`; for variable-penalty constraints (e.g. break clustering) each match contributes a different score. |
| `violations` | `List<ViolationDetail>` | Individual violation instances |

**ViolationDetail:**

| Field | Type | Description |
|---|---|---|
| `agent` | `Agent` | Affected agent (if applicable) |
| `timeslot` | `Timeslot` | Affected timeslot (if applicable) |
| `description` | `String` | Human-readable explanation (e.g. "Agent Jane Smith assigned to Billing but has no matching specialization") |

The response also includes score totals:

| Field | Type | Description |
|---|---|---|
| `hardScore` | `int` | Total hard score (0 = all hard constraints satisfied) |
| `softScore` | `int` | Total soft score (higher = better) |
| `feasible` | `boolean` | `true` if `hardScore == 0` |
| `violatedHardConstraints` | `List<String>` | Names of hard constraints with at least one violation (empty when `feasible == true`). Derived from the violations list above, included at top level for convenience. |

### 8.5 Spreadsheet Export

The schedule can be exported as a multi-tab spreadsheet (`.xlsx`). Each tab corresponds to one of the output views above.

| Tab | Contents | Source |
|---|---|---|
| **Staffing Summary** | Predicted vs actual hours per day per specialization, with totals | Section 8.1 |
| **Agent Schedule** | One row per agent per timeslot: agent name, date, timeslot start/end, specialization, match type (primary/secondary), and a "Break" flag for gap slots | Section 8.2 |
| **Preference Report** | One row per agent per day: preferences submitted, actual values, honoured flags | Section 8.3 |

Constraint violations (section 8.4) are not included in the spreadsheet — they are diagnostic data consumed via the API and displayed in the UI.

Export is triggered via a dedicated endpoint (see section 7.8). The response streams the `.xlsx` file with `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

## 9. BambooHR Integration

### 9.1 Overview

Agent data is sourced from BambooHR via its REST API. The integration keeps the local `agent` table in sync with the BambooHR employee directory.

The service is operated by a single **BPO (Business Process Outsourcer)** that manages agents on behalf of multiple clients. Each client is represented by a `tenant_id` in WFM Service. All agents are stored in **one shared BambooHR instance** managed by the BPO — there is not a separate BambooHR account per tenant. Employee-to-tenant mapping is handled by the BPO's operational processes and reflected in the department or custom field data within BambooHR.

### 9.2 Data Source

Initially the BambooHR client will operate against an **in-memory mock** that returns static employee data. This allows development and testing to proceed without a live BambooHR account. The mock will be swapped for a real HTTP client behind a common interface when credentials are available.

### 9.3 Client Interface

```java
public interface BambooHRClient {
    List<BambooEmployee> listEmployees();
    BambooEmployee getEmployee(String bamboohrId);
    List<BambooTimeOff> listTimeOff(LocalDate from, LocalDate to);
}
```

`BambooTimeOff` represents a single day-off record: employee id, date, and type (`MANDATORY` or `PTO`).

Two implementations:

| Implementation | Purpose |
|---|---|
| `MockBambooHRClient` | Returns hard-coded employee data from memory. Active by default via a Spring profile (`bamboohr.mock=true`). |
| `HttpBambooHRClient` | Calls the live BambooHR REST API. Activated when `bamboohr.mock=false` and credentials are configured. |

### 9.4 Sync Behaviour

- **Scheduled sync** — A `@Scheduled` job runs at a configurable interval (default: every 6 hours) and calls `BambooHRClient.listEmployees()`. Synced employees are mapped to their respective tenants based on BambooHR data.
- **On-demand sync** — `POST /api/v1/agents/sync` triggers an immediate sync for the requesting tenant.
- **Upsert logic** — Employees are matched by `bamboohrId`. New employees are inserted; existing employees have their name, email, department, and job title updated. Employees no longer present in BambooHR are marked `active = false` (soft-delete).
- **Specializations are preserved** — Locally assigned specializations are never overwritten by a sync.
- **Days off sync** — The sync also calls `listTimeOff` for a configurable lookahead window (default: 8 weeks from today). Returned day-off records are upserted into the `agent_day_off` table, matched by (`agent`, `date`). Days off no longer present in BambooHR for the synced date range are deleted.

### 9.5 Configuration

| Property | Description | Default |
|---|---|---|
| `bamboohr.mock` | Use in-memory mock client | `true` |
| `bamboohr.api-key` | BambooHR API key (required when mock=false) | — |
| `bamboohr.subdomain` | BambooHR company subdomain | — |
| `bamboohr.sync-cron` | Cron expression for scheduled sync | `0 0 */6 * * *` |
| `solver.time-limit` | Maximum duration for a single solve run (ISO-8601 duration) | `PT5M` (5 minutes) |

## 10. Package Layout

```
src/main/java/com/wfm/
├── model/
│   ├── Specialization.java
│   ├── Agent.java
│   ├── Timeslot.java
│   ├── StaffingRequirement.java
│   ├── AgentPreference.java
│   ├── AgentDayOff.java
│   ├── AgentException.java
│   ├── AgentAssignment.java
│   ├── ConstraintWeights.java
│   └── Schedule.java
├── repository/
│   ├── SpecializationRepository.java
│   ├── AgentRepository.java
│   ├── AgentPreferenceRepository.java
│   ├── AgentDayOffRepository.java
│   ├── AgentExceptionRepository.java
│   ├── TimeslotRepository.java
│   ├── StaffingRequirementRepository.java
│   ├── ConstraintWeightsRepository.java
│   └── ScheduleRepository.java
├── service/
│   ├── AgentService.java
│   ├── AgentPreferenceService.java
│   ├── AgentDayOffService.java
│   ├── AgentExceptionService.java
│   ├── SpecializationService.java
│   ├── ConstraintWeightsService.java
│   ├── StaffingRequirementService.java
│   ├── TimeslotGeneratorService.java
│   ├── ErlangXService.java
│   ├── ScheduleOutputService.java
│   ├── ScheduleExportService.java
│   └── SolverService.java
├── controller/
│   ├── AgentController.java
│   ├── AgentDayOffController.java
│   ├── AgentExceptionController.java
│   ├── SpecializationController.java
│   ├── StaffingRequirementController.java
│   ├── ConstraintWeightsController.java
│   └── ScheduleController.java
├── integration/
│   ├── BambooHRClient.java
│   ├── BambooEmployee.java
│   ├── BambooTimeOff.java
│   ├── MockBambooHRClient.java
│   ├── HttpBambooHRClient.java
│   └── BambooSyncService.java
├── solver/
│   └── ScheduleConstraintProvider.java
└── WfmApplication.java
```

## 11. Database

PostgreSQL is the sole data store. Hibernate generates the schema from the JPA entity annotations. A migration tool (Flyway or Liquibase) should be added before the first production deployment.

Every tenant-owned table carries a `tenant_id BIGINT NOT NULL` column. All queries filter on `tenant_id` to enforce data isolation (see section 3.1).

### Key tables

- `specialization` (`tenant_id`, unique on `tenant_id` + `name`)
- `agent` (`tenant_id`, FK → `specialization` for primary, unique on `tenant_id` + `bamboohr_id`)
- `agent_secondary_specialization` (join table: FK → `agent`, FK → `specialization`)
- `agent_preference` (`tenant_id`, FK → `agent`, `date`, `is_standing`, unique on `tenant_id` + `agent` + `date` + `is_standing`; partial unique on `tenant_id` + `agent` where `is_standing = true` to enforce at most one standing preference per agent)
- `agent_day_off` (`tenant_id`, FK → `agent`, `date`, `type`, unique on `tenant_id` + `agent` + `date`)
- `agent_exception` (`tenant_id`, FK → `agent`, `date`, `contracted_hours_override`, `reason`, unique on `tenant_id` + `agent` + `date`)
- `timeslot` (`tenant_id`, unique on `tenant_id` + `date` + `start_time` + `end_time`)
- `staffing_requirement` (`tenant_id`, FK → `timeslot`, FK → `specialization`, unique on `tenant_id` + `timeslot` + `specialization`)
- `agent_assignment` (`tenant_id`, FK → `timeslot`, FK → `specialization`, FK → `agent`)
- `constraint_weights` (`tenant_id`, unique on `tenant_id` — one row per tenant; FK from `schedule`)
- `schedule` (`tenant_id`, FK → `constraint_weights`)

## 12. React UI

The React front end communicates exclusively through the REST API described in section 7. A persistent sidebar or top-level navigation provides access to each page. All pages operate within the authenticated tenant context.

### 12.1 Specializations Page

Manages the set of available specializations (section 4.3).

| Control | Type | Description |
|---|---|---|
| Specializations table | Table | Lists all specializations by name. One row per specialization. |
| Add specialization | Text input + button | Text field for the specialization name and an "Add" button. Creates a new specialization via `POST /specializations`. |
| Delete button | Icon button (per row) | Deletes the specialization via `DELETE /specializations/{id}`. Disabled or shows a warning if the specialization is in use by agents or staffing requirements. |

### 12.2 Agents Page

Displays agents synced from BambooHR and allows local specialization assignment (sections 5.2, 7.1).

| Control | Type | Description |
|---|---|---|
| Agent table | Table | Columns: name, email, department, job title, primary specialization, secondary specializations, contracted hours/day, active status, last synced timestamp. Sortable and filterable. |
| Sync button | Button | Triggers `POST /agents/sync`. Displays a loading indicator while the sync runs and refreshes the table on completion. |
| Active/inactive filter | Toggle or dropdown | Filters the table to show active agents, inactive agents, or all. Defaults to active only. |
| Edit specializations (per agent) | Inline or modal form | **Primary specialization:** single-select dropdown populated from the specializations list. **Secondary specializations:** multi-select control populated from the specializations list (excluding the selected primary). Saves via `PUT /agents/{id}/specializations`. |
| Edit contracted hours (per agent) | Inline edit or modal | Numeric input for the agent's contracted hours per day. Displays the tenant default if no override is set. Saves via `PUT /agents/{id}/contracted-hours`. |
| Days off (per agent) | Expandable row or modal | Shows upcoming days off for the agent (read-only), fetched via `GET /agents/{id}/days-off`. Each entry displays the date and type (Mandatory / PTO). |

### 12.3 Agent Preferences Page

Allows agents (or administrators on their behalf) to submit shift preferences (sections 4.5, 7.4). Accessible as a sub-view of the Agents page or as a standalone page.

| Control | Type | Description |
|---|---|---|
| Agent selector | Dropdown or search | Selects the agent whose preferences are being viewed or edited. |
| Period picker | Date range picker | Selects the target date range. Loads existing preferences via `GET /agents/{id}/preferences?from={date}&to={date}`. |
| Preferences grid | Editable table | One row per day (Monday–Sunday). Columns: **Day**, **Preferred start time** (time picker), **Preferred break time** (time picker), **Standing** (checkbox — at most one row may be checked; checking a new row unchecks the previous standing row), **Source** (read-only label: "Standing default" if the row's values are inherited from the standing preference, "Weekly override" if a date-specific preference exists, "None" if no preference). Time pickers are constrained by the active break start alignment (section 4.6.4). |
| Save button | Button | Persists all rows via `PUT /agents/{id}/preferences`. Disabled until a change is made. Sends the `isStanding` flag with each record; the server ensures only one is standing per agent. |
| Delete button | Button (per row) | Deletes the preference for the selected day via `DELETE /agents/{id}/preferences/{date}`. That day then falls back to the standing preference (if one exists). |

### 12.4 Agent Exceptions Page

Manages per-agent, per-day contracted hours overrides (sections 4.9, 5.8, 7.5). Accessible as a sub-view of the Agents page or as a standalone page.

| Control | Type | Description |
|---|---|---|
| Agent selector | Dropdown or search | Selects the agent whose exceptions are being viewed or edited. |
| Period picker | Date range picker | Selects the target date range. Loads existing exceptions via `GET /agents/{id}/exceptions?from={date}&to={date}`. |
| Exceptions grid | Editable table | One row per day within the selected range. Columns: **Day**, **Standard hours** (read-only — the agent's normal `contractedHoursPerDay`), **Override hours** (numeric input — the `contractedHoursOverride` for this day; blank if no exception), **Reason** (text input — required when override hours is set). Days that coincide with a day off are greyed out and cannot have an exception. |
| Save button | Button | Persists all rows with an override via `PUT /agents/{id}/exceptions`. Disabled until a change is made. Validates that reason is provided for every override. |
| Delete button | Button (per row) | Deletes the exception for the selected day via `DELETE /agents/{id}/exceptions/{date}`. The agent reverts to standard contracted hours for that day. |

### 12.5 Staffing Requirements Page

Defines how many agents are needed per timeslot per specialization (sections 4.4, 7.7).

| Control | Type | Description |
|---|---|---|
| Period picker | Date range picker | Selects the target date range. Loads existing requirements via `GET /staffing-requirements?from={date}&to={date}`. |
| Input mode toggle | Tab or radio group | Switches between **Direct input** and **Erlang X calculation**. |
| **Direct input mode** | | |
| Demand grid | Editable table | Rows: timeslots (generated from the time range and increment). Columns: one per specialization. Cells contain the required agent count (integer input). |
| Copy day | Button + day selector | Copies one day's demand profile to other selected days to speed up entry. |
| Save button | Button | Persists via `POST /staffing-requirements`. |
| **Erlang X mode** | | |
| Erlang X parameters form | Form fields | Per specialization per timeslot (or per day with a distribution pattern): **Forecasted call volume**, **Average handle time (AHT)** in seconds, **Caller patience** in seconds, **Retry rate** (percentage), **Target service level** (percentage within threshold seconds). |
| Calculate button | Button | Submits parameters via `POST /staffing-requirements/erlang-x`. Displays the calculated agent counts in the demand grid for review before saving. |
| Accept & save button | Button | Persists the calculated requirements. |

### 12.6 Constraint Weights Page

Displays and adjusts per-tenant constraint weights (sections 4.7, 5.9, 7.6).

| Control | Type | Description |
|---|---|---|
| Weights table | Editable table | One row per constraint (matching the constraints in section 6). Columns: **Constraint name**, **Description**, **Level** (Hard/Soft dropdown), **Weight** (numeric input). Pre-populated from `GET /constraint-weights`. |
| Reset to defaults button | Button | Restores all weights to the defaults defined in section 5.9. |
| Save button | Button | Persists changes via `PUT /constraint-weights`. |

### 12.7 Schedule Setup Page

Configures solver inputs and triggers a solve run (sections 4.1, 4.2, 4.6, 7.8).

| Control | Type | Description |
|---|---|---|
| Schedule period start | Date picker | Selects the first day of the schedule period (`periodStartDate`). |
| Schedule period end | Date picker | Selects the last day of the schedule period (`periodEndDate`). Must be on or after the start date. The period must be contiguous (e.g. Mon–Fri, Thu–Sun, Mon–Sun). |
| Timeslot increment | Dropdown | Options: 15 minutes, 30 minutes, 60 minutes. |
| Time range start | Time picker | Coverage window start (e.g. 08:00). |
| Time range end | Time picker | Coverage window end (e.g. 18:00). Must be after start. |
| Break blocked hours | Numeric input | Hours at the start and end of a shift where breaks are forbidden (default 1.0). Accepts fractional values (e.g. 0.5 for 30 minutes). |
| Break duration | Dropdown or numeric input | Break length in minutes (default 60). Options are filtered to multiples of the selected timeslot increment (e.g. 30, 45, 60 for a 15-min increment). |
| Minimum shift for break | Numeric input | Contracted hours must strictly exceed this value for a break to be assigned (default 4). Agents with exactly this many hours or fewer get no break. |
| Break start alignment | Dropdown | Options: On the hour, On the half hour, On the quarter hour. |
| Break cluster threshold | Numeric input (%) | Maximum percentage of on-shift agents on break per timeslot before penalty applies (default 20). |
| Default contracted hours/day | Numeric input | Tenant-level default contracted daily hours for agents without an explicit override (default 8.0). |
| Over-allocation hard limit | Numeric input (%) | Maximum percentage by which total assigned staffing hours may exceed total predicted demand hours before triggering a hard constraint violation (default 130%). |
| Validation summary | Read-only panel | Before solving, displays a summary: number of agents, specializations configured, staffing requirements loaded, days off affecting this period, exceptions configured, and any missing data warnings (e.g. agents without specializations, conflicting exceptions and days off). |
| Solve button | Button | Submits `POST /schedules/solve`. Disabled if validation errors exist. Navigates to the Schedule Results page on success. |
| Past schedules list | Paginated table | Lists previously completed and accepted schedules with date, score, and status, fetched via `GET /schedules`. Accepted schedules are visually distinguished (e.g. bold or with an "Accepted" badge). Each row links to its Schedule Results page. |

### 12.8 Schedule Results Page

Displays solver output for a given schedule (section 8). Shown after a solve completes or when viewing a past schedule.

| Control | Type | Description |
|---|---|---|
| Schedule header | Read-only panel | Displays: schedule period, time range, increment, solver score (hard/soft), feasibility indicator, and solve status (running/completed/stopped/accepted). Shows an **"Accepted"** badge when the schedule has been accepted. |
| Non-optimal banner | Alert banner | Displayed prominently at the top of the page when `feasible == false`. Shows the text **"NON-OPTIMAL SOLUTION"** followed by a bulleted list of every violated hard constraint name (from `violatedHardConstraints`). Styled as a warning/error banner (e.g. red or amber background) so it is immediately visible. Hidden when the solution is feasible. |
| Stop button | Button | Visible while the solver is running. Calls `PUT /schedules/{id}/stop`. |
| Progress indicator | Progress bar or spinner | Shown while the solver is running. Polls `GET /schedules/{id}` for status and intermediate scores. |
| Accept button | Primary button | Visible when the schedule status is `COMPLETED` or `STOPPED`. Accepts this schedule as the active schedule for its period via `PUT /schedules/{id}/accept`. If the solution is **not feasible** (`feasible == false`), clicking the button opens a confirmation dialog (see below) before proceeding. On success, the schedule status changes to `ACCEPTED`, the button is replaced by the accepted badge, and the reject button is hidden. |
| Reject button | Destructive/secondary button | Visible when the schedule status is `COMPLETED` or `STOPPED`. Opens a confirmation dialog: *"Are you sure you want to reject this schedule? It will be permanently deleted."* On confirmation, calls `PUT /schedules/{id}/reject` and navigates back to the Schedule Setup page. |
| Non-optimal accept confirmation | Modal dialog | Shown only when the user clicks **Accept** on a non-feasible schedule. Displays: **"This schedule has hard constraint violations and is not optimal. Are you sure you want to accept it?"** with a summary of violated hard constraints. Two buttons: **"Accept anyway"** (proceeds with accept) and **"Cancel"** (returns to the results page). |
| Export to Excel button | Button | Downloads the `.xlsx` export via `GET /schedules/{id}/export`. |
| Results tabs | Tab bar | Four tabs as described below. |

#### 12.8.1 Staffing Summary Tab

Corresponds to section 8.1.

| Control | Type | Description |
|---|---|---|
| Summary table | Table | Columns: **Day**, **Specialization**, **Predicted hours**, **Actual hours**, **Delta hours**, **Coverage %**. Colour-coded: green for fully staffed or overstaffed, amber for slightly understaffed, red for significantly understaffed. Includes per-day totals and a weekly grand-total row. |
| Day filter | Dropdown or button group | Filters the table to a single day or shows all days. |

#### 12.8.2 Agent Schedule Tab

Corresponds to section 8.2.

| Control | Type | Description |
|---|---|---|
| Schedule grid | Grid / heatmap | Rows: agents. Columns: timeslots for the selected day. Cells are colour-coded by specialization match type (primary vs secondary). Break timeslots are visually distinct (e.g. hatched or grey). Hovering a cell shows a tooltip with timeslot times, required specialization, and match type. |
| Day selector | Dropdown or tab strip | Switches the grid to a different day of the schedule period. |
| Agent search / filter | Text input | Filters the grid to agents whose name matches the search text. |
| Legend | Inline legend | Explains cell colours: primary match, secondary match, break, unassigned. |

#### 12.8.3 Preference Report Tab

Corresponds to section 8.3.

| Control | Type | Description |
|---|---|---|
| Preference table | Table | Columns: **Agent**, **Day**, **Preferred start**, **Actual start**, **Start honoured** (check/cross icon), **Preferred break**, **Actual break**, **Break honoured** (check/cross icon). Sortable by any column. |
| Summary counters | Read-only panel | Displays `totalPreferences`, `startTimeHonouredCount`, `breakTimeHonouredCount`, and `overallHonouredPct`. |
| Filter: overridden only | Toggle | Filters the table to show only rows where at least one preference was overridden. |

#### 12.8.4 Constraint Violations Tab

Corresponds to section 8.4.

| Control | Type | Description |
|---|---|---|
| Score summary | Read-only panel | Displays **Hard score**, **Soft score**, and **Feasible** (yes/no badge). Hard score highlighted in red if non-zero. |
| Violations table | Expandable table | One row per constraint. Columns: **Constraint name**, **Level** (Hard/Soft badge), **Weight**, **Violation count**, **Total penalty**. Expandable to show individual `ViolationDetail` rows with agent, timeslot, and human-readable description. |
| Filter by level | Dropdown or toggle | Filters to Hard only, Soft only, or All. |

## 13. Open Issues (SME Review Required)

- ~~**Contracted hours: does the value include or exclude break time?**~~ **Resolved.** Contracted hours **exclude** break time. An agent's contracted hours represent assigned (non-break) working time only. Break time is additional — e.g. a full-time agent with 8.0 contracted hours and a 60-minute break works a 9-hour shift. This is now stated explicitly in sections 5.2, 5.10, and 6.

- ~~**Coverage window vs. contracted hours + break pre-solve validation.**~~ **Resolved.** Since contracted hours exclude break time, the coverage window must be ≥ `contractedHoursPerDay + (breakDurationMinutes / 60)` for agents whose shift includes a break. The formula in section 7.8 has been updated accordingly.

- **"Every seat must be filled" vs contracted hours — over-allocation and under-allocation.** The planning variable `AgentAssignment.agent` is non-nullable, so the solver **must** assign an agent to every seat. Combined with the "Contracted hours" hard constraint (every agent works exactly their contracted hours), this creates a tension: if total seat-hours exceed the workforce's total contracted hours the solver is forced to over-allocate some agents, and if total seat-hours fall short some agents cannot reach their contracted hours. The "Bulk over-allocation limit" hard constraint (section 6) caps aggregate over-allocation at `overallocationHardLimitPct` (default 130 %), but there is no equivalent mechanism for under-allocation. SME discussion is needed to decide: (a) whether a "dummy" or "unassigned" agent should be introduced so that surplus seats can go unfilled, (b) whether the contracted hours constraint should be relaxed from "exactly" to "at most" (or soft), (c) how under-allocation (fewer seats than contracted hours) should be handled — e.g. allow agents to be idle, introduce slack assignments, or flag as infeasible, and (d) what the acceptable tolerance bands are for both over- and under-allocation scenarios.

## 14. API Versioning

All endpoints are served under `/api/v1`. The following versioning strategy applies:

**What constitutes a breaking change (requires a new major version):**

- Removing or renaming an endpoint, field, or enum value.
- Changing the type of an existing field (e.g. `int` → `String`).
- Adding a new **required** field to a request body.
- Changing the semantics of an existing field or error code in a way that would break existing clients.

**What is non-breaking (does not require a new version):**

- Adding a new optional field to a request or response body (clients must ignore unknown fields).
- Adding a new endpoint.
- Adding a new enum value (clients should handle unknown values gracefully).
- Adding a new error code.

**Coexistence:** When a `v2` is introduced, both `/api/v1` and `/api/v2` will be served simultaneously for a deprecation period. The React UI is always built against the latest API version and does not need to support older versions. External consumers (if any) will receive a deprecation notice with a sunset date.

**Current status:** Only `v1` exists. No breaking changes are anticipated before the first production release.

## 15. Open Questions

- Deployment topology (single JAR, containers, cloud provider).
- Erlang X input parameters to expose (call volume forecast per interval, AHT, caller patience, retry rate, service level target).

## 16. Future Enhancements

The following items are out of scope for the initial release but are anticipated as future work:

- **Audit trail and timestamps.** Add `createdAt`, `updatedAt`, and `createdBy` fields to key entities (schedules, constraint weights, exceptions, preferences). Record who accepted/rejected each schedule and when.
- **Optimistic locking.** Add `@Version` fields to entities with concurrent write risk (constraint weights, staffing requirements, preferences, exceptions). Return ETags on responses and require `If-Match` on updates to prevent lost updates.
- **Solve progress via SSE/WebSocket.** Replace polling-based solve progress with a server-sent events or WebSocket stream for real-time score updates and status changes.
- **Schedule comparison.** Allow side-by-side comparison of two completed schedules for the same period before accepting one.
- **Health and readiness endpoints.** Expose `/actuator/health` and `/actuator/readiness` for container orchestration and monitoring.
- **Structured logging and observability.** Add structured JSON logging, solve-duration metrics, constraint violation counters, and integration with an observability platform (e.g. OpenTelemetry).
- **Database indexing strategy.** Define composite indexes for high-frequency query patterns (`tenant_id` + date-range filters on preferences, days off, exceptions, timeslots, and staffing requirements).
- **Stale schedule cleanup.** A scheduled job to delete schedules stuck in `COMPLETED` or `STOPPED` status beyond a configurable TTL (e.g. 7 days) that were never accepted or rejected.
- **Rate limiting.** Throttle expensive endpoints (`POST /agents/sync`, `POST /schedules/solve`) to prevent abuse.
- **Multi-zone tenant support.** Extend the time model to support tenants operating across multiple time zones.
