# WFM Service — System Specification

## 1. Overview

WFM Service is a workforce management system that allocates **agents** to **timeslots** using constraint-based optimisation. The solver is powered by [Timefold](https://timefold.ai/) (Java). The application exposes a REST API via Spring Boot and persists state in PostgreSQL.

## 2. Tech Stack

| Layer | Technology |
|---|---|
| UI | React |
| API / Controllers | Spring Boot (Java 21+) |
| Business Logic / Services | Spring Boot |
| ORM | Hibernate (via Spring Data JPA) |
| Database | PostgreSQL |
| Solver | Timefold Solver (Java) |

## 3. Architecture

```
┌───────────┐       ┌─────────────────────────────────────────────┐       ┌────────────┐
│           │       │              Spring Boot                    │       │            │
│   React   │◄─JSON─┤  Controller ─► Service ─► Repository       │◄─JPA──┤ PostgreSQL │
│           │       │                  │                          │       │            │
└───────────┘       │            Timefold Solver                  │       └────────────┘
                    └─────────────────────────────────────────────┘
```

The backend is organised into three packages mirroring the standard layered pattern:

- **`model`** — JPA entities and Timefold planning model annotations.
- **`service`** — Business logic, solver lifecycle management, and transaction orchestration.
- **`controller`** — REST endpoints that accept and return JSON.

## 4. Domain Model

### 4.1 Agent

An agent is a person who can be assigned to work during one or more timeslots.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `name` | `String` | Display name |
| `skills` | `Set<Skill>` | Skills the agent possesses |

### 4.2 Timeslot

A timeslot represents a window of time that requires agent coverage.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `startTime` | `OffsetDateTime` | Start of the window |
| `endTime` | `OffsetDateTime` | End of the window |
| `requiredSkill` | `Skill` | Skill needed for this slot |

### 4.3 Skill

A reference entity representing a named capability.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `name` | `String` | Unique skill name |

### 4.4 AgentAssignment (Planning Entity)

The central Timefold planning entity. The solver decides which agent fills each timeslot.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `timeslot` | `Timeslot` | The slot to fill |
| `agent` | `Agent` | **Planning variable** — assigned by the solver |

### 4.5 Schedule (Planning Solution)

The top-level Timefold `@PlanningSolution` that aggregates all facts and planning entities for a single solve run.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `agents` | `List<Agent>` | Problem facts |
| `timeslots` | `List<Timeslot>` | Problem facts |
| `assignments` | `List<AgentAssignment>` | Planning entities |
| `score` | `HardSoftScore` | Populated by solver |

## 5. Constraints

Constraints are defined in a `ConstraintProvider` implementation.

| Constraint | Level | Description |
|---|---|---|
| One assignment per timeslot | Hard | Each timeslot is assigned at most one agent. |
| Agent skill match | Hard | An agent must possess the skill required by the timeslot. |
| No overlapping assignments | Hard | An agent cannot be assigned to two timeslots that overlap in time. |
| Balanced workload | Soft | Prefer an even distribution of assignments across agents. |

## 6. API

All endpoints are served under the base path `/api/v1`.

### 6.1 Agents

| Method | Path | Description |
|---|---|---|
| `GET` | `/agents` | List all agents |
| `GET` | `/agents/{id}` | Get agent by id |
| `POST` | `/agents` | Create agent |
| `PUT` | `/agents/{id}` | Update agent |
| `DELETE` | `/agents/{id}` | Delete agent |

### 6.2 Timeslots

| Method | Path | Description |
|---|---|---|
| `GET` | `/timeslots` | List all timeslots |
| `GET` | `/timeslots/{id}` | Get timeslot by id |
| `POST` | `/timeslots` | Create timeslot |
| `PUT` | `/timeslots/{id}` | Update timeslot |
| `DELETE` | `/timeslots/{id}` | Delete timeslot |

### 6.3 Skills

| Method | Path | Description |
|---|---|---|
| `GET` | `/skills` | List all skills |
| `POST` | `/skills` | Create skill |
| `DELETE` | `/skills/{id}` | Delete skill |

### 6.4 Solver

| Method | Path | Description |
|---|---|---|
| `POST` | `/schedules/solve` | Start a solve run (async). Returns schedule id. |
| `GET` | `/schedules/{id}` | Get schedule and current best solution. |
| `PUT` | `/schedules/{id}/stop` | Terminate a running solve early. |

## 7. Package Layout

```
src/main/java/com/wfm/
├── model/
│   ├── Agent.java
│   ├── Timeslot.java
│   ├── Skill.java
│   ├── AgentAssignment.java
│   └── Schedule.java
├── repository/
│   ├── AgentRepository.java
│   ├── TimeslotRepository.java
│   ├── SkillRepository.java
│   └── ScheduleRepository.java
├── service/
│   ├── AgentService.java
│   ├── TimeslotService.java
│   ├── SkillService.java
│   └── SolverService.java
├── controller/
│   ├── AgentController.java
│   ├── TimeslotController.java
│   ├── SkillController.java
│   └── ScheduleController.java
├── solver/
│   └── ScheduleConstraintProvider.java
└── WfmApplication.java
```

## 8. Database

PostgreSQL is the sole data store. Hibernate generates the schema from the JPA entity annotations. A migration tool (Flyway or Liquibase) should be added before the first production deployment.

### Key tables

- `agent`
- `skill`
- `agent_skill` (join table)
- `timeslot`
- `agent_assignment`
- `schedule`

## 9. React UI

The React front end communicates exclusively through the REST API described in section 6. Core views:

| View | Purpose |
|---|---|
| Agent list | CRUD for agents and their skills |
| Timeslot list | CRUD for timeslots |
| Schedule | Trigger a solve, view progress, display resulting assignments |

## 10. Open Questions

- Authentication and authorisation mechanism (e.g. Spring Security + OAuth2).
- Solver time limit and termination strategy defaults.
- Multi-tenancy requirements.
- Deployment topology (single JAR, containers, cloud provider).
