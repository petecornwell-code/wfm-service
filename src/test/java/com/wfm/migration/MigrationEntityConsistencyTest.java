package com.wfm.migration;

import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.AgentUsualShift;
import com.wfm.model.ConstraintWeights;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * P-07: closes the G-14-1 migration-vs-entity blind spot without a new dependency.
 * {@code src/test/resources/application-test.yml} sets {@code flyway.enabled: false} with {@code
 * ddl-auto: create-drop} against H2, so no test in this repository executes migration SQL — the
 * test schema is instead built directly from the JPA entity mappings. V39 shipped
 * {@code valid_weekdays CHAR(7)} against an entity mapped to {@code varchar(7)}: the migration
 * applied cleanly and the application then failed to boot under {@code ddl-auto=validate}, with a
 * fully green 402-test suite (UAT gap G-14-1).
 *
 * <p>This plain JUnit 5 test — no Spring context, no Testcontainers, no database — reads the
 * migration files directly off the classpath, folds their {@code CREATE TABLE} / {@code ALTER
 * TABLE ... ADD COLUMN} / {@code DROP COLUMN} statements in version order into an effective
 * column map per table, and reflects over each declared entity's {@code @Column} mappings to
 * assert every entity column exists in the effective migration map and maps to a compatible SQL
 * type. A Testcontainers-backed boot test remains the fuller answer; this phase declines it only
 * because it would add a dependency this milestone forbids.
 *
 * <p>The declared table list below is a constant a later phase extends, not a scan of every
 * entity — the guard's value is being exact and cheap, not exhaustive.
 */
class MigrationEntityConsistencyTest {

    /**
     * table name -> entity class this test reconciles it against.
     *
     * <p>{@code constraint_weights} added in Phase 15 plan 15-06 (T-15-24): V42's new
     * {@code band_capacity_weight} column is reconciled against {@link ConstraintWeights}'s
     * {@code bandCapacityWeight} field here rather than surfacing at boot. Its {@code HardSoftScore}
     * -typed fields are not in {@link #COMPATIBLE_SQL_TYPES} (no entry for that type), so this test
     * only asserts column *existence*, not a type match, for every weight column -- deliberately:
     * {@code HardSoftScoreConverter} maps every weight field to a single {@code VARCHAR}, and this
     * test's type table has no vocabulary for "converted via a JPA AttributeConverter", only for
     * plain scalar mappings.
     *
     * <p>{@code schedule} added for the CR-02 gap closure (V43): the new {@code scheduling_mode}
     * column is reconciled against {@link Schedule}'s {@code schedulingMode} field here rather
     * than surfacing at boot, same rationale as {@code constraint_weights} above. {@code schedule}
     * accumulates columns across many earlier migrations (V1, V2, V20) that this table's own
     * fold-in-version-order logic already handles correctly -- {@code score} (V2's merge of
     * {@code hard_score}/{@code soft_score} into one column) and {@code version} (V20) both
     * resolve to existing columns the same way {@code scheduling_mode} does now.
     */
    private static final Map<String, Class<?>> DECLARED_TABLES = Map.of(
            "shift_template", ShiftTemplate.class,
            "shift_template_break_band", ShiftTemplateBreakBand.class,
            "agent_shift_assignment", AgentShiftAssignment.class,
            "constraint_weights", ConstraintWeights.class,
            "schedule", Schedule.class,
            "agent_usual_shift", AgentUsualShift.class
    );

