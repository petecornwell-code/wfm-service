---
last_mapped_commit: 7e18ca2766e5fc34d93136d520c5a1529f8dc3b5
last_mapped_at: 2026-09-17
---
# External Integrations

**Analysis Date:** 2026-09-17

## APIs & External Services

**BambooHR Integration:**

- Service: BambooHR API (employee and time-off data)
- SDK/Client: Custom HTTP client (`com.wfm.integration.HttpBambooHRClient`)
- Purpose: Fetch employee roster, job titles, employment types, and time-off periods
- Auth: API key via `bamboohr.api-key` (Spring property)
- Subdomain: Via `bamboohr.subdomain` (Spring property)
- Endpoints used:
  - List employees: `GET /api/gateway.php/{subdomain}/v1/employees` (with custom fields)
  - Get employee: `GET /api/gateway.php/{subdomain}/v1/employees/{id}`
  - List time-off: `GET /api/gateway.php/{subdomain}/v1/employees/time_off?start={date}&end={date}`
- Mock mode: Enabled via `bamboohr.mock: true` in config; routes to `MockBambooHRClient`
- Timeouts:
  - Connect: 10 seconds (configurable via `bamboohr.http.connect-timeout-seconds`)
  - Read: 120 seconds (configurable via `bamboohr.http.read-timeout-seconds`)
  - Rationale: Whole-tenant fetches during upload can be larger; bounded to prevent upload hangs

**Implementation Details:**

- Client interface: `src/main/java/com/wfm/integration/BambooHRClient.java`
- HTTP implementation: `src/main/java/com/wfm/integration/HttpBambooHRClient.java`
- Mock implementation: `src/main/java/com/wfm/integration/MockBambooHRClient.java`
- Delegating wrapper: `src/main/java/com/wfm/integration/DelegatingBambooHRClient.java`
- Refresh service: `src/main/java/com/wfm/service/BambooSyncEventService.java`
- Data structures:
  - `BambooEmployee` - Employee entity mapping
  - `BambooTimeOff` - Time-off period mapping

## Data Storage

**Databases:**

- PostgreSQL 12+
  - Connection: `jdbc:postgresql://localhost:5432/wfm` (configurable via `spring.datasource.url`)
  - Driver: `org.postgresql.Driver`
  - Credentials: Via `spring.datasource.username` and `spring.datasource.password`
  - ORM: Hibernate/JPA via Spring Data JPA
  - Dialect: `org.hibernate.dialect.PostgreSQLDialect`
  - Schema migration: Flyway (see below)
  - Connection validation: `ddl-auto: validate` (production uses existing schema)

**Schema Management:**

- Tool: Flyway (Flyway Core + PostgreSQL extension)
- Migrations location: `src/main/resources/db/migration/`
- Naming: Versioned SQL files `V{N}__description.sql`
- Current version: V48 (`V48__add_consistency_tolerance_and_preferred_start_weight.sql`)
- Test profile: Flyway disabled; Hibernate `ddl-auto: create-drop` for in-memory H2
- Postgres-backed tests: Run full migration suite via Testcontainers (contract in `PostgresBackedTest`)
- Key migrations:
  - V24: pgvector extension (for potential vector search)
  - V41: Agent shift assignment table
  - V47: Agent usual shift
  - V48: Consistency tolerance and preferred start weight

**File Storage:**

- Not externalized - embedded spreadsheet export only
- Excel export: Via Apache POI (`org.apache.poi:poi-ooxml` 5.3.0)
- Services:
  - `ScheduleExportService` - Schedule export to Excel
  - `DeskAgentExportService` - Desk assignments export
  - `ClientManagementExportService` - Client management data export

**Caching:**

- None detected - no Spring Cache, Redis, or explicit caching integration

## Authentication & Identity

**Auth Provider:**

- Custom/Tenant-based isolation
- Implementation: Tenant context filter (not traditional auth)
- Details: `com.wfm.config.TenantFilter` - Multi-tenant request routing
- No Spring Security integration
- No OAuth2 or third-party identity provider (BambooHR provides data import only)
- CORS configuration: Allows credentials with explicit origin patterns

## Monitoring & Observability

**Error Tracking:**

