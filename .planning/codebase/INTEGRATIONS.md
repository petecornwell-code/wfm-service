# External Integrations

**Analysis Date:** 2026-04-02

## APIs & External Services

**BambooHR (HR System):**
- Purpose: Employee roster sync and time-off (PTO/requested leave) data ingestion
- Interface: `src/main/java/com/wfm/integration/BambooHRClient.java`
- Live implementation: `src/main/java/com/wfm/integration/HttpBambooHRClient.java`
- Mock implementation: `src/main/java/com/wfm/integration/MockBambooHRClient.java`
- Delegator: `src/main/java/com/wfm/integration/DelegatingBambooHRClient.java` — selects live vs mock based on DB config
- Auth: HTTP Basic Auth — API key from database `app_configuration` table, encoded as `Base64(apiKey:x)`
- Base URL: `https://api.bamboohr.com/api/gateway.php/{subdomain}/v1`
- HTTP client: Spring `RestClient` (synchronous; no Feign or WebClient)
- Endpoints consumed:
  - `POST /reports/custom?format=JSON` — fetch full employee list via custom report (fields: id, displayName, workEmail, department, jobTitle, status)
  - `GET /employees/{id}?fields=...` — fetch single employee details
  - `GET /time_off/requests/?start=&end=&status=` — called twice per refresh (status=`approved` and status=`requested`)
- DTOs: `BambooEmployee.java`, `BambooTimeOff.java` (both in `src/main/java/com/wfm/integration/`)
- Refresh service: `src/main/java/com/wfm/integration/BambooRefreshService.java`
- Config properties (`application.yml`):
  - `bamboohr.mock: true` — mock mode active by default; set false and configure credentials to go live
  - `bamboohr.time-off.lookahead-weeks: 8`
  - `bamboohr.time-off.lookback-weeks: 12`
- Credentials stored in database `app_configuration` table (not env vars), keys:
  - `bamboohr.server` — BambooHR subdomain or full hostname (e.g. `acme` or `acme.bamboohr.com`)
  - `bamboohr.apiKey` — API key

## REST API Produced

**Base path:** `/api/v1`

All endpoints require `X-Tenant-ID` header (enforced by `src/main/java/com/wfm/config/TenantFilter.java`; `/actuator/*` is exempt).

**Schedule Management** (`src/main/java/com/wfm/controller/ScheduleController.java`):
- `POST   /api/v1/desks/{deskId}/schedules/solve` - Start solver run
- `GET    /api/v1/desks/{deskId}/schedules` - List schedules (paginated)
- `GET    /api/v1/desks/{deskId}/schedules/{id}` - Get schedule detail
- `PUT    /api/v1/desks/{deskId}/schedules/{id}/stop` - Stop active solver
- `PUT    /api/v1/desks/{deskId}/schedules/{id}/accept` - Accept schedule (persists to DB)
- `PUT    /api/v1/desks/{deskId}/schedules/{id}/reject` - Reject schedule
- `DELETE /api/v1/desks/{deskId}/schedules/{id}` - Delete schedule
- `GET    /api/v1/desks/{deskId}/schedules/{id}/export` - Export schedule as `.xlsx`

**Other controllers** (all under `/api/v1`):
- `src/main/java/com/wfm/controller/AgentController.java` - Agent CRUD
- `src/main/java/com/wfm/controller/AgentDayOffController.java` - Agent day-off management
- `src/main/java/com/wfm/controller/AppConfigurationController.java` - App config (BambooHR settings)
- `src/main/java/com/wfm/controller/ClientManagementController.java` - Client management
- `src/main/java/com/wfm/controller/ConstraintWeightsController.java` - Solver constraint weight tuning
- `src/main/java/com/wfm/controller/DeskAgentController.java` - Desk-agent assignments
- `src/main/java/com/wfm/controller/DeskController.java` - Desk CRUD
- `src/main/java/com/wfm/controller/SpecializationController.java` - Agent specializations
- `src/main/java/com/wfm/controller/StaffingRequirementController.java` - Staffing requirements per timeslot
- `src/main/java/com/wfm/controller/TimeslotController.java` - Timeslot generation and management

**Error handling:** `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` (structured `ErrorResponse` DTOs)

## Data Storage

**Primary Database:**
- PostgreSQL (production via AWS RDS 16.4; local dev at `localhost:5432`)
  - Connection: `spring.datasource.*` in `application.yml`; overridden by `SPRING_DATASOURCE_*` env vars in production
  - ORM: Spring Data JPA / Hibernate; 13 repository interfaces in `src/main/java/com/wfm/repository/`
  - Schema: managed by Flyway (24 migrations `V1__` through `V24__`)
  - Extension: `pgvector` enabled (`V24__enable_pgvector_extension.sql`) — not yet used in application code
  - RDS: encrypted storage, 7-day backup retention, deletion protection on prod (`infra/rds.tf`)

**In-Memory Store:**
- `src/main/java/com/wfm/service/InMemoryScheduleStore.java` — `ConcurrentHashMap`-based store
- Holds active/running/non-accepted schedules; accepted schedules are persisted to PostgreSQL
- Includes a `deskToScheduleIndex` for desk-to-active-schedule lookup