    /** Java field type -> SQL types it may legitimately be declared as. */
    private static final Map<Class<?>, Set<String>> COMPATIBLE_SQL_TYPES = Map.ofEntries(
            Map.entry(String.class, Set.of("VARCHAR", "TEXT")),
            Map.entry(int.class, Set.of("INTEGER", "BIGINT")),
            Map.entry(Integer.class, Set.of("INTEGER", "BIGINT")),
            Map.entry(long.class, Set.of("BIGINT")),
            Map.entry(Long.class, Set.of("BIGINT")),
            Map.entry(UUID.class, Set.of("UUID")),
            Map.entry(LocalTime.class, Set.of("TIME")),
            Map.entry(LocalDate.class, Set.of("DATE")),
            Map.entry(BigDecimal.class, Set.of("NUMERIC", "DECIMAL"))
    );

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE TABLE\\s+(\\w+)\\s*\\((.*?)\\)\\s*;", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /**
     * One whole {@code ALTER TABLE <name> <clauses>;} statement, clauses captured as a single
     * blob to be split on top-level commas afterwards (T-15-24 fix). A single {@code ALTER TABLE}
     * keyword can introduce MULTIPLE comma-separated {@code ADD COLUMN}/{@code DROP COLUMN}
     * clauses in one statement (Postgres syntax, used extensively by {@code V2} for
     * {@code constraint_weights}) — the previous per-clause regex only ever matched the clause
     * immediately following the {@code ALTER TABLE} keyword itself, silently dropping every
     * subsequent {@code ADD COLUMN}/{@code DROP COLUMN} clause in the same statement. That bug
     * was latent because no table using multi-column {@code ALTER TABLE} was ever added to
     * {@link #DECLARED_TABLES} until {@code constraint_weights} (Phase 15, ENVL board T-15-24) —
     * adding it surfaced the gap rather than introducing a new one.
     */
    private static final Pattern ALTER_TABLE_STATEMENT = Pattern.compile(
            "ALTER TABLE\\s+(\\w+)\\s+(.*?);", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ADD_COLUMN_CLAUSE = Pattern.compile(
            "^ADD COLUMN\\s+(\\w+)\\s+([A-Za-z]+(?:\\(\\d+(?:,\\s*\\d+)?\\))?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_COLUMN_CLAUSE = Pattern.compile(
            "^DROP COLUMN\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_LINE = Pattern.compile(
            "^(\\w+)\\s+([A-Za-z]+(?:\\(\\d+(?:,\\s*\\d+)?\\))?)");
    private static final Pattern MIGRATION_FILE_NAME = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Set<String> CONSTRAINT_KEYWORDS =
            Set.of("PRIMARY", "UNIQUE", "FOREIGN", "CONSTRAINT", "CHECK");

    @Test
    void migrationDeclaredColumns_reconcileWithEntityMappings() throws IOException, URISyntaxException {
        Map<String, Map<String, String>> effectiveColumns = buildEffectiveColumnMap();

        for (Map.Entry<String, Class<?>> entry : DECLARED_TABLES.entrySet()) {
            String table = entry.getKey();
            Class<?> entityClass = entry.getValue();
            Map<String, String> migrationColumns = effectiveColumns.getOrDefault(table, Map.of());

            for (Field field : entityClass.getDeclaredFields()) {
                if (isTransientOrStatic(field)) {
                    continue;
                }
                String columnName = columnName(field);
                String sqlType = migrationColumns.get(columnName);
                if (sqlType == null) {
                    fail("Entity " + entityClass.getSimpleName() + " declares column '" + columnName
                            + "' on table '" + table + "' that no migration creates.");
                }
                Set<String> compatible = COMPATIBLE_SQL_TYPES.get(field.getType());
                if (compatible != null && !compatible.contains(sqlType)) {
                    fail("Entity " + entityClass.getSimpleName() + " field '" + field.getName()
                            + "' (" + field.getType().getSimpleName() + ") maps to column '" + columnName
                            + "' declared as SQL type '" + sqlType + "' on table '" + table
                            + "', which is not compatible (expected one of " + compatible + ").");
                }
            }
        }
    }

    @Test
    void v40DroppedColumns_absentFromMigrationMapAndEntity() throws IOException, URISyntaxException {
        Map<String, Map<String, String>> effectiveColumns = buildEffectiveColumnMap();
        Map<String, String> shiftTemplateColumns = effectiveColumns.getOrDefault("shift_template", Map.of());

        assertThat(shiftTemplateColumns).doesNotContainKey("break_offset_minutes");
        assertThat(shiftTemplateColumns).doesNotContainKey("break_duration_minutes");

        Set<String> entityColumnNames = Arrays.stream(ShiftTemplate.class.getDeclaredFields())
                .filter(f -> !isTransientOrStatic(f))
                .map(MigrationEntityConsistencyTest::columnName)
                .collect(Collectors.toSet());
        assertThat(entityColumnNames).doesNotContain("break_offset_minutes", "break_duration_minutes");
    }

    // --- migration folding ---

    private Map<String, Map<String, String>> buildEffectiveColumnMap() throws IOException, URISyntaxException {
        Map<String, Map<String, String>> effective = new HashMap<>();
        for (Path migrationFile : sortedMigrationFiles()) {
            String sql = stripLineComments(Files.readString(migrationFile, StandardCharsets.UTF_8));
            applyCreateTables(sql, effective);
            applyAlterColumns(sql, effective);
        }
        return effective;
    }

    /**
     * Strips SQL {@code --} line comments before any other parsing. This project's migration
     * header comments routinely embed commas and parens in prose (e.g. "VARCHAR(7), not
     * CHAR(7)") that would otherwise corrupt the top-level-comma column splitter below — commit
     * this project's own G-14-1 migration comment discipline as a reason this step is mandatory,
     * not optional.
     */
    private static String stripLineComments(String sql) {
        return sql.replaceAll("--[^\\n]*", "");
    }

    private void applyCreateTables(String sql, Map<String, Map<String, String>> effective) {
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            String body = matcher.group(2);
            Map<String, String> columns = effective.computeIfAbsent(table, t -> new LinkedHashMap<>());
            for (String rawLine : splitTopLevelCommaList(body)) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String firstWord = line.split("\\s+")[0].toUpperCase(Locale.ROOT);
                if (CONSTRAINT_KEYWORDS.contains(firstWord)) {
                    continue;
                }
                Matcher colMatcher = COLUMN_LINE.matcher(line);
                if (colMatcher.find()) {
                    String columnName = colMatcher.group(1).toLowerCase(Locale.ROOT);
                    String sqlType = normalizeType(colMatcher.group(2));
                    columns.put(columnName, sqlType);
                }
            }
        }
    }