- Not detected - no Sentry, DataDog, or similar integration

**Logs:**

- Framework: Spring Boot default logging
- Configuration: `src/main/resources/application.yml` logging levels
- Targets:
  - Timefold solver phases: DEBUG level
    - `ai.timefold.solver.core.impl.constructionheuristic`
    - `ai.timefold.solver.core.impl.localsearch`
    - `ai.timefold.solver.core.impl.phase`
    - `ai.timefold.solver.core.impl.solver`
  - Custom solver logging: `com.wfm.service.SolverService`, `com.wfm.solver`
  - Default: INFO level

**Health & Metrics:**

- Spring Boot Actuator enabled (`spring-boot-starter-actuator`)
- Endpoints exposed:
  - Health: `GET /actuator/health` (shows details: always)
  - Probes: K8s-style liveness/readiness enabled
- Management endpoint base: `/actuator`

## CI/CD & Deployment

**Hosting:**

- Not explicitly documented in config
- Implied: Self-hosted or cloud-agnostic (no AWS-specific, GCP, or platform SDKs)

**CI Pipeline:**

- Not detected in repository scope (no GitHub Actions, GitLab CI, or Jenkins files found)

**Build Output:**

- Backend: Spring Boot fat JAR (via Gradle bootJar task)
- Frontend: Vite static bundle (`dist/` directory)

## Environment Configuration

**Required Environment Variables:**

- `SPRING_DATASOURCE_URL` - PostgreSQL connection string (default: `jdbc:postgresql://localhost:5432/wfm`)
- `SPRING_DATASOURCE_USERNAME` - DB user (default: `wfm`)
- `SPRING_DATASOURCE_PASSWORD` - DB password (default: `wfm`)
- `BAMBOOHR_API_KEY` - BambooHR API key (required if `bamboohr.mock: false`)
- `BAMBOOHR_SUBDOMAIN` - BambooHR account subdomain (required if `bamboohr.mock: false`)
- `CORS_ALLOWED_ORIGINS` - Comma-separated list of allowed frontend origins (default: `http://localhost:3000`)

**Optional Environment Variables:**

- `BAMBOOHR_MOCK` - Enable mock BambooHR responses (default: `true`)
- `BAMBOOHR_TIME_OFF_LOOKAHEAD_WEEKS` - Weeks to look ahead for time-off (default: `8`)
- `BAMBOOHR_TIME_OFF_LOOKBACK_WEEKS` - Weeks to look back for time-off (default: `12`)
- `BAMBOOHR_HTTP_CONNECT_TIMEOUT_SECONDS` - BambooHR connect timeout (default: `10`)
- `BAMBOOHR_HTTP_READ_TIMEOUT_SECONDS` - BambooHR read timeout (default: `120`)
- `SOLVER_TIME_LIMIT` - Solver duration (default: `PT5M` = 5 minutes, ISO-8601)
- `SOLVER_POLLING_INTERVAL_MS` - Polling frequency during solve (default: `2000`)

**Secrets Location:**

- Spring Boot externalized configuration: Environment variables or `application.yml`
- No secrets vault integration detected (Vault, Secrets Manager, etc.)
- Recommendation: Use environment variables for production credentials

## Webhooks & Callbacks

**Incoming:**

- BambooHR sync events: Service `BambooSyncEventService` suggests webhook handling capability
- Implementation: `src/main/java/com/wfm/service/BambooSyncEventService.java`
- Endpoint: Likely `/api/bamboohr/sync` or similar (not explicit in configs)

**Outgoing:**

- None detected - application does not push data to external systems

## Multi-Tenancy

**Architecture:**

- Multi-tenant design via `TenantContext` filter
- Context propagation: Spring request filter (`TenantFilter`)
- Isolation: Tenant ID routing at service layer
- Database: Shared PostgreSQL with tenant ID column partitioning (inferred from BambooHR `wfmTenantId` parameter usage)

## Development Proxy

**Frontend to Backend:**

- Vite dev server proxies `/api/*` requests to `http://localhost:8080` (configurable in `frontend/vite.config.ts`)
- This allows React app to call backend APIs without CORS issues during development

---

*Integration audit: 2026-09-17*
