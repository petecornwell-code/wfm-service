# Phase 5: Agent Data Enrichment & Desk Upload - Pattern Map

**Mapped:** 2026-05-11
**Files analyzed:** 27 (new/modified)
**Analogs found:** 25 / 27 (2 files have no analog and should follow RESEARCH.md examples)

## File Classification

### Backend — New files

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `src/main/java/com/wfm/model/EmploymentType.java` | model (enum) | n/a | `src/main/java/com/wfm/model/DayOffStatus.java` | exact |
| `src/main/java/com/wfm/model/JobTitleConfig.java` | model (entity) | CRUD | `src/main/java/com/wfm/model/Specialization.java` | exact (tenant-scoped, unique on composite key) |
| `src/main/java/com/wfm/model/BambooSyncEvent.java` | model (entity) | event log | `src/main/java/com/wfm/model/AppConfiguration.java` | role-match (simple tenant-scoped entity) |
| `src/main/java/com/wfm/repository/JobTitleConfigRepository.java` | repository | CRUD | `src/main/java/com/wfm/repository/SpecializationRepository.java` | exact |
| `src/main/java/com/wfm/repository/BambooSyncEventRepository.java` | repository | CRUD | `src/main/java/com/wfm/repository/AppConfigurationRepository.java` | role-match |
| `src/main/java/com/wfm/service/JobTitleConfigService.java` | service | CRUD | `src/main/java/com/wfm/service/AppConfigurationService.java` | exact (tenant-scoped, simple list/upsert) |
| `src/main/java/com/wfm/service/AgentEligibilityService.java` | service | request-response | `src/main/java/com/wfm/service/DeskAgentService.java` | role-match (tenant-scoped query helper) |
| `src/main/java/com/wfm/service/BambooSyncEventService.java` | service | event-write | `src/main/java/com/wfm/service/AppConfigurationService.java` | role-match |
| `src/main/java/com/wfm/exception/BambooHRRateLimitedException.java` | exception | n/a | `src/main/java/com/wfm/exception/RefreshInProgressException.java` | exact (RuntimeException with extra field) |
| `src/main/java/com/wfm/controller/JobTitleConfigController.java` | controller | request-response (CRUD) | `src/main/java/com/wfm/controller/AppConfigurationController.java` | exact |
| `src/main/java/com/wfm/dto/SkippedRow.java` | dto (record) | n/a | `src/main/java/com/wfm/dto/FteUploadResult.java` | role-match (record DTO) |
| `src/main/java/com/wfm/dto/JobTitleConfigResponse.java` | dto (record) | n/a | `src/main/java/com/wfm/dto/DeskAgentResponse.java` | exact |
| `src/main/java/com/wfm/dto/BambooSyncEventResponse.java` | dto (record) | n/a | `src/main/java/com/wfm/dto/DeskAgentResponse.java` | exact |
| `src/main/resources/db/migration/V25__add_agent_employment_type.sql` | migration | DDL | `src/main/resources/db/migration/V22__add_day_off_status.sql` | exact (add VARCHAR column with default-then-drop) |
| `src/main/resources/db/migration/V26__add_job_title_config.sql` | migration | DDL | `src/main/resources/db/migration/V20__accepted_schedule_per_desk_date.sql` | exact (CREATE TABLE + indexes) |
| `src/main/resources/db/migration/V27__add_bamboo_sync_event.sql` | migration | DDL | `src/main/resources/db/migration/V20__accepted_schedule_per_desk_date.sql` | exact |

### Backend — Modified files

| Modified File | Role | Change | Closest Analog | Match Quality |
|---------------|------|--------|----------------|---------------|
| `src/main/java/com/wfm/integration/BambooEmployee.java` | record (DTO) | add field | self (extend record) | self-extend |
| `src/main/java/com/wfm/integration/HttpBambooHRClient.java` | service (HTTP) | add field + onStatus | self (same file pattern) | self-extend |
| `src/main/java/com/wfm/integration/MockBambooHRClient.java` | service (mock) | parity with new field | self | self-extend |
| `src/main/java/com/wfm/integration/BambooRefreshService.java` | service | mapping + jobTitleConfig + sync event | self | self-extend |
| `src/main/java/com/wfm/model/Agent.java` | model (entity) | add `employmentType` column | `src/main/java/com/wfm/model/AgentDayOff.java` (status enum) | exact |
| `src/main/java/com/wfm/service/SolverService.java` | service | PTO filter + non-schedulable filter | self (line 124-127 + 158-161) | self-extend |
| `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` | service (XLSX) | header-based shape + structured SkippedRow + non-schedulable reject | self (extend) | self-extend |
| `src/main/java/com/wfm/service/DeskAgentService.java` | service | add employmentType + pendingPto to response | self (toResponse method) | self-extend |
| `src/main/java/com/wfm/service/ClientManagementService.java` | service | reject non-schedulable in `assignEmployeesToDesk` | self | self-extend |
| `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` | controller (advice) | handle `BambooHRRateLimitedException` | self (extend) | self-extend |
| `src/main/java/com/wfm/controller/AppConfigurationController.java` | controller | add `/bamboohr/sync-status` GET | self (extend) | self-extend |
| `src/main/java/com/wfm/dto/DeskAgentResponse.java` | dto | add `employmentType`, `pendingPtoCount`, `pendingPtoDates` | self | self-extend |

### Frontend — Modified files

| Modified File | Role | Change | Closest Analog | Match Quality |
|---------------|------|--------|----------------|---------------|
| `frontend/src/pages/DeskAgents.tsx` | page (component) | new column + filter + pending PTO badge | self (lines 227-269 existing filter pattern) | self-extend |
| `frontend/src/pages/Configuration.tsx` | page (component) | 2 new `<section>` cards | self + `frontend/src/pages/ClientManagement.tsx:428-498` (section layout) | role-match |
| `frontend/src/pages/ClientManagement.tsx` | page (component) | replace upload toast with modal | self (lines 156-176 upload flow) + `DeskAgents.tsx:382-413` (modal) | self-extend |
| `frontend/src/api/client.ts` | api client | new endpoints + types | self (lines 125-161 deskAgents block) | self-extend |

### Backend — New tests

