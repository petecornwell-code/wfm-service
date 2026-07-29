# Phase 6: PTO & Weekends — Pattern Map

**Mapped:** 2026-06-02
**Files analyzed:** 8 new/modified files
**Analogs found:** 8 / 8

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/com/wfm/integration/WorkingDaysParser.java` | utility (package-private) | transform | `src/main/java/com/wfm/util/BigDecimals.java` + `BambooRefreshService.mapEmploymentType` | role-match |
| `src/test/java/com/wfm/integration/WorkingDaysParserTest.java` | test | transform | `src/test/java/com/wfm/service/SolverServicePtoFilterTest.java` | exact |
| `src/main/resources/db/migration/V28__add_agent_working_days_known.sql` | migration | CRUD | `src/main/resources/db/migration/V25__add_agent_employment_type.sql` | exact |
| `src/main/java/com/wfm/integration/HttpBambooHRClient.java` (modify) | service | request-response | self | — |
| `src/main/java/com/wfm/integration/BambooEmployee.java` (modify) | model (record) | transform | self | — |
| `src/main/java/com/wfm/integration/BambooRefreshService.java` (modify) | service | CRUD | self (lines 246-296) | — |
| `src/main/java/com/wfm/integration/MockBambooHRClient.java` (modify) | service (mock) | transform | self (lines 86-104) | — |
| `src/main/java/com/wfm/service/SolverService.java` (modify) | service | CRUD | self (lines 974-981) | — |

---

## Pattern Assignments

### `src/main/java/com/wfm/integration/WorkingDaysParser.java` (utility, transform)

**Primary analog:** `src/main/java/com/wfm/util/BigDecimals.java` — style for a `final` package-scoped utility class with only static methods and a private constructor.

**Secondary analog (method-level):** `BambooRefreshService.java:79-81` — `private static EmploymentType mapEmploymentType(String status)` — the established in-package static helper pattern for string→enum mapping called from refresh logic.

**Class shell pattern** (from `BigDecimals.java:1-20`):
```java
package com.wfm.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BigDecimals {

    private BigDecimals() {}

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
```

**Adaptation for WorkingDaysParser:** use `final class` (package-private, no `public`), private constructor, all methods `static`. This matches `BigDecimals` structure but without `public` on the class declaration — keeping it package-private so only `BambooRefreshService` can call it.

**Static helper in-class pattern** (from `BambooRefreshService.java:79-81`):
```java
private static EmploymentType mapEmploymentType(String status) {
    return BAMBOO_PART_TIME.equals(status) ? EmploymentType.PART_TIME : EmploymentType.FULL_TIME;
}
```

**Null/blank guard pattern** (from `AgentEligibilityService.java:24-26`):
```java
if (jobTitle == null || jobTitle.isBlank()) {
    return false;
}
```

Apply this same guard at the top of `parseWorkingDays(String raw)` before any string operations.

**Imports to include:**
```java
package com.wfm.integration;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;
```

No Spring annotations — this is a plain static utility. No `@Component`, no `@Service`.

---

### `src/test/java/com/wfm/integration/WorkingDaysParserTest.java` (test, transform)

**Analog:** `src/test/java/com/wfm/service/SolverServicePtoFilterTest.java` — exact match on: package-private class under test called via static method, AssertJ assertions, no Spring context, helper builder methods at the bottom.

**Package declaration** (from `SolverServicePtoFilterTest.java:1`):
```java
package com.wfm.integration;
```

Note: the test lives in `src/test/java/com/wfm/integration/` — same package as `WorkingDaysParser` — giving it access to the package-private class without reflection.

**Import block pattern** (from `SolverServicePtoFilterTest.java:1-9`):
```java
import com.wfm.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
```

Adapt with `DayOfWeek` instead of model imports, and add JUnit 5 parameterized imports:
```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
```

**Test class structure** (from `SolverServicePtoFilterTest.java:24-136`):
- Class-level `private static final` constants for shared values
- Test methods named `verb_condition_expectedResult()` pattern
- `// --- Section comment ---` separator blocks between logical groups
- Private builder helpers at the bottom (equivalent to `agent()` and `dayOff()` helpers)
- No `@BeforeEach`, no Spring context, no `@ExtendWith`

**Individual `@Test` method pattern** (from `SolverServicePtoFilterTest.java:36-44`):
```java
@Test
void pto_approved_blocksDay() {
    Agent agent = agent("A1");
    AgentDayOff pto = dayOff(agent, D1, DayOffType.PTO, DayOffStatus.APPROVED);

    Map<UUID, Set<LocalDate>> map = SolverService.buildAgentDaysOffMap(List.of(pto));

    assertThat(map).containsKey(agent.getId());
    assertThat(map.get(agent.getId())).containsExactly(D1);
}
```

Adapt for `@ParameterizedTest` with `@MethodSource`:
```java
@ParameterizedTest
@MethodSource("workingDaysSource")
void parseWorkingDays_allFormats(String input, Set<DayOfWeek> expectedWorking) {
    Optional<Set<DayOfWeek>> result = WorkingDaysParser.parseWorkingDays(input);
    if (expectedWorking == null) {
        assertThat(result).isEmpty();
    } else {
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrderElementsOf(expectedWorking);
    }
}

static Stream<Arguments> workingDaysSource() {
    return Stream.of(
        Arguments.of("Mon-Fri",               Set.of(MON, TUE, WED, THU, FRI)),
        Arguments.of("Wed-Sun",               Set.of(WED, THU, FRI, SAT, SUN)),
        Arguments.of("Sun-Thu",               Set.of(SUN, MON, TUE, WED, THU)),
        Arguments.of("Tue-Sat",               Set.of(TUE, WED, THU, FRI, SAT)),
        Arguments.of("Fri-Tue",               Set.of(FRI, SAT, SUN, MON, TUE)), // week-wrap
        Arguments.of("Mon - Sun",             Set.of(MON, TUE, WED, THU, FRI, SAT, SUN)), // 0 off-days
        Arguments.of("Mon - Sun HOOP",        Set.of(MON, TUE, WED, THU, FRI, SAT, SUN)), // annotation
        Arguments.of("Mon. to Thurs.",        Set.of(MON, TUE, WED, THU)),
        Arguments.of("Mon, Tue, Wed, Thu, Sat", Set.of(MON, TUE, WED, THU, SAT)),
        Arguments.of("Variable",              null),
        Arguments.of("",                      null),
        Arguments.of(null,                    null)
    );
}
```

Use `DayOfWeek` static imports:
```java
import static java.time.DayOfWeek.*;
```

**Reflection is NOT needed** for `WorkingDaysParser` — the test is in the same package. The `BambooRefreshServiceTest` uses reflection (`m.setAccessible(true)`) only because `mapEmploymentType` is `private`. `WorkingDaysParser` methods should be package-private (`static` without `private`) so same-package tests call them directly — same pattern as `SolverService.buildAgentDaysOffMap` (package-private static called from `SolverServicePtoFilterTest` without reflection).

---

### `src/main/resources/db/migration/V28__add_agent_working_days_known.sql` (migration, CRUD)

**Analog:** `src/main/resources/db/migration/V25__add_agent_employment_type.sql` — exact match: single-column ALTER TABLE on the `agent` table with a NOT NULL DEFAULT, followed by DROP DEFAULT.

**Analog content** (`V25__add_agent_employment_type.sql:1-4`):
```sql
ALTER TABLE agent ADD COLUMN employment_type VARCHAR(20) NOT NULL DEFAULT 'FULL_TIME';

-- Per V22 convention: drop the default after backfill so new rows must set explicitly
ALTER TABLE agent ALTER COLUMN employment_type DROP DEFAULT;
```

**Pattern to copy for the boolean flag:**
```sql
ALTER TABLE agent ADD COLUMN working_days_known BOOLEAN NOT NULL DEFAULT TRUE;

-- Keep DEFAULT TRUE permanently: existing agents before first refresh must not be excluded.
-- After first BambooHR refresh, BambooRefreshService sets this explicitly.
```

Note: unlike V25, do NOT drop the default here. The `DEFAULT TRUE` must persist so agents created between deployments (before their first refresh) are not incorrectly excluded from the solver. This is the one deviation from V25's pattern, and should be commented in the migration file.

**Flyway version:** Next available is V28 (V27 is `add_bamboo_sync_event.sql`).

**Migration location:** `src/main/resources/db/migration/` — confirmed by listing above.

---

### `src/main/java/com/wfm/integration/HttpBambooHRClient.java` — modify `listEmployees` (service, request-response)

**Location of change:** lines 133-176.

**Current fields array** (line 136):
```java
"fields": ["id", "displayName", "workEmail", "department", "jobTitle", "status", "employmentHistoryStatus"]
```

**Pattern for adding a field** — add `"4517"` to the JSON array (line 136) and read the value from each `emp` node in the existing loop (after line 168):
```java
String customWorkingdays = emp.path("customWorkingdays").asText(null);
```

**`asText(null)` pattern** — used throughout the loop for nullable fields. `asText(null)` returns `null` (not empty string) when the JSON node is missing — important so `WorkingDaysParser` can distinguish missing from blank.

**Constructor call update** (lines 172-176) — add `customWorkingdays` as new positional argument to `BambooEmployee(...)`.

**Current constructor call** (lines 172-175):
```java
employees.add(new BambooEmployee(
        id, displayName, workEmail, department, jobTitle, status,
        employmentHistoryStatus,
        wfmTenantId, project
));
```

**After change:**
```java
employees.add(new BambooEmployee(
        id, displayName, workEmail, department, jobTitle, status,
        employmentHistoryStatus, customWorkingdays,
        wfmTenantId, project
));
```

---

### `src/main/java/com/wfm/integration/BambooEmployee.java` — add `customWorkingdays` field (model, transform)

**Current record** (full file, lines 1-13):
```java
package com.wfm.integration;

public record BambooEmployee(
    String id,
    String displayName,
    String workEmail,
    String department,
    String jobTitle,
    String status,
    String employmentHistoryStatus,
    String wfmTenantId,
    String project
) {}
```

**Pattern:** Insert `String customWorkingdays` after `employmentHistoryStatus` and before `wfmTenantId`. Order must match all call sites (`HttpBambooHRClient`, `MockBambooHRClient`, `BambooRefreshServiceTest.emp()` helper).

**Updated record:**
```java
public record BambooEmployee(
    String id,
    String displayName,
    String workEmail,
    String department,
    String jobTitle,
    String status,
    String employmentHistoryStatus,
    String customWorkingdays,      // NEW — raw BambooHR field 4517 value; null = not populated
    String wfmTenantId,
    String project
) {}
```

**Callers that must also be updated:**
- `MockBambooHRClient.listEmployees` (lines 92-101) — positional constructor call
- `MockBambooHRClient.buildVintedAgents` (lines 61-71) — positional constructor call
- `MockBambooHRClient.getEmployee` (line 109) — positional constructor call
- `BambooRefreshServiceTest.emp()` helper (lines 129-132) — positional constructor call

---

### `src/main/java/com/wfm/integration/BambooRefreshService.java` — modify `persistRefreshData` (service, CRUD)

**Anchor lines for modification:** 246-296.

**Current dedup map + priority logic pattern** (lines 257-286) — the MANDATORY generation loop must feed into the same `dedupedDaysOff` `LinkedHashMap` using the same `agentId + "|" + date` key format:

```java
// Existing key format (line 268):
String key = agent.getId() + "|" + timeOff.date();

// Existing priority check (lines 270-273):
if (existing == null
        || (dayOffType == DayOffType.MANDATORY && existing.getType() != DayOffType.MANDATORY)
        || (dayOffType == existing.getType() && dayOffStatus == DayOffStatus.APPROVED
                && existing.getStatus() != DayOffStatus.APPROVED)) {
```

**Existing AgentDayOff construction pattern** (lines 274-281) — copy this structure for MANDATORY rows:
```java
AgentDayOff dayOff = new AgentDayOff();
dayOff.setTenantId(tenantId);
dayOff.setAgent(agent);
dayOff.setDate(timeOff.date());
dayOff.setType(dayOffType);
dayOff.setStatus(dayOffStatus);
dedupedDaysOff.put(key, dayOff);
```

For MANDATORY rows: `setType(DayOffType.MANDATORY)`, `setStatus(DayOffStatus.APPROVED)`.

**`deleteByAgent_IdAndDateBetween` + flush pattern** (lines 249-252) — idempotency is already covered; MANDATORY rows fall within the same `[from..to]` window and are cleared on each refresh.

**Existing employee stream pattern for jobTitle** (lines 291-295) — analog for building `empByBambooId` map:
```java
employees.stream()
        .map(BambooEmployee::jobTitle)
        .filter(t -> t != null && !t.isBlank())
        .distinct()
        .forEach(title -> jobTitleConfigService.ensureExists(tenantId, title));
```

Adapt: `employees.stream().collect(Collectors.toMap(BambooEmployee::id, e -> e, (a, b) -> a))` for the `empByBambooId` lookup map.

**Insertion point:** The MANDATORY generation block goes BEFORE the existing `dedupedDaysOff` map declaration and PTO loop (i.e., immediately after the `agentDayOffRepository.flush()` call at line 252), so MANDATORY entries are in the map before `putIfAbsent` would be reached by the PTO loop.

**Dead code to remove** (line 263):
```java
DayOffType dayOffType = "MANDATORY".equalsIgnoreCase(type)
        || "holiday".equalsIgnoreCase(type)
        ? DayOffType.MANDATORY : DayOffType.PTO;
```
Remove the `"MANDATORY".equalsIgnoreCase(type)` branch entirely. Keep `"holiday".equalsIgnoreCase(type)` → `DayOffType.MANDATORY` if holidays from BambooHR are still wanted; otherwise clean to just `DayOffType.PTO` for all time-off entries.

---

### `src/main/java/com/wfm/integration/MockBambooHRClient.java` — add `customWorkingdays` (mock, transform)

**Location of constructor calls:** `listEmployees` loop lines 92-101, `buildVintedAgents` loop lines 61-71, `getEmployee` line 109.

**Existing index-modulo pattern for varying values** (lines 68-69, 99):
```java
i % 5 == 0 ? "Part-Time" : "Full-time"
```

Reuse this same `i % 5` pattern to emit varied `customWorkingdays` values. Add after the existing `employmentHistoryStatus` argument:

```java
// New customWorkingdays argument (index-based variation):
String customWorkingdays = switch (i % 5) {
    case 0 -> "Mon-Fri";
    case 1 -> "Sun-Thu";
    case 2 -> "Tue-Sat";
    case 3 -> "Variable";     // data gap — tests D-07 exclusion
    default -> "Mon. to Thurs.";  // 4-day week — outlier flag
};
```

For `getEmployee` (line 109) which constructs a single `BambooEmployee` without an index, use `"Mon-Fri"` as a fixed default.

---

### `src/main/java/com/wfm/service/SolverService.java` — extend `filterEligible` (service, CRUD)

**Location:** lines 974-981.

**Current `filterEligible`** (lines 974-981):
```java
static List<Agent> filterEligible(List<Agent> agents, long tenantId,
                                   AgentEligibilityService agentEligibilityService) {
    return agents.stream()
            .filter(Agent::isActive)
            .filter(a -> !agentEligibilityService.isNonSchedulable(tenantId, a.getJobTitle()))
            .filter(a -> a.getPrimarySpecialization() != null)
            .toList();
}
```

**Pattern for adding a fourth filter criterion** — append a new `.filter(...)` after the existing three, preserving the same chained style. Do NOT change the method signature.

**After change:**
```java
static List<Agent> filterEligible(List<Agent> agents, long tenantId,
                                   AgentEligibilityService agentEligibilityService) {
    return agents.stream()
            .filter(Agent::isActive)
            .filter(a -> !agentEligibilityService.isNonSchedulable(tenantId, a.getJobTitle()))
            .filter(a -> a.getPrimarySpecialization() != null)
            .filter(Agent::isWorkingDaysKnown)  // D-07: exclude agents with no working-days data
            .toList();
}
```

**`SolverServiceEligibilityFilterTest`** already tests the first three filters. Extend it by adding a new `@Test` group (following the same `agent(bool, String, Specialization)` builder helper pattern at line 123) for the `workingDaysKnown` criterion — an agent with `workingDaysKnown=false` must be excluded even when active with a primary spec and a schedulable title.

---

## Shared Patterns

### Null/blank guard
**Source:** `AgentEligibilityService.java:24-26`
**Apply to:** `WorkingDaysParser.parseWorkingDays()` entry point, and any method in `BambooRefreshService` that reads `emp.customWorkingdays()`
```java
if (raw == null || raw.isBlank()) return Optional.empty();
```

### dedup map key format
**Source:** `BambooRefreshService.java:268`
**Apply to:** MANDATORY row generation loop (must use identical key to participate in priority resolution)
```java
String key = agent.getId() + "|" + cursor;  // cursor = LocalDate
```

### AgentDayOff construction
**Source:** `BambooRefreshService.java:274-281`
**Apply to:** MANDATORY row construction inside the new generation loop
```java
AgentDayOff dayOff = new AgentDayOff();
dayOff.setTenantId(tenantId);
dayOff.setAgent(agent);
dayOff.setDate(/* cursor */);
dayOff.setType(DayOffType.MANDATORY);
dayOff.setStatus(DayOffStatus.APPROVED);
dedupedDaysOff.putIfAbsent(key, dayOff);
```

### AssertJ assertion style
**Source:** `SolverServicePtoFilterTest.java` and `SolverServiceEligibilityFilterTest.java`
**Apply to:** `WorkingDaysParserTest.java`
```java
assertThat(result).isPresent();
assertThat(result.get()).containsExactlyInAnyOrderElementsOf(expectedWorking);
assertThat(result).isEmpty();
```

### Test helper builder at bottom of class
**Source:** `SolverServicePtoFilterTest.java:116-135`, `SolverServiceEligibilityFilterTest.java:114-152`
**Apply to:** `WorkingDaysParserTest` — if needed, place any DayOfWeek set builder helpers at the bottom, after the `@ParameterizedTest` methods.

---

## No Analog Found

No files in this phase lack a codebase analog. All patterns have direct matches.

---

## Metadata

**Analog search scope:** `src/main/java/com/wfm/integration/`, `src/main/java/com/wfm/service/`, `src/main/java/com/wfm/util/`, `src/main/java/com/wfm/model/`, `src/test/java/com/wfm/`, `src/main/resources/db/migration/`
**Files scanned:** ~20 source files, 27 migration files
**Pattern extraction date:** 2026-06-02