    /**
     * Folds every {@code ALTER TABLE} statement's comma-separated clause list (T-15-24 fix — see
     * {@link #ALTER_TABLE_STATEMENT}'s javadoc). {@code ADD COLUMN}/{@code DROP COLUMN} clauses
     * are applied in the order they appear; any other clause (e.g. {@code ALTER COLUMN ... SET
     * NOT NULL}/{@code SET DEFAULT}) is intentionally ignored — it changes a constraint or
     * default this test doesn't model, never column existence.
     */
    private void applyAlterColumns(String sql, Map<String, Map<String, String>> effective) {
        Matcher statementMatcher = ALTER_TABLE_STATEMENT.matcher(sql);
        while (statementMatcher.find()) {
            String table = statementMatcher.group(1).toLowerCase(Locale.ROOT);
            String clauseList = statementMatcher.group(2);
            Map<String, String> columns = effective.computeIfAbsent(table, t -> new LinkedHashMap<>());
            for (String rawClause : splitTopLevelCommaList(clauseList)) {
                String clause = rawClause.trim();
                if (clause.isEmpty()) {
                    continue;
                }
                Matcher addMatcher = ADD_COLUMN_CLAUSE.matcher(clause);
                if (addMatcher.find()) {
                    String column = addMatcher.group(1).toLowerCase(Locale.ROOT);
                    String sqlType = normalizeType(addMatcher.group(2));
                    columns.put(column, sqlType);
                    continue;
                }
                Matcher dropMatcher = DROP_COLUMN_CLAUSE.matcher(clause);
                if (dropMatcher.find()) {
                    String column = dropMatcher.group(1).toLowerCase(Locale.ROOT);
                    columns.remove(column);
                }
            }
        }
    }

    /** Splits a CREATE TABLE body on top-level commas only, respecting nested parens (e.g. UNIQUE (a, b)). */
    private static List<String> splitTopLevelCommaList(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (c == '(') {
                depth++;
            }
            if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String normalizeType(String rawType) {
        String withoutSize = rawType.replaceAll("\\(.*\\)", "").trim().toUpperCase(Locale.ROOT);
        return "INT".equals(withoutSize) ? "INTEGER" : withoutSize;
    }

    private static boolean isTransientOrStatic(Field field) {
        return Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class);
    }

    private static String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isBlank()) {
            return column.name();
        }
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        if (joinColumn != null && !joinColumn.name().isBlank()) {
            return joinColumn.name();
        }
        return camelToSnake(field.getName());
    }

    private static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private List<Path> sortedMigrationFiles() throws IOException, URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("db/migration");
        if (resource == null) {
            throw new IllegalStateException("db/migration classpath resource not found");
        }
        Path dir = Path.of(resource.toURI());
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> MIGRATION_FILE_NAME.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(MigrationEntityConsistencyTest::versionOf))
                    .toList();
        }
    }

    private static int versionOf(Path path) {
        Matcher m = MIGRATION_FILE_NAME.matcher(path.getFileName().toString());
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        return Integer.MAX_VALUE;
    }
}