| New Test File | Role | Closest Analog | Match Quality |
|---------------|------|----------------|---------------|
| `src/test/java/com/wfm/integration/BambooRefreshServiceTest.java` | unit test | `src/test/java/com/wfm/service/ResolvePreferencesPtoFilterTest.java` | role-match (JUnit5 + AssertJ + reflection) |
| `src/test/java/com/wfm/integration/HttpBambooHRClientTest.java` | unit test (mock HTTP) | (no direct analog — use Spring `MockRestServiceServer`; RESEARCH.md §15) | partial |
| `src/test/java/com/wfm/service/SolverServiceTest.java` | unit test | `ResolvePreferencesPtoFilterTest.java` | role-match |
| `src/test/java/com/wfm/service/DeskAssignmentUploadServiceTest.java` | unit test | `ResolvePreferencesPtoFilterTest.java` | role-match |
| `src/test/java/com/wfm/service/JobTitleConfigServiceTest.java` | unit test | `ResolvePreferencesPtoFilterTest.java` | role-match |

## Pattern Assignments

### `src/main/java/com/wfm/model/EmploymentType.java` (model enum)

**Analog:** `src/main/java/com/wfm/model/DayOffStatus.java`

**Full pattern to copy** (lines 1-7):
```java
package com.wfm.model;

public enum DayOffStatus {
    APPROVED,
    REQUESTED
}
```

**Apply for EmploymentType** — identical shape, two values: `FULL_TIME`, `PART_TIME`.

---

### `src/main/java/com/wfm/model/JobTitleConfig.java` (entity, CRUD)

**Analog:** `src/main/java/com/wfm/model/Specialization.java` — tenant-scoped, unique composite, UUID @Id.

**Imports + entity header pattern** (Specialization.java lines 1-10):
```java
package com.wfm.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "specialization", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "desk_id", "name"})
})
public class Specialization {
```

**Apply for JobTitleConfig:** swap table to `job_title_config`, unique on `(tenant_id, job_title)`. Add `created_at`/`updated_at` as `OffsetDateTime` (RESEARCH.md `<Code Examples>` §JobTitleConfig entity).

**ID + tenant pattern** (Specialization.java lines 11-23):
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;

@Column(name = "tenant_id", nullable = false)
private long tenantId;

@Column(name = "desk_id", nullable = false)
private UUID deskId;

@Column(nullable = false)
private String name;
```

**For JobTitleConfig:** drop `deskId` (config is tenant-scoped, not desk-scoped per D-08); use `jobTitle` instead of `name`; add `boolean nonSchedulable` and timestamps.

---

### `src/main/java/com/wfm/model/Agent.java` MODIFY — add `employmentType` column

**Analog:** `src/main/java/com/wfm/model/AgentDayOff.java` lines 27-33 (enum-as-string pattern with default value).

**Pattern to copy** (AgentDayOff.java lines 27-33):
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private DayOffStatus status = DayOffStatus.APPROVED;
```

**Apply for Agent.employmentType** — insert after `jobTitle` field (after Agent.java line 36), explicit column name + length:
```java
@Enumerated(EnumType.STRING)
@Column(name = "employment_type", nullable = false, length = 20)
private EmploymentType employmentType = EmploymentType.FULL_TIME;
```

Add getter/setter following the same style as Agent.java lines 84-85 (`getJobTitle`/`setJobTitle`).

---

### `src/main/java/com/wfm/model/BambooSyncEvent.java` (entity, event log)

**Analog:** `src/main/java/com/wfm/model/AppConfiguration.java` — minimal tenant-scoped entity.

**Pattern to copy** (AppConfiguration.java lines 1-25):
```java
package com.wfm.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_configuration", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "config_key"})
})
public class AppConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue = "";

    public AppConfiguration() {}
```

**Apply for BambooSyncEvent:** add columns per RESEARCH.md §"Sync event persistence" (`desk_id` nullable, `started_at`, `finished_at`, `success` boolean, `error_message`, `agents_synced`, `time_off_pulled`, `retry_after_seconds`). No unique constraint — this is an append-only event log. Use `OffsetDateTime` for timestamps (matches `Agent.lastRefreshedAt` at Agent.java:42).

---

### `src/main/java/com/wfm/repository/JobTitleConfigRepository.java`

**Analog:** `src/main/java/com/wfm/repository/SpecializationRepository.java` (the canonical tenant-scoped repository).

**Full pattern to copy** (SpecializationRepository.java lines 1-23):
```java
package com.wfm.repository;

import com.wfm.model.Specialization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpecializationRepository extends JpaRepository<Specialization, UUID> {

    List<Specialization> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    Optional<Specialization> findByIdAndTenantIdAndDeskId(UUID id, long tenantId, UUID deskId);

    boolean existsByTenantIdAndDeskIdAndName(long tenantId, UUID deskId, String name);

    Optional<Specialization> findByTenantIdAndDeskIdAndName(long tenantId, UUID deskId, String name);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
```

**Apply for JobTitleConfigRepository:**
- `List<JobTitleConfig> findByTenantId(long tenantId)` (drop `deskId` — tenant-scoped per D-08)
- `Optional<JobTitleConfig> findByTenantIdAndJobTitle(long tenantId, String jobTitle)`
- `Optional<JobTitleConfig> findByIdAndTenantId(UUID id, long tenantId)`
- `List<JobTitleConfig> findByTenantIdAndNonSchedulableTrue(long tenantId)` (for SolverService lookup; D-11)

---

### `src/main/java/com/wfm/repository/BambooSyncEventRepository.java`

**Analog:** `src/main/java/com/wfm/repository/AppConfigurationRepository.java` (16 lines — minimal).

**Full pattern** (AppConfigurationRepository.java lines 1-16):
```java
package com.wfm.repository;

import com.wfm.model.AppConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppConfigurationRepository extends JpaRepository<AppConfiguration, UUID> {

    Optional<AppConfiguration> findByTenantIdAndConfigKey(long tenantId, String configKey);

    List<AppConfiguration> findByTenantId(long tenantId);
}
```

**Apply for BambooSyncEventRepository:**
- `Optional<BambooSyncEvent> findFirstByTenantIdOrderByStartedAtDesc(long tenantId)` — "most recent" lookup for sync-status card (D-19).
- (Optional) `List<BambooSyncEvent> findByTenantIdOrderByStartedAtDesc(long tenantId, Pageable pageable)` if a "history" tab is added later.

---

### `src/main/java/com/wfm/service/JobTitleConfigService.java`

**Analog:** `src/main/java/com/wfm/service/AppConfigurationService.java` (57 lines — same shape: tenant-scoped, `@Transactional(readOnly = true)` for reads, `@Transactional` for upserts).

