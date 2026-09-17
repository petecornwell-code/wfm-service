---
last_mapped_commit: 7e18ca2766e5fc34d93136d520c5a1529f8dc3b5
last_mapped_at: 2026-09-17
---
# Technology Stack

**Analysis Date:** 2026-09-17

## Languages

**Primary:**

- Java 21 - Backend services, solver implementation, REST APIs
- TypeScript 5.7 - Frontend UI components and routing logic
- SQL - PostgreSQL database migrations (Flyway, currently at V48)

**Secondary:**

- XML - Timefold solver configuration
- YAML - Spring Boot application configuration

## Runtime

**Environment:**

- Java Runtime Environment (JRE) 21 - Backend execution
- Node.js (latest stable via Vite) - Frontend development and build
- PostgreSQL 12+ - Primary production database

**Package Manager:**

- Gradle 8.x (wrapper) - Java/backend dependency management
- npm - JavaScript/TypeScript dependency management
- Lockfile: `backend/gradle/wrapper/gradle-wrapper.jar` (present); `frontend/package-lock.json` (present)

## Frameworks

**Core:**

- Spring Boot 3.4.2 - REST API framework, dependency injection, actuator endpoints
- React 19.0.0 - Frontend UI library
- React Router 7.1.0 - Frontend client-side routing

**Solver/Optimization:**

- Timefold Solver 1.16.0 - Shift scheduling constraint satisfaction and optimization
  - Integration: Spring Boot starter + JPA support
  - Config: `src/main/resources/solverConfig.xml`
  - Constraint provider: `com.wfm.solver.ScheduleConstraintProvider`

**Testing:**

- JUnit 5 (Jupiter) - Java test runner
- Spring Boot Test - Mocking and test context
- Testcontainers 1.21.4 - PostgreSQL-backed integration tests (Docker 29 compatible)
- H2 Database - In-memory test database for unit tests

**Build/Dev:**

- Gradle 8.x - Build automation, test execution, dependency resolution
- Vite 6.1.0 - Frontend bundler and dev server
- TypeScript Compiler (tsc) - Type checking before Vite build

## Key Dependencies

**Critical:**

- `org.springframework.boot:spring-boot-starter-web` 3.4.2 - HTTP request handling
- `org.springframework.boot:spring-boot-starter-data-jpa` 3.4.2 - ORM and database access
- `org.timefold.solver:timefold-solver-spring-boot-starter` 1.16.0 - Solver integration
- `org.postgresql:postgresql` (latest managed) - PostgreSQL JDBC driver (runtime only)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql` - Database migration management

**Infrastructure:**

- `org.springframework.boot:spring-boot-starter-actuator` 3.4.2 - Health checks, metrics endpoints
- `org.springframework.boot:spring-boot-starter-validation` 3.4.2 - Bean validation (JSR-380)
- `org.apache.poi:poi-ooxml` 5.3.0 - Excel/spreadsheet export for schedules

**Testing:**

- `org.testcontainers:testcontainers-bom` 1.21.4 - Container orchestration for integration tests
- `org.testcontainers:junit-jupiter` 1.21.4 - JUnit 5 integration
- `org.testcontainers:postgresql` 1.21.4 - PostgreSQL test container
- `com.h2database:h2` (latest managed) - In-memory database for fast unit tests

**Frontend:**

- `@vitejs/plugin-react` 4.3.0 - JSX/TSX support in Vite
- `@types/react` 19.0.0 - React type definitions
- `@types/react-dom` 19.0.0 - React DOM type definitions

## Configuration

**Environment:**

- Property source: `src/main/resources/application.yml`
- Profile-specific: `application-test.yml` (test profile, H2 in-memory, no Flyway)
- Externalization: Spring Boot `@Value` annotations for runtime configuration
  - CORS origins: `cors.allowed-origins`
  - BambooHR: `bamboohr.api-key`, `bamboohr.subdomain`, `bamboohr.mock`, timeouts
  - Solver: `solver.time-limit`, `solver.polling-interval-ms`
  - Timefold: `timefold.solver.solver-config-xml`

**Build:**

- `build.gradle` - Dependency declarations and build plugins
- Testcontainers version pinned at 1.21.4 in `ext['testcontainers.version']` for Docker 29 API compatibility
- Benchmark harness: optional system property `-Dwfm.benchmark=true` passed to tests via `systemProperty`

## Platform Requirements

**Development:**

- Java 21 JDK
- Docker (for Testcontainers PostgreSQL tests; Docker 29+)
- PostgreSQL 12+ (local dev instance at `localhost:5432`)
- Node.js LTS (for frontend development)
- Gradle 8.x (wrapper included)

**Production:**

- Java 21 JRE
- PostgreSQL 12+ (production database)
- Port 8080 (Spring Boot default)
- Environment variables for BambooHR integration: `BAMBOOHR_API_KEY`, `BAMBOOHR_SUBDOMAIN`
- CORS allowed origins must be configured via environment or application.yml

**Frontend Deployment:**

- Node.js for build-time TypeScript compilation
- Static host (Vite build output: `dist/` directory)
- Must be able to reach backend API at `/api` endpoint (configurable proxy target)

---

*Stack analysis: 2026-09-17*
