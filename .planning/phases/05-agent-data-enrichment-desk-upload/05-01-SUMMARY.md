---
phase: 05-agent-data-enrichment-desk-upload
plan: "01"
subsystem: backend-schema-model
tags: [flyway, jpa, entity, repository, service, exception]
dependency_graph:
  requires: []
  provides:
    - "V25: agent.employment_type column"
    - "V26: job_title_config table"
    - "V27: bamboo_sync_event table"
    - "EmploymentType enum"
    - "JobTitleConfig entity + repository"
    - "BambooSyncEvent entity + repository"
    - "Agent.employmentType field"
    - "AgentEligibilityService.isNonSchedulable"
    - "BambooHRRateLimitedException"
    - "GlobalExceptionHandler 503 BAMBOOHR_RATE_LIMITED"
  affects:
    - "src/main/java/com/wfm/model/Agent.java"
    - "src/main/java/com/wfm/controller/GlobalExceptionHandler.java"
tech_stack:
  added: []
  patterns:
    - "Flyway default-then-drop column migration (V22 pattern)"
    - "Tenant-scoped entity with composite unique constraint (Specialization pattern)"
    - "Append-only event log entity (no unique constraint)"
    - "@Enumerated(EnumType.STRING) field with default value"
    - "Stateless tenant-param service (AgentEligibilityService)"
    - "@DataJpaTest H2 persistence round-trip test"
key_files:
  created:
    - src/main/resources/db/migration/V25__add_agent_employment_type.sql
    - src/main/resources/db/migration/V26__add_job_title_config.sql
    - src/main/resources/db/migration/V27__add_bamboo_sync_event.sql
    - src/main/java/com/wfm/model/EmploymentType.java
    - src/main/java/com/wfm/model/JobTitleConfig.java
    - src/main/java/com/wfm/model/BambooSyncEvent.java
    - src/main/java/com/wfm/repository/JobTitleConfigRepository.java
    - src/main/java/com/wfm/repository/BambooSyncEventRepository.java
    - src/main/java/com/wfm/service/AgentEligibilityService.java
    - src/main/java/com/wfm/exception/BambooHRRateLimitedException.java
    - src/test/java/com/wfm/model/AgentEmploymentTypePersistenceTest.java
  modified:
    - src/main/java/com/wfm/model/Agent.java
    - src/main/java/com/wfm/controller/GlobalExceptionHandler.java
decisions:
  - "AgentEligibilityService takes tenantId as parameter (not TenantContext) — callers resolve tenantId from TenantContext at request boundary; isolates service from web-tier concerns"
  - "BambooSyncEventRepository has no @Repository annotation (AppConfigurationRepository pattern); Spring Data auto-detects interface beans"
metrics:
  duration_minutes: 15
  completed_date: "2026-05-29"
  tasks_completed: 3
  files_changed: 13
---

# Phase 5 Plan 1: Schema + Model + Cross-Cutting Foundation Summary

**One-liner:** Flyway migrations V25/V26/V27 plus EmploymentType enum, JobTitleConfig/BambooSyncEvent entities and repositories, AgentEligibilityService, BambooHRRateLimitedException → 503 mapping, and H2 persistence test for Agent.employmentType.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Add Flyway migrations V25/V26/V27 | 4212377 | V25/V26/V27 .sql |
| 2 | Add EmploymentType enum, JobTitleConfig & BambooSyncEvent entities, repositories, Agent.employmentType | 107ac9b | EmploymentType.java, JobTitleConfig.java, BambooSyncEvent.java, Agent.java, 2 repos |
| 3 (test) | AgentEmploymentTypePersistenceTest (TDD RED) | 986233e | AgentEmploymentTypePersistenceTest.java |
| 3 (impl) | AgentEligibilityService, BambooHRRateLimitedException, GlobalExceptionHandler (TDD GREEN) | 75b63bb | AgentEligibilityService.java, BambooHRRateLimitedException.java, GlobalExceptionHandler.java |

## Verification Results

- `./gradlew compileJava` — BUILD SUCCESSFUL
- `./gradlew test --tests "com.wfm.model.AgentEmploymentTypePersistenceTest"` — BUILD SUCCESSFUL (2 tests pass)
- Acceptance criteria: all 17 grep-checks pass

## Deviations from Plan

None - plan executed exactly as written.

## TDD Gate Compliance

- RED gate: `test(05-01)` commit `986233e` — AgentEmploymentTypePersistenceTest
- GREEN gate: `feat(05-01)` commit `75b63bb` — AgentEligibilityService + BambooHRRateLimitedException + GlobalExceptionHandler mapping
- Note: Test passed immediately on first run because Task 2 model changes were already committed. This is expected for a persistence test that validates the model layer established in a prior task within the same plan.

## Known Stubs

None — this plan creates infrastructure only (migrations, entities, repositories, service, exception handler). No UI rendering or data flows are wired in this plan.

## Threat Flags

No new threat surface beyond what is documented in the plan's threat model (T-05-01-01 through T-05-01-06). All mitigations applied:
- T-05-01-01: All JobTitleConfigRepository methods are explicitly tenant-scoped (no findAll exposed)
- T-05-01-03: AgentEligibilityService takes tenantId as parameter, not from TenantContext
- T-05-01-06: GlobalExceptionHandler echoes ex.getMessage() for BAMBOOHR_RATE_LIMITED; Plan 02 owns the safe message construction

## Self-Check: PASSED

Files exist:
- src/main/resources/db/migration/V25__add_agent_employment_type.sql ✓
- src/main/resources/db/migration/V26__add_job_title_config.sql ✓
- src/main/resources/db/migration/V27__add_bamboo_sync_event.sql ✓
- src/main/java/com/wfm/model/EmploymentType.java ✓
- src/main/java/com/wfm/model/JobTitleConfig.java ✓
- src/main/java/com/wfm/model/BambooSyncEvent.java ✓
- src/main/java/com/wfm/repository/JobTitleConfigRepository.java ✓
- src/main/java/com/wfm/repository/BambooSyncEventRepository.java ✓
- src/main/java/com/wfm/service/AgentEligibilityService.java ✓
- src/main/java/com/wfm/exception/BambooHRRateLimitedException.java ✓
- src/test/java/com/wfm/model/AgentEmploymentTypePersistenceTest.java ✓

Commits exist: 4212377, 107ac9b, 986233e, 75b63bb ✓