**Imports + class header** (AppConfigurationService.java lines 1-23):
```java
package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.AppConfiguration;
import com.wfm.repository.AppConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AppConfigurationService {

    public static final String BAMBOOHR_SERVER = "bamboohr.server";
    public static final String BAMBOOHR_API_KEY = "bamboohr.apiKey";
    public static final String BAMBOOHR_CACHE_MAX_SIZE = "bamboohr.cache.maxSize";

    private final AppConfigurationRepository repository;

    public AppConfigurationService(AppConfigurationRepository repository) {
        this.repository = repository;
    }
```

**Tenant + transaction pattern** (AppConfigurationService.java lines 25-31):
```java
@Transactional(readOnly = true)
public Map<String, String> getAllConfig() {
    long tenantId = TenantContext.getTenantId();
    Map<String, String> config = new HashMap<>();
    repository.findByTenantId(tenantId).forEach(c -> config.put(c.getConfigKey(), c.getConfigValue()));
    return config;
}
```

**Upsert pattern** (AppConfigurationService.java lines 41-56):
```java
@Transactional
public Map<String, String> saveConfig(Map<String, String> entries) {
    long tenantId = TenantContext.getTenantId();
    for (Map.Entry<String, String> entry : entries.entrySet()) {
        AppConfiguration config = repository.findByTenantIdAndConfigKey(tenantId, entry.getKey())
                .orElseGet(() -> {
                    AppConfiguration c = new AppConfiguration();
                    c.setTenantId(tenantId);
                    c.setConfigKey(entry.getKey());
                    return c;
                });
        config.setConfigValue(entry.getValue() != null ? entry.getValue() : "");
        repository.save(config);
    }
    return getAllConfig();
}
```

**Apply for JobTitleConfigService:**
- `listJobTitles()` — `findByTenantId(tenantId)` mapped to `JobTitleConfigResponse`
- `setNonSchedulable(UUID id, boolean value)` — `findByIdAndTenantId` + `setNonSchedulable(value)` + `setUpdatedAt(OffsetDateTime.now())` + `save`
- `ensureExists(long tenantId, String jobTitle)` — upsert helper called by `BambooRefreshService` (D-09): `findByTenantIdAndJobTitle` → `orElseGet(create new with nonSchedulable=false)`.

---

### `src/main/java/com/wfm/service/AgentEligibilityService.java`

**Analog:** `src/main/java/com/wfm/service/AppConfigurationService.java` (minimal stateless tenant-scoped service).

**Per RESEARCH.md D-11 + line 19 of `<Architectural Responsibility Map>`:** single helper `boolean isNonSchedulable(long tenantId, String jobTitle)`. Inject `JobTitleConfigRepository`. Use the existing `@Transactional(readOnly = true)` pattern.

**Optional caching** — see RESEARCH.md "Solver eligibility filter (D-11)" example: a Set-based per-request cache or `@Cacheable`. For Phase 5 a plain `findByTenantIdAndJobTitleAndNonSchedulableTrue` per call is fine — fewer than 50 rows per tenant.

---

### `src/main/java/com/wfm/exception/BambooHRRateLimitedException.java`

**Analog:** `src/main/java/com/wfm/exception/RefreshInProgressException.java` (9 lines — RuntimeException with message).

**Full pattern to copy** (RefreshInProgressException.java lines 1-9):
```java
package com.wfm.exception;

public class RefreshInProgressException extends RuntimeException {

    public RefreshInProgressException(String message) {
        super(message);
    }
}
```

**Apply for BambooHRRateLimitedException** — add `int retryAfterSeconds` field with getter (per D-20):
```java
package com.wfm.exception;

public class BambooHRRateLimitedException extends RuntimeException {
    private final int retryAfterSeconds;

    public BambooHRRateLimitedException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}
```

---

### `src/main/java/com/wfm/controller/JobTitleConfigController.java`

**Analog:** `src/main/java/com/wfm/controller/AppConfigurationController.java` (27 lines — minimal REST controller pattern).

**Full pattern to copy** (AppConfigurationController.java lines 1-27):
```java
package com.wfm.controller;

import com.wfm.service.AppConfigurationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/configuration")
public class AppConfigurationController {

    private final AppConfigurationService configurationService;

    public AppConfigurationController(AppConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public Map<String, String> getConfiguration() {
        return configurationService.getAllConfig();
    }

    @PutMapping
    public Map<String, String> updateConfiguration(@RequestBody Map<String, String> config) {
        return configurationService.saveConfig(config);
    }
}
```

**Apply for JobTitleConfigController:**
- `@RequestMapping("/api/v1/job-titles")`
- `GET /` → `service.listJobTitles()` returning `List<JobTitleConfigResponse>`
- `PATCH /{id}` (or `PUT /{id}/non-schedulable`) → `service.setNonSchedulable(id, body.nonSchedulable())`

Note: Tenant resolution happens via `TenantContext.getTenantId()` inside the service — the controller never passes `tenantId` from the URL (matches AppConfigurationController pattern). `TenantFilter` populates `TenantContext` from the `X-Tenant-ID` header per `frontend/src/api/client.ts:51`.

---

### `src/main/java/com/wfm/controller/AppConfigurationController.java` MODIFY — add sync-status endpoint

**Pattern within the same file** — add a new `@GetMapping("/bamboohr/sync-status")` that delegates to `BambooSyncEventService.getLatestSyncEvent()` returning `BambooSyncEventResponse`.

---

### `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` MODIFY — handle rate-limit exception

**Analog:** the existing handler patterns in the same file.

**Pattern to copy** (GlobalExceptionHandler.java lines 61-64 — RefreshInProgressException → 409):
```java
@ExceptionHandler(RefreshInProgressException.class)
public ResponseEntity<ErrorResponse> handleRefreshInProgress(RefreshInProgressException ex) {
    return buildResponse(HttpStatus.CONFLICT, "REFRESH_IN_PROGRESS", ex.getMessage(), List.of());
}
```

**Apply for BambooHRRateLimitedException → 503:**
```java
@ExceptionHandler(BambooHRRateLimitedException.class)
public ResponseEntity<ErrorResponse> handleBambooHRRateLimited(BambooHRRateLimitedException ex) {
    return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "BAMBOOHR_RATE_LIMITED", ex.getMessage(), List.of());
}
```

Use `HttpStatus.SERVICE_UNAVAILABLE` (matches the upstream cause). Existing test code already asserts via `ApiRequestError.code` (`frontend/src/api/client.ts:32-44`); the new code `BAMBOOHR_RATE_LIMITED` is the contract.

---