**File Storage:**
- No external object storage (no S3/GCS in application code)
- Excel `.xlsx` files generated in-memory via Apache POI and returned as streaming HTTP responses

**Caching:**
- No external cache (no Redis, Memcached)
- BambooHR response cache size configurable via `bamboohr.cache.maxSize` app config key (in-memory)

## Authentication & Identity

**API Authentication:**
- No authentication on the WFM API itself (no Spring Security, no OAuth/JWT)
- Multi-tenancy via `X-Tenant-ID` request header (numeric long value)
- `src/main/java/com/wfm/config/TenantFilter.java` validates header presence and format; returns HTTP 400 if missing or non-numeric
- `src/main/java/com/wfm/config/TenantContext.java` stores tenant ID in `ThreadLocal` for request scope

**CORS:**
- Configured in `src/main/java/com/wfm/config/CorsConfig.java`
- Allowed origins: `cors.allowed-origins` property (default: `http://localhost:3000`); comma-separated list for multiple origins
- Exposes `Content-Disposition` header (for file downloads)

**External Auth:**
- BambooHR: Basic Auth with API key, stored in database `app_configuration` table (not in env vars or config files)
- AWS: OIDC-based role assumption from GitHub Actions (no long-lived credentials); `secrets.AWS_DEPLOY_ROLE_ARN` in `.github/workflows/deploy.yml`

## Monitoring & Observability

**Health Checks:**
- Spring Boot Actuator at `/actuator/health` — only actuator endpoint exposed
- Kubernetes/ECS probes enabled: `management.endpoint.health.probes.enabled: true`
- ECS health check: `curl -f http://localhost:8080/actuator/health` (`infra/ecs.tf`)

**Logging:**
- SLF4J + Logback (Spring Boot default)
- CloudWatch Logs in production (ECS log driver: `awslogs`, group `/ecs/{app}-{env}`, retention 30 days — `infra/ecs.tf`)
- Verbose solver logging in `application.yml`:
  - `ai.timefold.solver.core.*` packages: INFO/DEBUG
  - `com.wfm.service.SolverService`: DEBUG
  - `com.wfm.solver`: DEBUG

**Error Tracking:**
- No external error tracking service (no Sentry, Datadog, Rollbar)

## CI/CD & Deployment

**CI Pipeline:** GitHub Actions (`.github/workflows/ci.yml`) on pull_request and push to `main`:
- `test-backend`: runs `./gradlew test`, builds JAR, uploads test results artifact
- `build-frontend`: `npm ci && npm run build` in `frontend/`
- `docker-build`: builds Docker image (depends on `test-backend` passing)

**Deploy Pipeline:** GitHub Actions (`.github/workflows/deploy.yml`) on push to `main`:
- `deploy-backend`:
  1. Authenticates to AWS via OIDC (`secrets.AWS_DEPLOY_ROLE_ARN`)
  2. Builds and pushes Docker image to AWS ECR (tagged with `github.sha`)
  3. Registers new ECS task definition revision
  4. Updates ECS service and waits for stability
- `deploy-frontend`:
  1. Builds React SPA (`npm run build`)
  2. Syncs `frontend/dist/` to S3 bucket `wfm-service-prod-frontend`
  3. Invalidates CloudFront distribution cache

**Container Registry:**
- AWS ECR (`infra/ecr.tf`), repository `wfm-service`
- Images tagged with `github.sha` commit hash

**Infrastructure Provisioning:**
- Terraform >= 1.5 (`infra/`)
- State backend: AWS S3 with DynamoDB locking (bucket/key configured per environment)
- AWS provider ~> 5.0

## Environment Configuration

**Required for production:**
- `SPRING_DATASOURCE_URL` - PostgreSQL JDBC URL (set by Terraform/ECS)
- `SPRING_DATASOURCE_USERNAME` - DB username (set by Terraform/ECS)
- `SPRING_DATASOURCE_PASSWORD` - DB password (from AWS Secrets Manager; `infra/rds.tf`)
- `CORS_ALLOWED_ORIGINS` - allowed CORS origins (set by Terraform/ECS)
- `SOLVER_TIME_LIMIT` - solver duration ISO-8601 (default: `PT5M`)
- `SPRING_PROFILES_ACTIVE` - active Spring profile
- `AWS_DEPLOY_ROLE_ARN` - GitHub Actions secret for OIDC deployment

**BambooHR credentials (stored in DB, not env vars):**
- `bamboohr.server` key in `app_configuration` table
- `bamboohr.apiKey` key in `app_configuration` table

**Optional:**
- `bamboohr.mock: false` - enable live BambooHR (default is `true` / mock mode)

## Webhooks & Callbacks

**Incoming:**
- None detected

**Outgoing:**
- None detected

## Frontend-Backend Communication

- Frontend at `frontend/` communicates with backend exclusively via REST API
- Dev: Vite dev server at `localhost:3000` proxies `/api/*` to `http://localhost:8080` (`frontend/vite.config.ts`)
- Production: CloudFront routes `/api/*` to ALB (ECS backend) and all other paths to S3 (SPA) (`infra/s3_cloudfront.tf`)
- All requests include `X-Tenant-ID` header; API base path `/api/v1` (relative URL, works in both dev proxy and prod same-origin)

---

*Integration audit: 2026-04-02*
