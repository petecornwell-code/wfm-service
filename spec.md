# WFM Service — System Specification

## 1. Overview

WFM Service is a workforce management system that allocates **agents** to **timeslots** using constraint-based optimisation. The solver is powered by [Timefold](https://timefold.ai/) (Java). The application exposes a REST API via Spring Boot and persists state in PostgreSQL. Agent data is sourced from **BambooHR** via its REST API and synchronised into the local database.

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

Each agent has exactly two specialization assignments:

- **Primary specialization** — the agent's main area of expertise.
- **Secondary specialization** — a secondary area the agent can cover.

The set of available specializations is an input (e.g. "Billing", "Technical Support", "Sales"). Each timeslot may require coverage from multiple specializations simultaneously; the solver prefers assigning agents whose primary specialization matches the need, but may fall back to secondary.

### 4.4 Staffing Demand

The total number of agent-hours the client requires for the upcoming week, broken down **by day**. This input is provided per-specialization so the solver knows how many hours of each specialization are needed each day.

| Field | Example |
|---|---|
| Day | Monday |
| Specialization | Billing |
| Required hours | 64 |

Demand can be:

1. **Directly input** — the customer provides the hours.
2. **Calculated via Erlang C** — derived from call-volume forecasts, average handle time, and target service level. The Erlang C calculation is performed before the solver is invoked and the result is stored as the required hours.

## 5. Domain Model

### 5.1 Specialization

A reference entity representing a named area of expertise (e.g. "Billing", "Technical Support", "Sales"). The set of specializations is configured as a solver input.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `name` | `String` | Unique specialization name |

### 5.2 Agent

An agent is a person who can be assigned to work during one or more timeslots. Agent records are imported from BambooHR and treated as **read-only** within this system, except for specialization assignments which are managed locally.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key (internal) |
| `bamboohrId` | `String` | BambooHR employee id (unique, external key) |
| `name` | `String` | Display name (from BambooHR) |
| `email` | `String` | Work email (from BambooHR) |
| `department` | `String` | Department (from BambooHR) |
| `jobTitle` | `String` | Job title (from BambooHR) |
| `primarySpecialization` | `Specialization` | Main area of expertise (managed locally) |
| `secondarySpecialization` | `Specialization` | Secondary area the agent can cover (managed locally) |
| `active` | `boolean` | Whether the employee is active in BambooHR |
| `lastSyncedAt` | `OffsetDateTime` | Timestamp of last successful sync |

### 5.3 Timeslot

A timeslot is a single time interval within the coverage window. Timeslots are **generated** from the configured time range and increment — they are not created manually. A timeslot is specialization-agnostic; multiple agents with different specializations may be needed in the same timeslot.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `date` | `LocalDate` | The day this slot belongs to |
| `startTime` | `LocalTime` | Start of the interval |
| `endTime` | `LocalTime` | End of the interval |

### 5.4 StaffingRequirement

Represents the demand for a given specialization on a given day. Used to determine how many **agent seats** (AgentAssignment instances) to generate per timeslot for that specialization.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `date` | `LocalDate` | The day |
| `specialization` | `Specialization` | Which specialization is needed |
| `requiredHours` | `BigDecimal` | Total agent-hours required |
| `source` | `enum(DIRECT, ERLANG_C)` | How the value was determined |

**Deriving seats per timeslot:** The number of concurrent agents needed for a specialization is `requiredHours / coverageHours`, where `coverageHours` is the length of the time range (e.g. 10 hours for 08:00–18:00). This count is applied uniformly across every timeslot on that day for that specialization.

Example: Monday needs 64 hours of Billing, coverage window is 10 hours → 64 / 10 = **7 Billing agents needed per timeslot** (rounding up: 7). If timeslots are 15-minute increments with 40 slots per day, the system generates 40 × 7 = **280 AgentAssignment** instances for Billing on Monday, each needing a Billing-capable agent.

### 5.5 AgentAssignment (Planning Entity)

The central Timefold planning entity. Each instance represents one **seat** — a need for one agent with a particular specialization in a particular timeslot. The solver decides which agent fills each seat.

Multiple AgentAssignment instances may reference the same timeslot, each for a different (or the same) specialization. This is how a single timeslot is staffed by several agents across multiple specializations.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `timeslot` | `Timeslot` | The time interval to fill |
| `requiredSpecialization` | `Specialization` | The specialization this seat demands |
| `agent` | `Agent` | **Planning variable** — assigned by the solver |

### 5.6 Schedule (Planning Solution)

The top-level Timefold `@PlanningSolution` that aggregates all facts and planning entities for a single solve run.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `incrementMinutes` | `int` | 15, 30, or 60 |
| `startTime` | `LocalTime` | Coverage window start |
| `endTime` | `LocalTime` | Coverage window end |
| `weekStartDate` | `LocalDate` | Monday of the target week |
| `specializations` | `List<Specialization>` | Problem facts |
| `agents` | `List<Agent>` | Problem facts |
| `staffingRequirements` | `List<StaffingRequirement>` | Problem facts |
| `timeslots` | `List<Timeslot>` | Generated problem facts |
| `assignments` | `List<AgentAssignment>` | Planning entities |
| `score` | `HardSoftScore` | Populated by solver |

## 6. Constraints

Constraints are defined in a `ConstraintProvider` implementation.

| Constraint | Level | Description |
|---|---|---|
| Specialization match | Hard | An agent's primary or secondary specialization must match the assignment's required specialization. |
| No overlapping assignments | Hard | An agent cannot be assigned to two seats whose timeslots overlap in time on the same day. |
| One agent per seat | Hard | Each AgentAssignment (seat) is filled by exactly one agent (enforced by the planning variable). |
| Prefer primary specialization | Soft | Prefer assigning agents to seats matching their primary specialization over their secondary. |
| Balanced workload | Soft | Prefer an even distribution of assignments across agents. |

## 7. API

All endpoints are served under the base path `/api/v1`.

### 7.1 Agents

Agent records originate from BambooHR. The API is read-only except for local specialization assignments.

| Method | Path | Description |
|---|---|---|
| `GET` | `/agents` | List all agents |
| `GET` | `/agents/{id}` | Get agent by id |
| `PUT` | `/agents/{id}/specializations` | Set primary and secondary specialization for an agent |
| `POST` | `/agents/sync` | Trigger an on-demand sync from BambooHR |

### 7.2 Specializations

| Method | Path | Description |
|---|---|---|
| `GET` | `/specializations` | List all specializations |
| `POST` | `/specializations` | Create specialization |
| `DELETE` | `/specializations/{id}` | Delete specialization |

### 7.3 Staffing Requirements

| Method | Path | Description |
|---|---|---|
| `GET` | `/staffing-requirements` | List all staffing requirements |
| `POST` | `/staffing-requirements` | Create or update requirements (batch by week) |
| `POST` | `/staffing-requirements/erlang-c` | Calculate requirements from Erlang C inputs |

### 7.4 Solver

| Method | Path | Description |
|---|---|---|
| `POST` | `/schedules/solve` | Start a solve run (async). Accepts solver inputs. Returns schedule id. |
| `GET` | `/schedules/{id}` | Get schedule and current best solution. |
| `PUT` | `/schedules/{id}/stop` | Terminate a running solve early. |

## 8. BambooHR Integration

### 8.1 Overview

Agent data is sourced from BambooHR via its REST API. The integration keeps the local `agent` table in sync with the BambooHR employee directory.

### 8.2 Data Source

Initially the BambooHR client will operate against an **in-memory mock** that returns static employee data. This allows development and testing to proceed without a live BambooHR account. The mock will be swapped for a real HTTP client behind a common interface when credentials are available.

### 8.3 Client Interface

```java
public interface BambooHRClient {
    List<BambooEmployee> listEmployees();
    BambooEmployee getEmployee(String bamboohrId);
}
```

Two implementations:

| Implementation | Purpose |
|---|---|
| `MockBambooHRClient` | Returns hard-coded employee data from memory. Active by default via a Spring profile (`bamboohr.mock=true`). |
| `HttpBambooHRClient` | Calls the live BambooHR REST API. Activated when `bamboohr.mock=false` and credentials are configured. |

### 8.4 Sync Behaviour

- **Scheduled sync** — A `@Scheduled` job runs at a configurable interval (default: every 6 hours) and calls `BambooHRClient.listEmployees()`.
- **On-demand sync** — `POST /api/v1/agents/sync` triggers an immediate sync.
- **Upsert logic** — Employees are matched by `bamboohrId`. New employees are inserted; existing employees have their name, email, department, and job title updated. Employees no longer present in BambooHR are marked `active = false` (soft-delete).
- **Specializations are preserved** — Locally assigned specializations are never overwritten by a sync.

### 8.5 Configuration

| Property | Description | Default |
|---|---|---|
| `bamboohr.mock` | Use in-memory mock client | `true` |
| `bamboohr.api-key` | BambooHR API key (required when mock=false) | — |
| `bamboohr.subdomain` | BambooHR company subdomain | — |
| `bamboohr.sync-cron` | Cron expression for scheduled sync | `0 0 */6 * * *` |

## 9. Package Layout

```
src/main/java/com/wfm/
├── model/
│   ├── Specialization.java
│   ├── Agent.java
│   ├── Timeslot.java
│   ├── StaffingRequirement.java
│   ├── AgentAssignment.java
│   └── Schedule.java
├── repository/
│   ├── SpecializationRepository.java
│   ├── AgentRepository.java
│   ├── TimeslotRepository.java
│   ├── StaffingRequirementRepository.java
│   └── ScheduleRepository.java
├── service/
│   ├── AgentService.java
│   ├── SpecializationService.java
│   ├── StaffingRequirementService.java
│   ├── TimeslotGeneratorService.java
│   ├── ErlangCService.java
│   └── SolverService.java
├── controller/
│   ├── AgentController.java
│   ├── SpecializationController.java
│   ├── StaffingRequirementController.java
│   └── ScheduleController.java
├── integration/
│   ├── BambooHRClient.java
│   ├── BambooEmployee.java
│   ├── MockBambooHRClient.java
│   ├── HttpBambooHRClient.java
│   └── BambooSyncService.java
├── solver/
│   └── ScheduleConstraintProvider.java
└── WfmApplication.java
```

## 10. Database

PostgreSQL is the sole data store. Hibernate generates the schema from the JPA entity annotations. A migration tool (Flyway or Liquibase) should be added before the first production deployment.

### Key tables

- `specialization`
- `agent` (FK → `specialization` for primary and secondary)
- `timeslot`
- `staffing_requirement` (FK → `specialization`)
- `agent_assignment` (FK → `timeslot`, FK → `specialization`, FK → `agent`)
- `schedule`

## 11. React UI

The React front end communicates exclusively through the REST API described in section 7. Core views:

| View | Purpose |
|---|---|
| Agent list | View agents synced from BambooHR; assign primary/secondary specializations |
| Specializations | Manage the list of available specializations |
| Staffing requirements | Enter or calculate (Erlang C) required hours per day per specialization |
| Schedule | Configure solver inputs (increment, time range), trigger a solve, view resulting assignments |

## 12. Open Questions

- Authentication and authorisation mechanism (e.g. Spring Security + OAuth2).
- Solver time limit and termination strategy defaults.
- Multi-tenancy requirements.
- Deployment topology (single JAR, containers, cloud provider).
- Erlang C input parameters to expose (call volume, AHT, service level target, etc.).