### `src/main/java/com/wfm/dto/SkippedRow.java` (record DTO)

**Analog:** `src/main/java/com/wfm/dto/FteUploadResult.java` (record with named fields).

**Pattern to copy** (FteUploadResult.java lines 7-17):
```java
public record FteUploadResult(
        int savedCount,
        int skippedCount,
        List<String> savedDetails,
        List<String> skippedDetails,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes
) {}
```

**Apply for SkippedRow** (per D-18):
```java
package com.wfm.dto;

public record SkippedRow(
    int rowNumber,
    String bamboohrId,    // nullable
    String name,           // nullable
    String reason
) {}
```

Then update `DeskAssignmentUploadService.DeskAssignmentUploadResult` (inner record at line 294-299) to use `List<SkippedRow>` instead of `List<String>` (breaking JSON shape change — also update `frontend/src/api/client.ts:430`).

---

### `src/main/java/com/wfm/dto/JobTitleConfigResponse.java` and `BambooSyncEventResponse.java`

**Analog:** `src/main/java/com/wfm/dto/DeskAgentResponse.java` (record with optional fields).

**Pattern** (DeskAgentResponse.java lines 8-14):
```java
public record DeskAgentResponse(UUID id, UUID deskId, String bamboohrId, String name, String email,
                                String department, String jobTitle, boolean active,
                                OffsetDateTime lastRefreshedAt,
                                SpecSummary primarySpecialization, List<SpecSummary> secondarySpecializations,
                                BigDecimal contractedHoursPerDay, BigDecimal effectiveContractedHoursPerDay) {
    public record SpecSummary(UUID id, String name) {}
}
```

**Apply for `JobTitleConfigResponse`:**
```java
public record JobTitleConfigResponse(UUID id, String jobTitle, boolean nonSchedulable,
                                      OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
```

**Apply for `BambooSyncEventResponse`** (per UI-SPEC §6 fields):
```java
public record BambooSyncEventResponse(OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                       boolean success, String errorMessage,
                                       Integer agentsSynced, Integer timeOffPulled,
                                       Integer retryAfterSeconds) {}
```

---

### `src/main/java/com/wfm/dto/DeskAgentResponse.java` MODIFY — add 3 fields

**Pattern** (DeskAgentResponse.java lines 8-14, extend in place):

Add to the record header: `EmploymentType employmentType, int pendingPtoCount, List<LocalDate> pendingPtoDates`.

**Where to source values:**
- `employmentType` — read from `Agent.employmentType` in `DeskAgentService.toResponse()` (DeskAgentService.java:52-71)
- `pendingPtoCount` / `pendingPtoDates` — query `AgentDayOff` rows for the agent where `type=PTO AND status=REQUESTED AND date >= today` (per UI-SPEC §3); add a new repository method or a `Map<UUID, List<LocalDate>>` pre-fetch in `DeskAgentService.listDeskAgentResponses()` (lines 41-50) to avoid N+1.

---

### `src/main/resources/db/migration/V25__add_agent_employment_type.sql`

**Analog:** `src/main/resources/db/migration/V22__add_day_off_status.sql` (2 lines — VARCHAR enum + default-then-drop pattern).

**Full pattern to copy** (V22__add_day_off_status.sql lines 1-4):
```sql
ALTER TABLE agent_day_off ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';

-- Remove the default after backfill so new rows must set it explicitly
ALTER TABLE agent_day_off ALTER COLUMN status DROP DEFAULT;
```

**Apply for V25:**
```sql
ALTER TABLE agent ADD COLUMN employment_type VARCHAR(20) NOT NULL DEFAULT 'FULL_TIME';

-- Per V22 convention: drop the default after backfill so new rows must set explicitly
ALTER TABLE agent ALTER COLUMN employment_type DROP DEFAULT;
```

---

### `src/main/resources/db/migration/V26__add_job_title_config.sql`

**Analog:** `src/main/resources/db/migration/V20__accepted_schedule_per_desk_date.sql` (CREATE TABLE + unique index).

**Pattern** (V20 lines 1-19):
```sql
CREATE TABLE accepted_schedule_date (
    schedule_id UUID NOT NULL REFERENCES schedule(id) ON DELETE CASCADE,
    tenant_id   BIGINT NOT NULL,
    desk_id     UUID NOT NULL,
    date        DATE NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
    PRIMARY KEY (schedule_id, date)
);

-- Enforce at most one ACCEPTED schedule per desk+date
CREATE UNIQUE INDEX idx_accepted_schedule_date_active
    ON accepted_schedule_date(tenant_id, desk_id, date)
    WHERE status = 'ACCEPTED';

CREATE INDEX idx_accepted_schedule_date_desk
    ON accepted_schedule_date(tenant_id, desk_id);
```

**Apply for V26:**
```sql
CREATE TABLE job_title_config (
    id               UUID PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,
    job_title        VARCHAR(255) NOT NULL,
    non_schedulable  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, job_title)
);

CREATE INDEX idx_job_title_config_tenant ON job_title_config(tenant_id);
```

---

### `src/main/resources/db/migration/V27__add_bamboo_sync_event.sql`

Same pattern as V26. Append-only event log. No unique constraint. Index on `(tenant_id, started_at DESC)` for "latest event" query.

---

### `src/main/java/com/wfm/integration/BambooEmployee.java` MODIFY — add `employmentHistoryStatus`

**Per RESEARCH.md "Anti-Patterns":** Add new fields at the END of the record constructor (positional callers must not break). Six existing constructor sites — verify with `grep -rn "new BambooEmployee" src/` per Pitfall 2.

**New shape** (extend BambooEmployee.java lines 3-12 with `employmentHistoryStatus` before `wfmTenantId`):
```java
public record BambooEmployee(
    String id,
    String displayName,
    String workEmail,
    String department,
    String jobTitle,
    String status,
    String employmentHistoryStatus,   // NEW — raw BambooHR value
    String wfmTenantId,
    String project
) {}
```

**Important:** This breaks all 6 callers — update each in lockstep (`HttpBambooHRClient.java:113`, `:140`; `MockBambooHRClient.java:61`, `:91`, `:107`).

---

### `src/main/java/com/wfm/integration/HttpBambooHRClient.java` MODIFY

**Two changes, both in the same file:**

**Change 1 — Custom-report fields array** (HttpBambooHRClient.java lines 76-81):
```java
String requestBody = """
        {
          "title": "WFM Employee Report",
          "fields": ["id", "displayName", "workEmail", "department", "jobTitle", "status"]
        }
        """;
```
Add `"employmentHistoryStatus"` to the array.

