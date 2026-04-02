# WFM Service User Manual

## Table of Contents

1. [Introduction](#1-introduction)
2. [Getting Started](#2-getting-started)
3. [Core Concepts](#3-core-concepts)
4. [Desk Management](#4-desk-management)
5. [Agent Management](#5-agent-management)
6. [Specializations](#6-specializations)
7. [Agent Preferences and Exceptions](#7-agent-preferences-and-exceptions)
8. [Timeslots](#8-timeslots)
9. [Staffing Requirements](#9-staffing-requirements)
10. [Schedule Solving](#10-schedule-solving)
11. [Constraint Weights](#11-constraint-weights)
12. [Exporting and Importing Data](#12-exporting-and-importing-data)
13. [Configuration](#13-configuration)
14. [API Reference](#14-api-reference)
15. [Troubleshooting](#15-troubleshooting)

---

## 1. Introduction

WFM Service is a workforce management system designed for contact centre scheduling. It automatically allocates agents to timeslots using constraint-based optimisation, producing optimal work schedules that respect agent preferences, specializations, time off, contracted hours, break requirements, and fairness constraints.

### Key Capabilities

- **Multi-tenant architecture** — each tenant's data is fully isolated.
- **Multi-desk support** — a tenant can manage multiple desks (e.g. "Inbound Sales", "Technical Support"), each with independent configuration.
- **Agent import from BambooHR** — employee data is synced from BambooHR, including time off.
- **Constraint solver** — powered by Timefold Solver with 18 configurable constraints covering hard rules (no overlaps, day-off respect) and soft goals (preference honouring, fairness).
- **Schedule lifecycle** — generate, review, accept, or reject schedules.
- **Excel import/export** — upload staffing data and download schedules as XLSX files.

### Technology Stack

| Component | Technology |
|-----------|-----------|
| Backend | Java 21, Spring Boot 3.4, Timefold Solver 1.16 |
| Database | PostgreSQL with Flyway migrations |
| Frontend | React 19, TypeScript 5.7, Vite 6 |
| Excel I/O | Apache POI 5.3 |

---

## 2. Getting Started

### Prerequisites

- **Java 21+** (JDK)
- **PostgreSQL 12+**
- **Node.js 20+** and **npm** (for the frontend)

### Database Setup

Create the database and user:

```bash
sudo -u postgres psql -c "CREATE USER wfm WITH PASSWORD 'wfm';"
sudo -u postgres psql -c "CREATE DATABASE wfm OWNER wfm;"
```

Flyway runs all schema migrations automatically on application startup.

### Starting the Backend

```bash
./gradlew bootRun
```

The API starts on `http://localhost:8080`. All API endpoints (except `/actuator/health`) require an `X-Tenant-ID` header.

### Starting the Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on `http://localhost:3000` and proxies API requests to `localhost:8080`.

### Verifying the Installation

Check the health endpoint:

```bash
curl http://localhost:8080/actuator/health
```

A healthy response returns `{"status":"UP"}`.

---

## 3. Core Concepts

### Tenants

Every request must include an `X-Tenant-ID` header (a numeric identifier). All data is scoped by tenant — tenants cannot see each other's data.

### Desks

A desk represents a logical grouping within a contact centre (e.g. "Inbound Sales", "Technical Support"). Agents, timeslots, schedules, specializations, and constraint weights are all scoped to a specific desk within a tenant.

### Agents

Agents are the employees who get assigned to timeslots. They are imported from BambooHR and then assigned to desks. Each agent has:

- **Contracted hours per day** — how many hours they should work daily.
- **Primary specialization** — their main skill area.
- **Preferences** — preferred start times and break times.
- **Days off** — vacation, sick leave, etc.

### Timeslots

Timeslots are fixed time windows during the day (e.g. 09:00–10:00). They are generated for a date range and represent the slots that need to be staffed.

### Staffing Requirements

Each timeslot can have staffing requirements — the number of agents needed per specialization. These can be set directly or calculated using the Erlang C formula.

### Schedules

A schedule is the output of the constraint solver. It assigns agents to timeslots for a date range. Schedules go through a lifecycle: **PENDING** → **SOLVING** → **SOLVED** → **ACCEPTED** or **REJECTED**.

### Constraint Weights

The solver uses 18 constraints to produce optimal schedules. Each constraint has a hard score (must be satisfied) and a soft score (preferred but not required). Weights can be tuned per desk.

---

## 4. Desk Management

### Creating a Desk

Send a `POST` request to `/api/v1/desks`:

```json
{
  "name": "Inbound Sales",
  "description": "Handles incoming sales calls",
  "defaultContractedHoursPerDay": 8.0
}
```

The desk name must be unique within a tenant.

### Listing Desks

`GET /api/v1/desks` returns all desks for the current tenant.

### Updating a Desk

`PUT /api/v1/desks/{deskId}` updates the desk name, description, or default contracted hours.

### Deleting a Desk

`DELETE /api/v1/desks/{deskId}` removes a desk. This will fail with a `409 Conflict` if the desk has any accepted schedules.

---

## 5. Agent Management

### Importing Agents from BambooHR

Agents are synced from BambooHR. To refresh agents for a desk:

```
POST /api/v1/desks/{deskId}/agents/refresh
```

This pulls employee data (name, email, department, job title) and their time-off records from BambooHR. In development mode (`bamboohr.mock=true`), a mock client generates sample agent data.

### Listing Agents

- **Tenant-level:** `GET /api/v1/agents` — lists all agents for the tenant. Supports pagination and search.
- **Desk-level:** `GET /api/v1/desks/{deskId}/agents` — lists agents assigned to a specific desk.
- **Unassigned only:** `GET /api/v1/agents?unassigned=true` — lists agents not yet assigned to any desk.

### Assigning Agents to a Desk

```
POST /api/v1/desks/{deskId}/agents
```

```json
{
  "agentIds": ["uuid-1", "uuid-2", "uuid-3"]
}
```

### Removing an Agent from a Desk

```
DELETE /api/v1/desks/{deskId}/agents/{agentId}
```

### Setting Agent Specializations

```
PUT /api/v1/desks/{deskId}/agents/{agentId}/specializations
```

```json
{
  "primarySpecializationId": "uuid",
  "secondarySpecializationIds": ["uuid-1", "uuid-2"]
}
```

### Setting Contracted Hours

```
PUT /api/v1/desks/{deskId}/agents/{agentId}/contracted-hours
```

```json
{
  "contractedHoursPerDay": 7.5
}
```

### Viewing Days Off

- **Per agent:** `GET /api/v1/agents/{agentId}/days-off`
- **All agents:** `GET /api/v1/days-off` (paginated)
- **Desk agents:** `GET /api/v1/desks/{deskId}/days-off`

Day-off types include: `VACATION`, `SICK`, `UNPAID`, and `SABBATICAL`.

---

## 6. Specializations

Specializations represent skills or capabilities required for timeslots (e.g. "English Support", "Billing", "Technical").

### Creating a Specialization

```
POST /api/v1/desks/{deskId}/specializations
```

```json
{
  "name": "Technical Support",
  "color": "#4287f5"
}
```

### Listing Specializations

```
GET /api/v1/desks/{deskId}/specializations
```

### Updating and Deleting

- `PUT /api/v1/desks/{deskId}/specializations/{id}` — update name or colour.
- `DELETE /api/v1/desks/{deskId}/specializations/{id}` — remove the specialization.

---

## 7. Agent Preferences and Exceptions

### Preferences

Agents can express preferences for their start times and break times. Two types:

- **Standing preferences** — recurring weekly, tied to a day of the week.
- **Weekly preferences** — one-off, tied to a specific date.

#### Viewing Preferences

```
GET /api/v1/desks/{deskId}/agents/{agentId}/preferences
```

#### Saving Preferences

```
PUT /api/v1/desks/{deskId}/agents/{agentId}/preferences
```

```json
[
  {
    "dayOfWeek": "MONDAY",
    "preferredStartTime": "09:00",
    "preferredBreakTime": "12:00",
    "isStanding": true
  },
  {
    "date": "2025-03-15",
    "preferredStartTime": "10:00",
    "preferredBreakTime": "13:00",
    "isStanding": false
  }
]
```

#### Deleting a Preference

```
DELETE /api/v1/desks/{deskId}/agents/{agentId}/preferences/{preferenceId}
```

#### Bulk Upload

```
POST /api/v1/desks/{deskId}/agents/preferences/upload
```

Upload a file containing preference data for multiple agents at once.

### Exceptions (Contracted Hours Overrides)

Exceptions allow overriding an agent's contracted hours for a specific date (e.g. a half-day).

#### Viewing Exceptions

```
GET /api/v1/desks/{deskId}/agents/{agentId}/exceptions
```

#### Saving Exceptions

```
PUT /api/v1/desks/{deskId}/agents/{agentId}/exceptions
```

```json
[
  {
    "date": "2025-03-20",
    "contractedHoursOverride": 4.0,
    "reason": "Dentist appointment"
  }
]
```

#### Deleting an Exception

```
DELETE /api/v1/desks/{deskId}/agents/{agentId}/exceptions/{date}
```

---

## 8. Timeslots

Timeslots define the time windows that need to be staffed.

### Generating Timeslots

```
POST /api/v1/desks/{deskId}/timeslots/generate
```

```json
{
  "startDate": "2025-03-01",
  "endDate": "2025-03-31",
  "startTime": "08:00",
  "endTime": "20:00",
  "incrementMinutes": 60
}
```

This creates timeslots for every day in the range. For the example above, it would create 12 one-hour slots per day (08:00-09:00, 09:00-10:00, ..., 19:00-20:00).

### Listing Timeslots

```
GET /api/v1/desks/{deskId}/timeslots?startDate=2025-03-01&endDate=2025-03-07
```

### Getting Timeslot Bounds

```
GET /api/v1/desks/{deskId}/timeslots/bounds
```

Returns the earliest and latest dates that have timeslots defined.

### Deleting Timeslots

```
DELETE /api/v1/desks/{deskId}/timeslots?startDate=2025-03-01&endDate=2025-03-31
```

Removes all timeslots (and associated staffing requirements) in the specified date range.

---

## 9. Staffing Requirements

Staffing requirements define how many agents of each specialization are needed per timeslot.

### Setting Requirements Directly

```
POST /api/v1/desks/{deskId}/staffing-requirements
```

```json
[
  {
    "timeslotId": "uuid",
    "specializationId": "uuid",
    "requiredAgents": 5
  }
]
```

### Calculating via Erlang C

The Erlang C formula calculates optimal staffing levels based on call volume and service-level targets:

```
POST /api/v1/desks/{deskId}/staffing-requirements/erlang-x
```

### Uploading FTE Data

```
POST /api/v1/desks/{deskId}/staffing-requirements/upload
```

Upload a spreadsheet with FTE (Full Time Equivalent) data. A template can be generated using:

```bash
./gradlew generateFteSpreadsheet
```

### Listing Requirements

```
GET /api/v1/desks/{deskId}/staffing-requirements
```

---

## 10. Schedule Solving

The schedule solver is the core of the system. It takes agents, timeslots, staffing requirements, preferences, days off, and constraint weights, and produces an optimal assignment of agents to timeslots.

### Starting a Solve

```
POST /api/v1/desks/{deskId}/schedules/solve
```

```json
{
  "periodStartDate": "2025-03-03",
  "periodEndDate": "2025-03-07",
  "startTime": "08:00",
  "endTime": "20:00",
  "incrementMinutes": 60,
  "breakDurationMinutes": 60,
  "breakBlockedHours": 2,
  "breakMinShiftHours": 5,
  "breakStartAlignment": "ON_HOUR",
  "overallocationHardLimitPct": 130,
  "underallocationHardLimitPct": 70
}
```

The solver runs asynchronously. Its status transitions through: **PENDING** → **SOLVING** → **SOLVED** (or **ERROR**).

#### Solver Parameters

| Parameter | Description |
|-----------|-------------|
| `periodStartDate` / `periodEndDate` | Date range to schedule |
| `startTime` / `endTime` | Daily operating hours |
| `incrementMinutes` | Timeslot length in minutes |
| `breakDurationMinutes` | Duration of agent breaks |
| `breakBlockedHours` | Hours at start/end of shift where breaks are not allowed |
| `breakMinShiftHours` | Minimum shift length to qualify for a break |
| `breakStartAlignment` | `ON_HOUR` or `ON_HALF_HOUR` — when breaks can start |
| `overallocationHardLimitPct` | Maximum % of contracted hours an agent can be assigned (default 130%) |
| `underallocationHardLimitPct` | Minimum % of contracted hours an agent should be assigned (default 70%) |

### Checking Solver Progress

```
GET /api/v1/desks/{deskId}/schedules/{scheduleId}
```

Returns the schedule status, score, and (when solved) all agent assignments.

### Stopping a Running Solver

```
PUT /api/v1/desks/{deskId}/schedules/{scheduleId}/stop
```

Stops the solver early. The best solution found so far is saved.

### Listing Schedules

```
GET /api/v1/desks/{deskId}/schedules
```

Returns all schedules for the desk (paginated).

### Accepting a Schedule

```
PUT /api/v1/desks/{deskId}/schedules/{scheduleId}/accept
```

Accepting a schedule:
- Marks it as the active schedule for the covered dates.
- Creates historical copies of timeslots and staffing requirements linked to the schedule.
- Only one schedule can be accepted per desk per date.

### Rejecting a Schedule

```
PUT /api/v1/desks/{deskId}/schedules/{scheduleId}/reject
```

### Deleting a Schedule

```
DELETE /api/v1/desks/{deskId}/schedules/{scheduleId}
```

### Understanding Scores

The solver produces a score in the format `hard/soft` (e.g. `0hard/-45soft`).

- **Hard score = 0:** All hard constraints are satisfied (required).
- **Soft score:** Lower magnitude is better. Represents preference violations and fairness penalties.

A schedule with a non-zero hard score has unresolvable constraint violations (e.g. not enough agents to meet staffing requirements).

---

## 11. Constraint Weights

The solver uses 18 constraints grouped into hard and soft categories. Weights control how heavily each constraint is penalised.

### Viewing Current Weights

```
GET /api/v1/desks/{deskId}/constraint-weights
```

### Updating Weights

```
PUT /api/v1/desks/{deskId}/constraint-weights
```

### Constraint Categories

#### Hard Constraints (must be satisfied)

| Constraint | Description |
|------------|-------------|
| Agent day off | Agents must not be assigned on their days off |
| Specialization match | Agents must have the required specialization (primary or secondary) |
| No overlapping assignments | An agent cannot be in two timeslots at the same time |
| Break rules | Breaks must follow blocked-hours and minimum-shift-hours rules |
| Contracted hours bounds | Agent hours must stay within the overallocation/underallocation limits |
| Allocation limits | Total desk allocation must respect hard percentage limits |

#### Soft Constraints (preferred but flexible)

| Constraint | Description |
|------------|-------------|
| Start time preference | Honour agent preferred start times |
| Break time preference | Honour agent preferred break times |
| Break clustering | Keep breaks together rather than scattered |
| Break alignment | Align breaks to hour or half-hour boundaries |
| Bulk allocation balance | Distribute work fairly across agents |
| Primary specialization preference | Prefer assigning agents to their primary specialization |
| Consecutive timeslots | Prefer contiguous shifts without gaps |

Increasing a soft weight makes the solver try harder to satisfy that constraint, potentially at the expense of others. Tuning weights allows you to balance competing priorities for each desk.

---

## 12. Exporting and Importing Data

### Exports (XLSX Downloads)

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/desks/{deskId}/agents/export` | Export desk agents to Excel |
| `GET /api/v1/desks/{deskId}/schedules/{id}/export` | Export a schedule to Excel |
| `GET /api/v1/client-management/employees/export` | Export all employees to Excel |

### Imports (File Uploads)

| Endpoint | Description |
|----------|-------------|
| `POST /api/v1/desks/{deskId}/agents/preferences/upload` | Upload agent preferences |
| `POST /api/v1/desks/{deskId}/staffing-requirements/upload` | Upload FTE / staffing data |
| `POST /api/v1/client-management/upload-desk-assignments` | Upload desk assignment mappings |

### Generating an FTE Template

```bash
./gradlew generateFteSpreadsheet
```

This creates a template spreadsheet that you can fill in and upload via the staffing requirements upload endpoint.

---

## 13. Configuration

### Application Properties

Key settings in `src/main/resources/application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/wfm` | PostgreSQL connection URL |
| `spring.datasource.username` | `wfm` | Database username |
| `spring.datasource.password` | `wfm` | Database password |
| `cors.allowed-origins` | `http://localhost:3000` | Allowed CORS origins for the frontend |
| `bamboohr.mock` | `true` | Use mock BambooHR client (for development) |
| `bamboohr.api-key` | _(empty)_ | BambooHR API key (production) |
| `bamboohr.subdomain` | _(empty)_ | BambooHR subdomain (production) |
| `bamboohr.time-off.lookahead-weeks` | `8` | Weeks ahead to fetch time-off data |
| `bamboohr.time-off.lookback-weeks` | `12` | Weeks back to fetch time-off data |
| `solver.time-limit` | `PT5M` | Maximum solver run duration (ISO 8601 duration) |
| `solver.polling-interval-ms` | `2000` | Solver status polling interval in milliseconds |

### Overriding Configuration

Configuration can be overridden in several ways:

1. **Environment variables:** Use Spring Boot's relaxed binding (e.g. `SPRING_DATASOURCE_URL`).
2. **Local profile:** Create `src/main/resources/application-local.yml` and run with `--spring.profiles.active=local`.
3. **Command-line arguments:** `./gradlew bootRun --args='--solver.time-limit=PT10M'`.

### BambooHR Integration

For production use, set `bamboohr.mock=false` and provide your BambooHR API key and subdomain. The system will then fetch real employee data and time-off records.

### Application Configuration Endpoint

Runtime configuration can also be managed via the API:

- `GET /api/v1/configuration` — view current app configuration.
- `PUT /api/v1/configuration` — update configuration values.

---

## 14. API Reference

All endpoints are under the base URL `http://localhost:8080/api/v1`.

Every request (except `/actuator/*`) must include the header:

```
X-Tenant-ID: {tenant-id}
```

### Error Response Format

All errors follow a consistent format:

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "details": [
      {
        "field": "name",
        "message": "Name is required",
        "value": null
      }
    ]
  }
}
```

### Error Codes

| HTTP Status | Error Code | Meaning |
|-------------|-----------|---------|
| 400 | `VALIDATION_FAILED` | Invalid request data or pre-solve validation failure |
| 404 | `NOT_FOUND` | Requested entity does not exist |
| 409 | `CONFLICT` | State conflict (e.g. deleting a desk with accepted schedules) |
| 409 | `REFRESH_IN_PROGRESS` | A BambooHR refresh is already running |
| 422 | `UNPROCESSABLE_ENTITY` | Request is syntactically valid but cannot be processed |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

### Pagination

List endpoints use cursor-based pagination. Responses include:

```json
{
  "data": [...],
  "nextCursor": "base64-encoded-cursor",
  "hasMore": true
}
```

Pass `cursor={nextCursor}` as a query parameter to fetch the next page.

### Complete Endpoint Summary

#### Desks
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/desks` | List all desks |
| POST | `/desks` | Create a desk |
| GET | `/desks/{deskId}` | Get desk details |
| PUT | `/desks/{deskId}` | Update a desk |
| DELETE | `/desks/{deskId}` | Delete a desk |

#### Agents (Tenant-Level)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/agents` | List all agents (supports `?unassigned=true`, search, pagination) |
| GET | `/agents/{agentId}` | Get agent details |
| GET | `/agents/{agentId}/days-off` | List agent days off |

#### Days Off
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/days-off` | List all days off (paginated) |
| GET | `/desks/{deskId}/days-off` | List days off for desk agents |

#### Desk Agents
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/desks/{deskId}/agents` | List desk agents |
| POST | `/desks/{deskId}/agents` | Assign agents to desk |
| DELETE | `/desks/{deskId}/agents/{agentId}` | Remove agent from desk |
| PUT | `/desks/{deskId}/agents/{agentId}/specializations` | Set specializations |
| PUT | `/desks/{deskId}/agents/{agentId}/contracted-hours` | Set contracted hours |
| POST | `/desks/{deskId}/agents/refresh` | Refresh from BambooHR |
| GET | `/desks/{deskId}/agents/export` | Export agents (XLSX) |
| GET | `/desks/{deskId}/agents/{agentId}/preferences` | List preferences |
| PUT | `/desks/{deskId}/agents/{agentId}/preferences` | Save preferences |
| DELETE | `/desks/{deskId}/agents/{agentId}/preferences/{id}` | Delete preference |
| POST | `/desks/{deskId}/agents/preferences/upload` | Upload preferences |
| GET | `/desks/{deskId}/agents/{agentId}/exceptions` | List exceptions |
| PUT | `/desks/{deskId}/agents/{agentId}/exceptions` | Save exceptions |
| DELETE | `/desks/{deskId}/agents/{agentId}/exceptions/{date}` | Delete exception |

#### Specializations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/desks/{deskId}/specializations` | List specializations |
| POST | `/desks/{deskId}/specializations` | Create specialization |
| PUT | `/desks/{deskId}/specializations/{id}` | Update specialization |
| DELETE | `/desks/{deskId}/specializations/{id}` | Delete specialization |

#### Timeslots
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/desks/{deskId}/timeslots` | List timeslots (by date range) |
| GET | `/desks/{deskId}/timeslots/bounds` | Get earliest/latest dates |
| POST | `/desks/{deskId}/timeslots/generate` | Generate timeslots |
| DELETE | `/desks/{deskId}/timeslots` | Delete timeslots (by date range) |

#### Staffing Requirements
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/desks/{deskId}/staffing-requirements` | List requirements |
| POST | `/desks/{deskId}/staffing-requirements` | Save requirements |
| POST | `/desks/{deskId}/staffing-requirements/erlang-x` | Calculate via Erlang C |
| POST | `/desks/{deskId}/staffing-requirements/upload` | Upload FTE data |

#### Schedules
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/desks/{deskId}/schedules/solve` | Start solver |
| GET | `/desks/{deskId}/schedules` | List schedules |
| GET | `/desks/{deskId}/schedules/{id}` | Get schedule details |
| PUT | `/desks/{deskId}/schedules/{id}/stop` | Stop solver |
| PUT | `/desks/{deskId}/schedules/{id}/accept` | Accept schedule |
| PUT | `/desks/{deskId}/schedules/{id}/reject` | Reject schedule |
| DELETE | `/desks/{deskId}/schedules/{id}` | Delete schedule |
| GET | `/desks/{deskId}/schedules/{id}/export` | Export schedule (XLSX) |

#### Constraint Weights
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/desks/{deskId}/constraint-weights` | Get weights |
| PUT | `/desks/{deskId}/constraint-weights` | Update weights |

#### Configuration
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/configuration` | Get app configuration |
| PUT | `/configuration` | Update app configuration |

#### Client Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/client-management/employees` | List BambooHR employees |
| POST | `/client-management/assign-to-desk` | Assign employees to desk |
| DELETE | `/client-management/desks/{deskId}/agents/{agentId}` | Remove agent |
| GET | `/client-management/employees/export` | Export employees (XLSX) |
| GET | `/client-management/employees/time-off` | Get department time off |
| POST | `/client-management/upload-desk-assignments` | Upload assignments |

#### Health
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Health check (no tenant header required) |

---

## 15. Troubleshooting

### Common Issues

#### "Missing X-Tenant-ID header" (400)

Every API request (except `/actuator/health`) must include the `X-Tenant-ID` header. Example:

```bash
curl -H "X-Tenant-ID: 1" http://localhost:8080/api/v1/desks
```

#### Solver returns ERROR status

Check the `errorMessage` field on the schedule response. Common causes:

- **No agents assigned to the desk** — assign agents before solving.
- **No timeslots generated** — generate timeslots for the date range first.
- **No staffing requirements** — set staffing requirements for timeslots.
- **Pre-solve validation failures** — the request body may be missing required fields or contain invalid date ranges.

#### Non-zero hard score after solving

A hard score other than zero means the solver could not satisfy all hard constraints. Common causes:

- Not enough agents with the required specializations.
- Too many agents on days off during the scheduled period.
- Overallocation/underallocation limits too tight for the available workforce.

Consider adding more agents, adjusting allocation limits, or extending the schedule period.

#### BambooHR refresh returns 409

A refresh is already in progress. Wait for it to complete before starting another.

#### Database connection failures

Verify PostgreSQL is running and accessible:

```bash
psql -h localhost -U wfm -d wfm -c "SELECT 1;"
```

Check `application.yml` for correct connection settings.

#### Frontend cannot reach the backend

Ensure the backend is running on port 8080 and CORS is configured to allow the frontend origin:

```yaml
cors:
  allowed-origins: http://localhost:3000
```

### Running Tests

```bash
./gradlew test
```

Tests use an in-memory H2 database and do not require PostgreSQL.

### Logs

Application logs are written to stdout. Increase solver logging verbosity:

```yaml
logging:
  level:
    ai.timefold.solver.core: DEBUG
```
