# Technology Stack

**Analysis Date:** 2026-04-02

## Languages

**Primary:**
- Java 21 (LTS) - backend application (`src/main/java/com/wfm/`)
- TypeScript 5.7 - frontend SPA (`frontend/src/`)

**Secondary:**
- SQL - Flyway database migrations (`src/main/resources/db/migration/`)
- HCL (Terraform) - infrastructure-as-code (`infra/`)
- XML - Timefold solver configuration (`src/main/resources/solverConfig.xml`)

## Runtime

**Backend:**
- JVM 21 (Eclipse Temurin distribution)
- Build container: `eclipse-temurin:21-jdk`
- Runtime container: `eclipse-temurin:21-jre`

**Frontend:**
- Node.js 20 (specified in `.github/workflows/ci.yml`)

## Package Managers

**Backend:**
- Gradle 8.12 (Gradle Wrapper) — `gradle/wrapper/gradle-wrapper.properties`
- No lockfile (standard Gradle resolution)

**Frontend:**
- npm — lockfile present at `frontend/package-lock.json`; CI uses `npm ci`

## Frameworks

**Backend Core:**
- Spring Boot 3.4.2 (`build.gradle`)
  - `spring-boot-starter-web` - REST API
  - `spring-boot-starter-data-jpa` - ORM/persistence
  - `spring-boot-starter-actuator` - health endpoint at `/actuator/health`
  - `spring-boot-starter-validation` - bean validation (jakarta.validation)
- Spring Dependency Management Plugin 1.1.7 (`build.gradle`)

**Constraint Solver:**
- Timefold Solver 1.16.0 (BOM-managed) - constraint-based workforce scheduling
  - `timefold-solver-spring-boot-starter` - Spring Boot auto-configuration
  - `timefold-solver-jpa` - JPA integration for solver planning entities
  - Config: `src/main/resources/solverConfig.xml`
  - Algorithm: Construction Heuristic + Local Search (Entity Tabu size 7, Simulated Annealing)
  - Solution class: `com.wfm.model.Schedule`; entity class: `com.wfm.model.AgentAssignment`

**Frontend:**
- React 19.0 (`frontend/package.json`)
- React Router DOM 7.1 - client-side routing
- Vite 6.1 - build tool and dev server (`frontend/vite.config.ts`)

**Testing:**
- JUnit 5 (via `spring-boot-starter-test`) - backend test runner
- `timefold-solver-test` - constraint unit testing

## Key Dependencies

**Critical:**
- `ai.timefold.solver:timefold-solver-bom:1.16.0` - entire scheduling system depends on this
- `org.springframework.boot:spring-boot-starter-data-jpa` - all database access
- `org.postgresql:postgresql` - production database driver (runtime scope)

**Infrastructure:**
- `org.flywaydb:flyway-core` + `flyway-database-postgresql` - schema migration; 24 versioned scripts at `src/main/resources/db/migration/`
- `org.apache.poi:poi-ooxml:5.3.0` - Excel `.xlsx` generation for schedule export and FTE uploads
- `com.h2database:h2` - in-memory database for test execution (test runtime only)
- Jackson (transitive via Spring Boot) - JSON; custom `HardSoftScore` serializers in `src/main/java/com/wfm/config/`

## Database

**Production:**
- PostgreSQL
  - Default connection (dev): `jdbc:postgresql://localhost:5432/wfm` (`application.yml`)
  - ORM: Hibernate via Spring Data JPA; DDL strategy: `validate` (Flyway controls all schema)
  - Dialect: `org.hibernate.dialect.PostgreSQLDialect`
  - pgvector extension enabled via migration `V24__enable_pgvector_extension.sql`

**Test:**
- H2 in-memory — configured in `src/test/resources/application-test.yml`; Flyway disabled, `ddl-auto: create-drop`

**Migrations:**
- Flyway; 24 scripts `V1__` through `V24__` in `src/main/resources/db/migration/`

## Configuration

**Application:**
- `src/main/resources/application.yml` — single primary config file
- Key sections: `spring.datasource.*`, `bamboohr.*`, `solver.*`, `timefold.solver.*`, `cors.*`, `management.*`
- Test overrides: `src/test/resources/application-test.yml`

**Build:**
- `build.gradle` - single-module Gradle build
- `settings.gradle` - project name `wfm-service`
- Custom Gradle task: `generateFteSpreadsheet` (runs `com.wfm.util.FteSpreadsheetGenerator`)
- Frontend: `frontend/package.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`
  - TypeScript: strict mode, ES2020 target, `moduleResolution: bundler`

**Runtime env vars (ECS production, defined in `infra/ecs.tf`):**
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD` (from AWS Secrets Manager)
- `CORS_ALLOWED_ORIGINS`
- `SOLVER_TIME_LIMIT`
- `SPRING_PROFILES_ACTIVE`
- `JAVA_OPTS` (`-XX:MaxRAMPercentage=75.0`)

## Platform Requirements

**Development:**
- JDK 21+
- PostgreSQL running locally at `localhost:5432` (database `wfm`, user `wfm`)
- Node.js 20
- Frontend dev server (`vite`) proxies `/api` to `http://localhost:8080` (`frontend/vite.config.ts`)

**Production:**
- Docker multi-stage build (`Dockerfile`)
- AWS ECS Fargate (task definition in `infra/ecs.tf`)
- AWS RDS PostgreSQL 16.4 (`infra/rds.tf`)
- AWS CloudFront + S3 for frontend SPA (`infra/s3_cloudfront.tf`)
- AWS ECR for container images (`infra/ecr.tf`)
- AWS Secrets Manager for database password (`infra/rds.tf`)
- Region: `eu-west-2`
- Terraform >= 1.5 required to provision infrastructure (`infra/main.tf`)

---

*Stack analysis: 2026-04-02*