**Change 2 — Per-row parsing** (HttpBambooHRClient.java lines 103-117):
```java
for (JsonNode emp : rows) {
    String id = emp.path("id").asText("");
    String displayName = emp.path("displayName").asText("");
    String workEmail = emp.path("workEmail").asText("");
    String department = emp.path("department").asText("");
    String jobTitle = emp.path("jobTitle").asText("");
    String status = emp.path("status").asText("Active");

    if (id.isEmpty()) continue;

    employees.add(new BambooEmployee(
            id, displayName, workEmail, department, jobTitle, status,
            wfmTenantId, project
    ));
}
```
Add `String employmentHistoryStatus = emp.path("employmentHistoryStatus").asText("");` and pass it as the new constructor argument. Same change applies to `getEmployee()` at line 140 (use `""` since single-employee endpoint doesn't include it).

**Change 3 — 503 → BambooHRRateLimitedException onStatus** — wrap every `.retrieve()` (3 sites: line 83 listEmployees, line 131 getEmployee, line 178 fetchTimeOffByStatus).

**Existing RestClient invocation pattern** (HttpBambooHRClient.java lines 83-90):
```java
String json = restClient.post()
        .uri(baseUrl() + "/reports/custom?format=JSON")
        .header(HttpHeaders.AUTHORIZATION, basicAuth())
        .header(HttpHeaders.ACCEPT, "application/json")
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .retrieve()
        .body(String.class);
```

**Apply** per RESEARCH.md Pattern 5: insert `.onStatus(predicate, handler)` between `.retrieve()` and `.body(String.class)`. Extract the predicate + handler into a private helper to share across the 3 call sites — e.g. `private <T> T retrieveBody(RestClient.RequestHeadersSpec<?> spec, Class<T> bodyType) { ... }` or a static `Consumer<RestClient.ResponseSpec> applyRateLimitHandler`.

---

### `src/main/java/com/wfm/integration/MockBambooHRClient.java` MODIFY — parity

**Per CONTEXT.md `<canonical_refs>`:** mock must learn the new field for test parity.

**Sites to update** (MockBambooHRClient.java lines 61-70, 91-100, 107):
```java
pool.add(new BambooEmployee(
    String.valueOf(i + 1),
    displayName,
    email,
    "Vinted",
    "Agent",
    "Active",
    wfmTenantId,
    "Vinted"
));
```

**Apply:** insert `employmentHistoryStatus` (the new field). For variety in dev/test, randomize: `i % 5 == 0 ? "Part-Time" : "Full-time"` for ~20% part-time agents.

---

### `src/main/java/com/wfm/integration/BambooRefreshService.java` MODIFY — mapping + JobTitleConfig + SyncEvent

**Three concerns in this file. All happen inside `persistRefreshData` (the existing transaction).**

**1. Mapping rule (D-03, D-05)** — add a constant + helper at the top of the class (after BambooRefreshService.java line 46):
```java
private static final String BAMBOO_PART_TIME = "Part-Time";

private static EmploymentType mapEmploymentType(String status) {
    return BAMBOO_PART_TIME.equals(status) ? EmploymentType.PART_TIME : EmploymentType.FULL_TIME;
}
```

**2. Apply mapping inside the per-agent update loop** (BambooRefreshService.java lines 160-184). Existing pattern:
```java
for (Agent agent : currentDeskAgentsList) {
    if (agent.getBamboohrId() == null) continue;
    BambooEmployee emp = employeesByBambooId.get(agent.getBamboohrId());
    if (emp == null) continue;

    agent.setName(emp.displayName());
    agent.setEmail(emp.workEmail());
    agent.setDepartment(emp.department());
    agent.setJobTitle(emp.jobTitle());
    agent.setActive("Active".equalsIgnoreCase(emp.status()));
    agent.setLastRefreshedAt(OffsetDateTime.now());
```
**Apply:** after `setJobTitle`, add `agent.setEmploymentType(mapEmploymentType(emp.employmentHistoryStatus()));`.

**3. JobTitleConfig auto-populate (D-09)** — inside `persistRefreshData` after the per-agent loop closes, iterate distinct job titles from the `employees` list (NOT from DB, per RESEARCH.md anti-pattern) and call `jobTitleConfigService.ensureExists(tenantId, jobTitle)`. Inject `JobTitleConfigService` via constructor (BambooRefreshService.java:48-60).

**4. Sync event recording (D-19)** — wrap the body of `refreshDeskAgents()` (BambooRefreshService.java lines 71-97) in a try/catch/finally to write a `BambooSyncEvent` per RESEARCH.md "Sync event persistence" pattern. The `bambooSyncEventRepository.save(event)` in `finally` should use `REQUIRES_NEW` propagation (or a separate `BambooSyncEventService` with `@Transactional(propagation = REQUIRES_NEW)`) so a failure of the main TX doesn't roll back the event row.

---

### `src/main/java/com/wfm/service/SolverService.java` MODIFY — PTO filter + non-schedulable filter

**Two concerns, both in `startSolve`:**

**1. PTO filter fix (D-22)** — the bug at SolverService.java:158-161:
```java
// 5. Build lookup map for days off (needed for preference resolution and later)
Map<UUID, Set<LocalDate>> agentDaysOffMap = new HashMap<>();
for (AgentDayOff d : allDaysOff) {
    agentDaysOffMap.computeIfAbsent(d.getAgent().getId(), k -> new HashSet<>()).add(d.getDate());
}
```

**Apply per RESEARCH.md Pitfall 3** — filter by type+status:
```java
Map<UUID, Set<LocalDate>> agentDaysOffMap = new HashMap<>();
for (AgentDayOff d : allDaysOff) {
    boolean blocks = d.getType() == DayOffType.MANDATORY
                  || d.getStatus() == DayOffStatus.APPROVED;
    if (blocks) {
        agentDaysOffMap.computeIfAbsent(d.getAgent().getId(), k -> new HashSet<>()).add(d.getDate());
    }
}
```

**2. Non-schedulable filter (D-11)** — extend the existing eligibility filter at SolverService.java:123-127:
```java
List<Agent> eligibleAgents = allAgents.stream()
        .filter(Agent::isActive)
        .filter(a -> a.getPrimarySpecialization() != null)
        .toList();
```

**Apply:** inject `AgentEligibilityService`; add `.filter(a -> !agentEligibilityService.isNonSchedulable(tenantId, a.getJobTitle()))` between the two existing filters. Constructor injection pattern: copy the constructor block at SolverService.java:57-81.

---

### `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` MODIFY

**Three concerns, all extend the existing `uploadDeskAssignments` method:**

**1. Header-based shape detection (D-13, D-14)** — RESEARCH.md Pattern 4 is the canonical pattern. Insert AFTER the `Sheet sheet = workbook.getSheetAt(0);` line at DeskAssignmentUploadService.java:81. The current code reads columns positionally (`row.getCell(0)`...`row.getCell(3)` at lines 103-106) — replace with `col.get("...")` lookups.

**2. Structured SkippedRow (D-18)** — replace `List<String> skipped` (line 60) with `List<SkippedRow> skipped`. Each `skipped.add("Row " + (i+1) + ": ..."`) call (lines 117, 124, 135, 157, 234) becomes `skipped.add(new SkippedRow(i+1, bamboohrId, name, "..."))`. Also update the return type at line 260 + the inner record at lines 294-299.

**Existing skipped pattern to replace** (DeskAssignmentUploadService.java lines 117-119, 122-126):
```java
if (deskName == null || deskName.isBlank()) {
    skipped.add("Row " + (i + 1) + ": missing Desk Assignment");
    continue;
}

Desk desk = deskByName.get(deskName.trim().toLowerCase());
if (desk == null) {
    skipped.add("Row " + (i + 1) + ": desk '" + deskName.trim() + "' not found");
    continue;
}
```

**Apply:**
```java
if (deskName == null || deskName.isBlank()) {
    skipped.add(new SkippedRow(i + 1, bamboohrId, name, "Missing Desk Assignment"));
    continue;
}
Desk desk = deskByName.get(deskName.trim().toLowerCase());
if (desk == null) {
    skipped.add(new SkippedRow(i + 1, bamboohrId, name,
        "Desk '" + deskName.trim() + "' not found"));
    continue;
}
```

**3. Non-schedulable rejection (D-12)** — after the agent is resolved but before `agent.setDeskId(desk.getId())` at DeskAssignmentUploadService.java:239, add:
```java
if (agentEligibilityService.isNonSchedulable(tenantId, agent.getJobTitle())) {
    skipped.add(new SkippedRow(i + 1, agent.getBamboohrId(), agent.getName(),
        "Agent has non-schedulable job title: " + agent.getJobTitle()));
    continue;
}
```
Inject `AgentEligibilityService` via constructor — copy the constructor pattern at DeskAssignmentUploadService.java:37-49.

---

### `src/main/java/com/wfm/service/ClientManagementService.java` MODIFY

**Per D-12** — reject non-schedulable agents in `assignEmployeesToDesk`. The method is around line 200+ (not shown in research above but referenced in CONTEXT.md `<code_context>`). Pattern: inject `AgentEligibilityService` (same as upload service); throw `ConflictException` (matches existing `DeskAgentService.assignAgents()` rejection style at DeskAgentService.java:91-96):
```java
if (!agent.isActive()) {
    throw new ConflictException("Agent '" + agent.getName() + "' is inactive");
}
if (agent.getDeskId() != null) {
    throw new ConflictException("Agent '" + agent.getName() + "' is already assigned to a desk");
}
```

**Apply for non-schedulable check** — add as a third `throw new ConflictException(...)` clause:
```java
if (agentEligibilityService.isNonSchedulable(tenantId, agent.getJobTitle())) {
    throw new ConflictException("Agent '" + agent.getName()
        + "' has a non-schedulable job title: " + agent.getJobTitle());
}
```

---

### `src/main/java/com/wfm/service/DeskAgentService.java` MODIFY — extend `toResponse`

**Pattern** (DeskAgentService.java:52-71):
```java
private DeskAgentResponse toResponse(Agent a, BigDecimal deskDefault) {
    Specialization ps = a.getPrimarySpecialization();
    BigDecimal effective = a.getContractedHoursPerDay() != null
            ? a.getContractedHoursPerDay() : deskDefault;

    return new DeskAgentResponse(
            a.getId(),
            a.getDeskId(),
            a.getBamboohrId(),
            a.getName(), a.getEmail(),
            a.getDepartment(), a.getJobTitle(),
            a.isActive(), a.getLastRefreshedAt(),
            ps != null ? new DeskAgentResponse.SpecSummary(ps.getId(), ps.getName()) : null,
            a.getSecondarySpecializations().stream()
                    .map(s -> new DeskAgentResponse.SpecSummary(s.getId(), s.getName()))
                    .toList(),
            a.getContractedHoursPerDay(),
            effective
    );
}
```

**Apply:** add `a.getEmploymentType()` and `pendingPto` values to the constructor call. The `listDeskAgentResponses` method (lines 41-50) should pre-fetch a `Map<UUID, List<LocalDate>>` of pending PTO dates for all agents on the desk (single query) — pass `Map.getOrDefault(agentId, List.of())` to `toResponse`. Avoid N+1 by using `agentDayOffRepository` with a JPQL query filtered by `type=PTO AND status=REQUESTED AND date >= today` and `agent.deskId = :deskId`.

---

### `frontend/src/pages/DeskAgents.tsx` MODIFY — column, filter, badge

**Three additions; all extend existing inline patterns.**

**1. Filter dropdown (D-07, UI-SPEC §1)** — add `<select>` inline with the existing controls bar at DeskAgents.tsx:253-269. Existing dropdown pattern (lines 274-277):
```tsx
<select value={pageSize} onChange={e => { setPageSize(Number(e.target.value)); setCurrentPage(1) }}>
    {[10, 20, 50, 100].map(n => <option key={n} value={n}>{n}</option>)}
</select>
```
**Apply:** copy this shape; options `All / Full-time / Part-time`; new state `const [empTypeFilter, setEmpTypeFilter] = useState('')`.

Compose with existing `showActiveOnly` filter at line 227:
```tsx
const filteredAgents = showActiveOnly ? agentList.filter(da => da.active) : agentList
```
**Apply:** chain `.filter(da => empTypeFilter === '' || da.employmentType === empTypeFilter)`.

**2. Employment Type column (UI-SPEC §2)** — add a `<th>Emp Type</th>` after the Job Title header at line 292, and a `<td>` rendering `da.employmentType === 'FULL_TIME' ? 'Full-time' : 'Part-time'` (fallback `'—'` for null).

**3. Pending PTO badge (D-21, UI-SPEC §3)** — UI-SPEC §3 prescribes a dedicated column. Add `<th>PTO</th>` between "Last Refreshed" and "Actions" (line 294). Cell renders the badge per UI-SPEC §3 spec (amber background, `title` attribute for tooltip).

**Existing badge precedent** (CSS class `.desk-badge` at `frontend/src/index.css:63-71`) — use inline style instead since the colors differ (amber not blue). UI-SPEC §3 has the full inline style block.

---

### `frontend/src/pages/Configuration.tsx` MODIFY — 2 new sections

**Existing page structure** (Configuration.tsx:45-87) — single `<div style={{ maxWidth: '500px' }}>` block with form rows.

**1. Non-Schedulable Job Titles section (D-10, UI-SPEC §5)** — append a `<section>` after the existing form block (after line 84).

**Checkbox list pattern** — DeskAgents.tsx:320-334 already implements a checkbox list with the project's typical inline style:
```tsx
{specs.map(s => (
  <label key={s.id} style={{ fontSize: '0.8rem' }}>
    <input type="checkbox" checked={editSecondary.includes(s.id)}
      onChange={e => setEditSecondary(e.target.checked
        ? [...editSecondary, s.id]
        : editSecondary.filter(id => id !== s.id)
      )} />
    {s.name}
  </label>
))}
```

**Apply** for Non-Schedulable Job Titles using UI-SPEC §5 spec (label wrapping checkbox, `padding: 0.25rem 0.5rem; borderBottom: 1px solid #f3f4f6`); fire PATCH on toggle with optimistic UI update + revert on error.

**2. BambooHR Sync Status card (D-19, UI-SPEC §6)** — append another `<section>`. The container style is at UI-SPEC §6. Existing card-style container in the codebase: `frontend/src/pages/ClientManagement.tsx:428` (`<div style={{ marginTop: '2rem', padding: '1rem', background: '#f9fafb', borderRadius: '6px', border: '1px solid #e5e7eb' }}>`) — reuse this exact style.

---

### `frontend/src/pages/ClientManagement.tsx` MODIFY — upload modal

**Existing toast-only upload flow** (ClientManagement.tsx:156-176):
```tsx
const handleUploadDeskAssignments = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      const result: DeskAssignmentUploadResult = await clientManagement.uploadDeskAssignments(file)
      showToast('success', `Desk assignments: ${result.assignedCount} assigned, ${result.skippedCount} skipped`)
      if (result.skippedDetails.length > 0) {
        console.warn('Skipped rows:', result.skippedDetails)
      }
      if (viewDeskId) {
        loadDeskAgents(viewDeskId)
      }
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }
```

**Apply per D-17 + UI-SPEC §4:**
- Replace `showToast('success', ...)` with `setUploadResult(result)` (new state).
- Keep `showToast('error', ...)` on the catch path (errors stay as toasts).
- Add a new state `const [uploadResult, setUploadResult] = useState<DeskAssignmentUploadResult | null>(null)`.

**Modal render pattern (canonical):** `DeskAgents.tsx:382-413` (Assign Modal). The full backdrop + inner card pattern:
```tsx
{showAssignModal && (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem', width: '500px', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
        <h3>Assign Agents</h3>
        ...
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem', justifyContent: 'flex-end' }}>
          <button onClick={() => setShowAssignModal(false)}>Cancel</button>
          <button className="primary" onClick={handleAssign} disabled={...}>
            {assigning ? 'Assigning...' : `Assign (${selectedIds.size})`}
          </button>
        </div>
      </div>
    </div>
)}
```

**Apply for Upload Result Modal:**
- Width `600px` (per UI-SPEC §4, vs 500px for Assign Modal).
- Heading `"Upload Results"`.
- Body: summary line + table of skipped rows (only when `skippedCount > 0`).
- Footer buttons: `"Download skipped as CSV"` + `"Close"`.
- Backdrop click does NOT close (UI-SPEC §4) — only Close button calls `setUploadResult(null)`.

**CSV download pattern** — copy from the existing `handleExportEmployees` (ClientManagement.tsx:178-199):
```tsx
const blob = await res.blob()
const url = URL.createObjectURL(blob)
const a = document.createElement('a')
a.href = url
a.download = `${department.trim().replace(/[^a-zA-Z0-9_\-]/g, '_')}-employees.xlsx`
a.click()
URL.revokeObjectURL(url)
```
**Apply for skipped CSV:** Build blob inline from `uploadResult.skippedDetails` (no fetch needed):
```tsx
const csv = ['Row,BambooHR ID,Name,Reason',
    ...uploadResult.skippedDetails.map(r => `${r.rowNumber},"${r.bamboohrId || ''}","${r.name || ''}","${r.reason.replace(/"/g, '""')}"`)
].join('\n')
const blob = new Blob([csv], { type: 'text/csv' })
const url = URL.createObjectURL(blob)
// ... same download dance
```

---

### `frontend/src/api/client.ts` MODIFY — new endpoints + types

**Pattern** (client.ts:395-399 appConfiguration block):
```tsx
export const appConfiguration = {
  get: () => request<Record<string, string>>('/configuration'),
  update: (config: Record<string, string>) =>
    request<Record<string, string>>('/configuration', { method: 'PUT', body: JSON.stringify(config) }),
}
```

**Apply for new endpoints:**
```tsx
export const jobTitleConfig = {
  list: () => request<JobTitleConfigResponse[]>('/job-titles'),
  setNonSchedulable: (id: string, nonSchedulable: boolean) =>
    request<JobTitleConfigResponse>(`/job-titles/${id}`,
      { method: 'PATCH', body: JSON.stringify({ nonSchedulable }) }),
}

export const bambooSyncStatus = {
  get: () => request<BambooSyncEventResponse>('/configuration/bamboohr/sync-status'),
}
```

**Update the `DeskAssignmentUploadResult` type** (client.ts:430) — change `skippedDetails: string[]` to `skippedDetails: SkippedRow[]` (breaking change locked to backend DTO change).

**Update `DeskAgent` interface** (client.ts:282) — add `employmentType: 'FULL_TIME' | 'PART_TIME' | null; pendingPtoCount: number; pendingPtoDates: string[]`.

---

### Backend tests

**Analog:** `src/test/java/com/wfm/service/ResolvePreferencesPtoFilterTest.java` (lines 1-100).

**Imports + class pattern** (ResolvePreferencesPtoFilterTest.java lines 1-19):
```java
package com.wfm.service;

import com.wfm.model.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvePreferencesPtoFilterTest {

    private static final long TENANT = 1L;
    private static final LocalDate MON = LocalDate.of(2026, 3, 9);
```

**Test method pattern** (ResolvePreferencesPtoFilterTest.java lines 28-46):
```java
@Test
void standingPreference_excludedOnPtoDay() throws Exception {
    UUID deskId = UUID.randomUUID();
    Agent agent = agent("A1", "Alice", deskId);

    AgentPreference standing = standingPref(agent, deskId, DayOfWeek.TUESDAY, LocalTime.of(9, 0));
    Map<UUID, Set<LocalDate>> daysOffMap = Map.of(agent.getId(), Set.of(TUE));
    Schedule schedule = schedule(deskId, MON, FRI);

    List<AgentPreference> resolved = invokeResolvePreferences(
            List.of(standing), schedule, daysOffMap);

    assertThat(resolved).isEmpty();
}
```

**Apply** for all 6 backend test classes — same JUnit 5 + AssertJ + private-method-via-reflection style. RESEARCH.md "Wave 0 Gaps" lists every test to create.

---

## Shared Patterns

### Tenant context resolution
**Source:** `src/main/java/com/wfm/config/TenantContext.java` accessed via `TenantContext.getTenantId()`
**Apply to:** All new services (JobTitleConfigService, AgentEligibilityService, BambooSyncEventService)
**Pattern:** Service methods are tenant-scoped — they call `TenantContext.getTenantId()` at the top of every public method. Controllers never pass tenantId in URLs.
```java
@Transactional(readOnly = true)
public List<JobTitleConfigResponse> list() {
    long tenantId = TenantContext.getTenantId();
    return repository.findByTenantId(tenantId).stream().map(this::toResponse).toList();
}
```
Source: `AppConfigurationService.java:25-31`.

### Transaction handling
**Source:** Pervasive `@Transactional` / `@Transactional(readOnly = true)` from `org.springframework.transaction.annotation`
**Apply to:** All new services
**Pattern:** Read methods `@Transactional(readOnly = true)`, write methods `@Transactional`. For the sync event write that must survive a rollback of the main TX, use `@Transactional(propagation = Propagation.REQUIRES_NEW)` (verify against RESEARCH.md A3 — planner discretion).

### Error responses
**Source:** `src/main/java/com/wfm/controller/GlobalExceptionHandler.java`
**Apply to:** New `BambooHRRateLimitedException` — register a `@ExceptionHandler` block returning `buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "BAMBOOHR_RATE_LIMITED", ex.getMessage(), List.of())`.
**Pattern:** `IllegalArgumentException` → 400, `EntityNotFoundException` → 404, `ConflictException` → 409, `RefreshInProgressException` → 409, generic `Exception` → 500. The new exception goes at 503 via the same `buildResponse` helper.

### Toast feedback (frontend)
**Source:** `frontend/src/components/Toast.tsx` exposed as `showToast('success'|'error', message)`
**Apply to:** All non-modal frontend feedback paths (error cases on the upload modal, success on PATCH non-schedulable, etc.)
**Pattern:** Wrap async API calls in `try/catch`; in `catch`, `showToast('error', getErrorMessage(err))`. The `getErrorMessage` helper at `frontend/src/api/client.ts:71-81` extracts structured `ApiRequestError` details.

### Inline modal
**Source:** `frontend/src/pages/DeskAgents.tsx:382-413` (Assign Modal) and `:415-436` (Days Off Modal)
**Apply to:** Upload Result Modal in `ClientManagement.tsx`
**Pattern:** `<div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>` outer + `<div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem', width: '...', maxHeight: '...', display: 'flex', flexDirection: 'column' }}>` inner. Gated by a boolean/object state — `setState(null)` closes.

### XLSX cell parsing
**Source:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:279-292` (getCellString helper)
**Apply to:** Header parsing in the same file (no need to re-implement — reuse this helper).
**Pattern:**
```java
private String getCellString(Cell cell) {
    if (cell == null) return null;
    return switch (cell.getCellType()) {
        case STRING -> cell.getStringCellValue();
        case NUMERIC -> {
            if (DateUtil.isCellDateFormatted(cell)) {
                yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
            }
            yield String.valueOf((long) cell.getNumericCellValue());
        }
        case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
        default -> null;
    };
}
```

### Flyway migration naming + structure
**Source:** `src/main/resources/db/migration/V1__...` through `V24__...`
**Apply to:** V25, V26, V27
**Pattern:** Sequential `V{N}__{snake_case_description}.sql`. ALTER for column adds (V25) uses default-then-drop pattern (V22). CREATE TABLE for new entities (V26, V27) follows V20's structure with UUID PK + tenant_id + composite uniques + indexes.

### Test pattern
**Source:** `src/test/java/com/wfm/service/ResolvePreferencesPtoFilterTest.java`
**Apply to:** All Wave 0 test classes
**Pattern:** JUnit 5 (`@Test`), AssertJ (`assertThat`), private-method-via-reflection (when testing private helpers like `SolverService.startSolve` internals). H2 in-memory DB for persistence tests via `spring-boot-starter-test` + `com.h2database:h2`. No `@SpringBootTest` for pure unit tests of mapping logic — instantiate the service directly.

---

## No Analog Found

| File | Role | Data Flow | Reason | Fallback |
|------|------|-----------|--------|----------|
| `src/test/java/com/wfm/integration/HttpBambooHRClientTest.java` | unit test (mock HTTP) | n/a | No existing test uses `MockRestServiceServer` or stubbed `RestClient.Builder` — this is a new pattern for the codebase | Use Spring's `MockRestServiceServer` per RESEARCH.md §"Validation Architecture" line 603 — well-documented in Spring docs |
| `src/main/java/com/wfm/service/BambooSyncEventService.java` (REQUIRES_NEW write surface) | service | event-write | No existing service uses `@Transactional(propagation = REQUIRES_NEW)` in this codebase | Pattern is standard Spring; planner can use either `REQUIRES_NEW` or a try/finally that calls `bambooSyncEventRepository.save()` BEFORE the main TX boundary (depends on the chosen wrap-point in `BambooRefreshService.refreshDeskAgents`). RESEARCH.md A3 documents both options. |

## Metadata

**Analog search scope:**
- `src/main/java/com/wfm/{model,repository,service,controller,dto,exception,integration,config}/`
- `src/main/resources/db/migration/`
- `src/test/java/com/wfm/`
- `frontend/src/{pages,api,components}/`

**Files scanned:** ~60 files inspected directly

**Pattern extraction date:** 2026-05-11
