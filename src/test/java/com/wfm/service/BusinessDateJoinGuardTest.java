package com.wfm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SOLV-02 / SOLV-07's structural join guard (D-08): the set of {@code join}/{@code equal}/
 * {@code groupBy}/{@code computeIfAbsent} key positions in {@code src/main/java} that resolve a
 * {@link com.wfm.model.Timeslot}'s calendar {@code getDate()} must equal, in BOTH directions, the
 * allowlist in {@code src/test/resources/bday-join-guard.md} — shipped EMPTY, since D-08 predicts
 * this scope is "plausibly empty for key positions" and this test is what PROVES that rather than
 * assumes it.
 *
 * <p><strong>A new class, not a fourth scan in {@link MidnightTimeArithmeticGuardTest}
 * (Claude's Discretion, D-08).</strong> That class's three existing scans already share a
 * {@code scanProductionSources}/{@code parseFencedBlock}-style technique and one resource file;
 * this guard copies that TECHNIQUE into a fresh file rather than extending the original, following
 * {@link BusinessDateWritePathGuardTest}'s own precedent of doing exactly that for BDAY-02. The
 * reason is the same here: the token and receiver heuristics below detect a different bug class
 * (calendar-vs-business-date confusion at a join key) from raw {@code LocalTime} interval
 * arithmetic, and conflating the two under one file name and one javadoc would make neither
 * javadoc stay focused on one bug class.
 *
 * <p><strong>Scope: two explicit file lists, never a tree walk.</strong> The verb-scoped list
 * {@link #TARGET_FILES} is {@code ScheduleConstraintProvider}, {@code ScheduleOutputService},
 * {@code ShiftLibraryGenerationService} (D-13) and {@code StaffingRequirementService} (D-15, the
 * demand-upload delete-range file), scanned with the four-verb predicate below. The widened list
 * {@link #WIDENED_TARGET_FILES} (Phase 24 D-07) is {@code ShiftLibraryValidationService}, {@code
 * ScheduleEnvelopeRepairService}, {@code ScheduleConsistencyRepairService} and {@code
 * ShiftStartMixTargetService}, scanned with a VERB-FREE predicate: those four files hold no
 * legitimate calendar-date Timeslot read once migrated, so the verb gate that keeps the first list
 * quiet is unnecessary there, and N-1 slipped past it precisely because the validator read the
 * calendar date through {@code .map(} and a plain local assignment, never a join verb.
 * Deliberately NOT a whole-{@code src/main/java} walk like {@link MidnightTimeArithmeticGuardTest}
 * uses: {@code groupBy} and {@code computeIfAbsent} fire constantly outside date handling
 * elsewhere in this codebase, and an ungated tree walk would demand the 100-plus-entry allowlist
 * Phase 18's D-03 measured and rejected as decoration (see {@code 18-CONTEXT.md}).
 *
 * <p><strong>Verb predicate.</strong> A comment-stripped line containing one of the four literal
 * tokens {@code join(}, {@code equal(}, {@code groupBy(}, {@code computeIfAbsent(} — D-08's locked
 * scope, nothing wider.
 *
 * <p><strong>Receiver predicate — three shapes, and why only these three.</strong> A {@code
 * .getDate()} read whose receiver is: (1) the chained accessor form ending {@code .getTimeslot()}
 * (any variable, e.g. {@code a.getTimeslot().getDate()}); (2) a bare variable named exactly {@code
 * ts}; or (3) the literal method reference {@code Timeslot::getDate}. Every genuine {@link
 * com.wfm.model.Timeslot}-typed {@code .getDate()} occurrence found in the four guarded files today
 * spells its receiver as one of these three shapes, with no counter-example in the tree — verified
 * directly, not assumed. The accepted false-negative risk, carried on the same terms {@link
 * MidnightTimeArithmeticGuardTest}'s own comparison heuristic already accepts for itself: a future
 * {@code Timeslot}-typed local named something other than {@code ts} (e.g. {@code t}, {@code slot})
 * would slip past this receiver predicate undetected. A bare {@code sa.getDate()} is deliberately
 * EXCLUDED — {@code sa} is an {@link com.wfm.model.AgentShiftAssignment}, already business-date
 * shaped by D-05's design, and flagging it would be a false positive demanding a pointless
 * allowlist entry.
 *
 * <p><strong>Why {@code .getDayOfWeek()}, {@code .plusDays(} and {@code ChronoUnit.DAYS} are not
 * scanned (D-09).</strong> {@code Timeslot} is the only type in this model carrying two competing
 * dates. The other three candidate tokens D-04 of {@code 18-CONTEXT.md} considered would fire
 * overwhelmingly on types with no competing calendar-date meaning — the same decoration-guard
 * failure mode the four-file scope restriction above exists to avoid.
 *
 * <p><strong>Set equality only, in BOTH directions, exactly as the four existing guards in this
 * codebase already enforce</strong> ({@link MidnightTimeArithmeticGuardTest}, {@link
 * BusinessDateWritePathGuardTest}, {@code UsualShiftWritePathGuardTest}, {@code
 * SolverUsualShiftWritePathGuardTest}). Widening the assertion below to a looser membership-style
 * check (subset, any-match, or a bare {@code .contains(...)}) turns the guard into decoration: a
 * new, unlisted calendar-date join would pass silently, and a stale entry for a line already fixed
 * would never be caught either.
 *
 * <p><strong>The allowlist is shipped EMPTY on purpose.</strong> An empty expected set makes a
 * naive set-equality assertion vacuously satisfiable the moment every guarded file is migrated — so
 * this class carries two INDEPENDENT liveness proofs that do not depend on the allowlist's
 * contents at all, one matcher proof and one pipeline proof per scan: {@link
 * #theMatcherDetectsEachReceiverShapeAndRejectsNonKeyPositions()} proves the verb-scoped matcher can
 * return both {@code true} and {@code false}, {@link
 * #pipelineRedProof_walkStripMatchAndSetCompareAreAllLive()} proves the whole walk-strip-match-
 * compare pipeline is live against a tracked, never-compiled synthetic offender, {@link
 * #theWidenedMatcherCatchesVerbFreeTimeslotDateReadsTheVerbScanMisses()} proves the same of the
 * widened verb-free matcher, and {@link
 * #widenedPipelineRedProof_walkStripMatchAndSetCompareAreAllLive()} proves the widened pipeline is
 * live against its own offender under {@link #OFFENDER_ROOT_WIDENED}. The widened scope landed RED
 * (fourteen unmigrated sites across the four widened files) and was turned green by migration in
 * plans 24-01 and 24-02, never by allowlisting.
 *
 * <p>No Spring context, no database — a purely textual, comment-stripped scan, mirroring every
 * precedent guard in this codebase. A false positive (an unrelated method literally spelled {@code
 * .getDate()} on a chained {@code .getTimeslot()}-named receiver) fails safe: a human looks at a
 * diff. It cannot see arithmetic or a join key hidden behind a helper method in another class —
 * but such a helper would itself have to spell one of the four verb tokens, and would be caught
 * there.
 */
class BusinessDateJoinGuardTest {

    private static final String RESOURCE = "bday-join-guard.md";
    private static final String ALLOWLIST_HEADING = "### Allowlist";

    /** D-08's locked verb scope — nothing wider, nothing narrower. */
    private static final List<String> JOIN_VERB_TOKENS = List.of(
            "join(", "equal(", "groupBy(", "computeIfAbsent(");

    /** The literal method-reference receiver shape (D-09 shape 3). */
    private static final String TIMESLOT_METHOD_REFERENCE = "Timeslot::getDate";

    /** The chained-accessor receiver shape (D-09 shape 1) — any receiver ending in this call. */
    private static final String CHAINED_TIMESLOT_ACCESSOR = ".getTimeslot()";

    /** The bare-variable receiver shape (D-09 shape 2) — exactly this identifier, nothing else. */
    private static final String BARE_TIMESLOT_VARIABLE = "ts";

    private static final String GET_DATE_CALL = ".getDate()";

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** D-08's explicit four-file scope — never a tree walk. */
    private static final List<Path> TARGET_FILES = List.of(
            SOURCE_ROOT.resolve(Path.of("com", "wfm", "solver", "ScheduleConstraintProvider.java")),
            SOURCE_ROOT.resolve(Path.of("com", "wfm", "service", "ScheduleOutputService.java")),
            SOURCE_ROOT.resolve(Path.of("com", "wfm", "service", "ShiftLibraryGenerationService.java")),
            SOURCE_ROOT.resolve(Path.of("com", "wfm", "service", "StaffingRequirementService.java")));

    /**
     * Phase 24 D-07's widened scope — an explicit four-file list, never a tree walk (Phase 20 D-08 /
     * Phase 18 D-03). These files hold no legitimate calendar-date Timeslot read once migrated, so
     * they are scanned with {@link #isWidenedTimeslotDateRead}, which has NO verb requirement. The
     * verb-scoped {@link #TARGET_FILES} keep their verb gate because {@code ScheduleOutputService}'s
     * D-10 calendar labels would otherwise fire.
     */
    private static final List<Path> WIDENED_TARGET_FILES = List.of(
            SOURCE_ROOT.resolve(Path.of("com", "wfm", "service", "ShiftLibraryValidationService.java")),
            SOURCE_ROOT.resolve(Path.of("com", "wfm", "service", "ScheduleEnvelopeRepairService.java")),
            SOURCE_ROOT.resolve(Path.of("com", "wfm", "service", "ScheduleConsistencyRepairService.java")),
            SOURCE_ROOT.resolve(Path.of("com", "wfm", "service", "ShiftStartMixTargetService.java")));

    /**
     * The fixture root for the pipeline-level red-proof below, mirroring {@link
     * MidnightTimeArithmeticGuardTest}'s {@code COMPARISON_OFFENDER_ROOT}. Lives under {@code
     * src/test/resources} — never {@code src/main/java} — so a killed or crashed run cannot leave
     * a deliberately offending {@code .java} file inside a compiled source set.
     */
    private static final Path OFFENDER_ROOT = Path.of("src", "test", "resources", "bday-join-guard-offender");

    /**
     * The fixture root for the WIDENED pipeline red-proof (Phase 24 D-07): a tracked, never-compiled
     * offender holding one verb-free calendar-date Timeslot read, so the empty allowlist stays honest
     * for the widened scan the same way {@link #OFFENDER_ROOT} keeps it honest for the verb scan.
     */
    private static final Path OFFENDER_ROOT_WIDENED =
            Path.of("src", "test", "resources", "bday-join-guard-offender-widened");

    @Test
    @DisplayName("business-date join key positions across the verb-scoped and widened guarded files match the allowlist exactly")
    void businessDateJoinKeyPositionsInProductionCode_matchTheAllowlistExactly() throws IOException {
        Set<String> derived = new LinkedHashSet<>(
                scanFiles(TARGET_FILES, BusinessDateJoinGuardTest::isBusinessDateJoinKeyPosition));
        derived.addAll(scanFiles(WIDENED_TARGET_FILES, BusinessDateJoinGuardTest::isWidenedTimeslotDateRead));
        Set<String> allowlist = parseAllowlist();

        Set<String> notAllowlisted = new HashSet<>(derived);
        notAllowlisted.removeAll(allowlist);
        Set<String> staleEntries = new HashSet<>(allowlist);
        staleEntries.removeAll(derived);

        assertThat(derived)
                .as("""
                        Business-date join key positions (join/equal/groupBy/computeIfAbsent \
                        resolving a Timeslot's calendar getDate(), in the verb-scoped files, and \
                        ANY Timeslot calendar getDate() read in the widened files) across the \
                        verb-scoped and widened guarded files must equal the allowlist in %s \
                        exactly, in BOTH directions.

                        NEW, not allowlisted -- these are un-migrated key positions still joining \
                        on calendar date where SOLV-01/SOLV-07 require business date. Re-point the \
                        Timeslot side to getBusinessDate(), or if the site genuinely must stay on \
                        calendar date, add it to the allowlist WITH a reasoned note: %s

                        STALE, allowlisted but no longer present -- remove the entry: %s""",
                        RESOURCE, notAllowlisted, staleEntries)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    /**
     * Matcher-liveness proof (a): every shape the combined matcher must accept or reject, taken
     * verbatim from this guard's own behaviour contract. Proves the PREDICATE can go both ways
     * before the pipeline-level proof below proves the whole walk can too.
     */
    @Test
    @DisplayName("the matcher detects each of the three receiver shapes and rejects every non-key-position")
    void theMatcherDetectsEachReceiverShapeAndRejectsNonKeyPositions() {
        // Shape 1: chained accessor ending .getTimeslot().getDate() -- TRUE.
        assertThat(isBusinessDateJoinKeyPosition(
                "equal(a -> a.getTimeslot().getDate(), AgentDayOff::getDate))")).isTrue();
        // Shape 2: bare variable named exactly ts -- TRUE.
        assertThat(isBusinessDateJoinKeyPosition(
                "timeslotsByDate.computeIfAbsent(ts.getDate(), k -> new ArrayList<>())")).isTrue();
        // Shape 3: literal Timeslot::getDate method reference, inside a .join( line -- TRUE.
        assertThat(isBusinessDateJoinKeyPosition(
                ".join(Timeslot.class, equal((sa, cfg) -> sa.getDate(), Timeslot::getDate))")).isTrue();

        // A bare `sa` receiver is an AgentShiftAssignment -- already business-date-shaped, FALSE
        // even though groupBy( is present.
        assertThat(isBusinessDateJoinKeyPosition(
                ".groupBy((sa, cfg) -> sa.getDate(), (sa, cfg) -> sa.getShiftBandPair(), countBi())"))
                .isFalse();
        // A commented-out occurrence -- FALSE, comment-stripped to nothing.
        assertThat(isBusinessDateJoinKeyPosition(
                "// equal(a -> a.getTimeslot().getDate(), AgentDayOff::getDate))")).isFalse();
        // A string-concatenated display label -- FALSE, concatenation is not one of the four verbs,
        // even though the receiver shape (bare ts) would otherwise match (D-10).
        assertThat(isBusinessDateJoinKeyPosition(
                "String timeslotLabel = ts.getDate() + \" \" + ts.getStartTime();")).isFalse();

        // Verb present, but neither a chained .getTimeslot(), a bare ts, nor a Timeslot:: method
        // reference -- FALSE. (AgentDayConfig::date is a different type's accessor entirely.)
        assertThat(isBusinessDateJoinKeyPosition(
                "equal((daId, date, cnt) -> date, AgentDayConfig::date)")).isFalse();
    }

    /**
     * {@link #theMatcherDetectsEachReceiverShapeAndRejectsNonKeyPositions} proves the matcher
     * PREDICATE is live against synthetic strings; this proves walk, comment-strip, match and
     * set-compare are all live TOGETHER, exactly mirroring {@link
     * MidnightTimeArithmeticGuardTest#pipelineRedProof_walkStripMatchAndSetCompareAreAllLive}. If
     * the final assertion above were ever weakened from set equality to a subset check, the
     * matcher-liveness proof would still pass -- it never reaches the set-compare step. This test
     * points the shared scan at {@link #OFFENDER_ROOT}, a tracked, never-compiled synthetic
     * offender, and proves the whole pipeline together.
     */
    @Test
    @DisplayName("the guard can go red through its whole pipeline, not only its matcher")
    void pipelineRedProof_walkStripMatchAndSetCompareAreAllLive() throws IOException {
        List<Path> fixtureFiles;
        try (Stream<Path> files = Files.walk(OFFENDER_ROOT)) {
            fixtureFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(fixtureFiles)
                .as("the pipeline red-proof fixture must hold exactly one .java file under %s", OFFENDER_ROOT)
                .hasSize(1);

        Set<String> derived = scanFiles(fixtureFiles, BusinessDateJoinGuardTest::isBusinessDateJoinKeyPosition);
        assertThat(derived)
                .as("walk + comment-strip + match against the fixture must yield exactly one entry "
                        + "-- a second entry would mean comment-stripping silently stopped working")
                .hasSize(1);

        assertThatThrownBy(() -> assertThat(derived).containsExactlyInAnyOrderElementsOf(Set.of()))
                .isInstanceOf(AssertionError.class);
    }

    /**
     * The widened scan's own pipeline red-proof (Phase 24 D-07). {@link
     * #pipelineRedProof_walkStripMatchAndSetCompareAreAllLive} proves the verb-scoped pipeline; the
     * widened scan has a different predicate and a different file list, so it needs its own proof
     * that an EMPTY allowlist is not vacuous. The fixture's single real line carries no join verb,
     * so the verb-scoped predicate must NOT see it -- the widening, not the verb scan, is what
     * catches a verb-free calendar-date Timeslot read.
     */
    @Test
    @DisplayName("the widened guard can go red through its whole pipeline, not only its matcher")
    void widenedPipelineRedProof_walkStripMatchAndSetCompareAreAllLive() throws IOException {
        List<Path> fixtureFiles;
        try (Stream<Path> files = Files.walk(OFFENDER_ROOT_WIDENED)) {
            fixtureFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(fixtureFiles)
                .as("the widened pipeline red-proof fixture must hold exactly one .java file under %s",
                        OFFENDER_ROOT_WIDENED)
                .hasSize(1);

        Set<String> derived = scanFiles(fixtureFiles, BusinessDateJoinGuardTest::isWidenedTimeslotDateRead);
        assertThat(derived)
                .as("walk + comment-strip + widened match against the fixture must yield exactly one "
                        + "entry -- a second entry would mean comment-stripping silently stopped working")
                .hasSize(1);

        assertThat(scanFiles(fixtureFiles, BusinessDateJoinGuardTest::isBusinessDateJoinKeyPosition))
                .as("the fixture's offending line carries no join verb, so the verb-scoped predicate "
                        + "must miss it -- otherwise this proof says nothing about the widening")
                .isEmpty();

        assertThatThrownBy(() -> assertThat(derived).containsExactlyInAnyOrderElementsOf(Set.of()))
                .isInstanceOf(AssertionError.class);
    }

    /**
     * Proves the set-COMPARISON itself rejects both directions -- an unlisted new occurrence, and
     * an allowlisted entry whose line has gone -- using synthetic sets rather than a real scan.
     * Mirrors {@code MidnightTimeArithmeticGuardTest.midnightAnchorScan_failsOnBothAnUnlistedOccurrenceAndAStaleEntry}.
     */
    @Test
    @DisplayName("the set-equality assertion fails on both an unlisted occurrence and a stale entry")
    void setEquality_failsOnBothAnUnlistedOccurrenceAndAStaleEntry() {
        String line = "com.wfm.example.Example :: equal(a -> a.getTimeslot().getDate(), Example::getDate))";

        // NEW direction: one real occurrence, allowlist empty -- must fail.
        assertThatThrownBy(() -> assertThat(Set.of(line)).containsExactlyInAnyOrderElementsOf(Set.of()))
                .isInstanceOf(AssertionError.class);

        // STALE direction: allowlist carries the entry, derived is empty -- must fail just as loudly.
        assertThatThrownBy(() -> assertThat(Set.<String>of()).containsExactlyInAnyOrderElementsOf(Set.of(line)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("a missing allowlist heading fails loudly rather than parsing to an empty set silently")
    void missingAllowlistHeading_failsLoudly() {
        assertThatThrownBy(() -> parseFencedBlock("# Empty resource\n\nno headings here", ALLOWLIST_HEADING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ALLOWLIST_HEADING);
        assertThatThrownBy(() -> parseFencedBlock("# Empty resource\n\nno headings here", "### No Such Heading"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Such Heading");
    }

    @Test
    void allGuardedFilesExist() {
        for (Path file : TARGET_FILES) {
            assertThat(Files.exists(file)).as("guarded file must exist: %s", file).isTrue();
        }
        for (Path file : WIDENED_TARGET_FILES) {
            assertThat(Files.exists(file)).as("widened guarded file must exist: %s", file).isTrue();
        }
    }

    /**
     * Phase 24 D-07 matcher proof: the widened, verb-free predicate catches the three validator
     * lines the verb scan missed (N-1), copied verbatim from HEAD before the fix, and still rejects
     * every non-Timeslot or non-code shape.
     */
    @Test
    @DisplayName("the widened matcher catches verb-free Timeslot date reads the verb scan misses")
    void theWidenedMatcherCatchesVerbFreeTimeslotDateReadsTheVerbScanMisses() {
        String[] preFixValidatorLines = {
                ".map(sr -> new Window(sr.getTimeslot().getDate(),",
                ".map(sr -> sr.getTimeslot().getDate())",
                "LocalDate date = sr.getTimeslot().getDate();"
        };
        for (String line : preFixValidatorLines) {
            assertThat(isWidenedTimeslotDateRead(line))
                    .as("widened matcher must catch: %s", line).isTrue();
            assertThat(isBusinessDateJoinKeyPosition(line))
                    .as("the verb scan must miss it (that is N-1): %s", line).isFalse();
        }

        // A bare sa.getDate() is an AgentShiftAssignment -- already business-date shaped.
        assertThat(isWidenedTimeslotDateRead("LocalDate d = sa.getDate();")).isFalse();
        // A commented-out chained Timeslot date read -- comment-stripped to nothing.
        assertThat(isWidenedTimeslotDateRead("// LocalDate date = sr.getTimeslot().getDate();")).isFalse();
        // The business-date accessor is the migrated form.
        assertThat(isWidenedTimeslotDateRead("LocalDate date = sr.getTimeslot().getBusinessDate();")).isFalse();
    }

    // --- scanning ---

    /**
     * Scans an explicit list of files (never a directory tree walk for the real target lists,
     * though the pipeline red-proof above reuses this against the single-file offender fixture
     * too), returning every comment-stripped line accepted by {@code matcher} — {@link
     * #isBusinessDateJoinKeyPosition} for the verb-scoped list, {@link #isWidenedTimeslotDateRead}
     * for the widened one. Mirrors {@link
     * MidnightTimeArithmeticGuardTest#scanProductionSources}'s scan body shape, adapted to walk an
     * explicit file list instead of a root directory.
     */
    private static Set<String> scanFiles(List<Path> files, Predicate<String> matcher) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException(
                        "Guarded file not found: " + file + " -- the guard cannot scan a key "
                                + "position scope that does not exist");
            }
            String fqcn = toFullyQualifiedName(file);
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String code = stripComment(rawLine);
                if (matcher.test(rawLine)) {
                    found.add(fqcn + " :: " + code);
                }
            }
        }
        return found;
    }

    /**
     * The combined matcher: D-08's four-verb predicate AND D-09's three-shape receiver predicate,
     * both evaluated on the same comment-stripped line.
     */
    private static boolean isBusinessDateJoinKeyPosition(String rawLine) {
        String code = stripComment(rawLine);
        if (code.isEmpty()) {
            return false;
        }
        return hasJoinVerb(code) && hasTimeslotReceiverGetDate(code);
    }

    /**
     * Phase 24 D-07's widened matcher: any comment-stripped line holding a {@code .getDate()} read
     * on one of the three Timeslot receiver shapes, with NO verb requirement.
     */
    private static boolean isWidenedTimeslotDateRead(String rawLine) {
        String code = stripComment(rawLine);
        if (code.isEmpty()) {
            return false;
        }
        return hasTimeslotReceiverGetDate(code);
    }

    private static boolean hasJoinVerb(String code) {
        return JOIN_VERB_TOKENS.stream().anyMatch(code::contains);
    }

    /**
     * True when {@code code} holds a {@code .getDate()} read on one of D-09's three Timeslot
     * receiver shapes. The method reference shape is checked as a standalone substring (it has no
     * {@code .getDate()} call form at all); the chained and bare-variable shapes are checked at
     * every {@code .getDate()} occurrence on the line, since a line like {@code
     * shiftEnvelopeCompliance}'s line 510 holds both a non-matching bare {@code sa.getDate()} and a
     * matching chained {@code a.getTimeslot().getDate()} together.
     */
    private static boolean hasTimeslotReceiverGetDate(String code) {
        if (code.contains(TIMESLOT_METHOD_REFERENCE)) {
            return true;
        }
        int from = 0;
        while (true) {
            int pos = code.indexOf(GET_DATE_CALL, from);
            if (pos < 0) {
                break;
            }
            if (precededByChainedTimeslotAccessor(code, pos) || BARE_TIMESLOT_VARIABLE.equals(receiverName(code, pos))) {
                return true;
            }
            from = pos + 1;
        }
        return false;
    }

    /** True when the text immediately before {@code tokenStart} is exactly {@code .getTimeslot()}. */
    private static boolean precededByChainedTimeslotAccessor(String code, int tokenStart) {
        int markerStart = tokenStart - CHAINED_TIMESLOT_ACCESSOR.length();
        return markerStart >= 0 && code.startsWith(CHAINED_TIMESLOT_ACCESSOR, markerStart);
    }

    /**
     * The identifier immediately to the left of a {@code .getDate()} token starting at {@code
     * tokenStart}, tolerating one trailing empty argument list (mirrors {@code
     * MidnightTimeArithmeticGuardTest#receiverName}) so both a bare field and a zero-arg accessor
     * resolve to a single name -- the receiver, not any qualifier further left.
     */
    private static String receiverName(String code, int tokenStart) {
        int p = tokenStart - 1;
        if (p >= 1 && code.charAt(p) == ')' && code.charAt(p - 1) == '(') {
            p -= 2;
        }
        int end = p + 1;
        int start = end;
        while (start > 0 && isIdentifierChar(code.charAt(start - 1))) {
            start--;
        }
        return code.substring(Math.max(start, 0), Math.max(end, 0));
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Returns the code portion of a source line: empty for a line that is wholly a comment
     * ({@code //}, a javadoc continuation {@code *}, or a block opener {@code /*}), and otherwise
     * the line trimmed with any trailing {@code //} comment removed. Mirrors {@code
     * MidnightTimeArithmeticGuardTest#stripComment}.
     */
    private static String stripComment(String rawLine) {
        String line = rawLine.strip();
        if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
            return "";
        }
        int inlineComment = line.indexOf("//");
        if (inlineComment >= 0) {
            line = line.substring(0, inlineComment);
        }
        return line.strip();
    }

    /** Relativises {@code file} against {@link #SOURCE_ROOT} when under it, else against its own
     *  immediate fixture root (the offender directory) -- the pipeline red-proof points this scan
     *  at a different root than the real target-file scope. */
    private static String toFullyQualifiedName(Path file) {
        Path root = file.startsWith(SOURCE_ROOT) ? SOURCE_ROOT : file.getParent();
        String relative = root.relativize(file).toString();
        return relative.substring(0, relative.length() - ".java".length())
                .replace(java.io.File.separatorChar, '.');
    }

    // --- resource parsing ---

    /**
     * Unlike every precedent guard's allowlist, this one is EXPECTED to parse to an empty set
     * today (D-08) -- so, deliberately, there is no {@code requireNonEmpty} guard here. The two
     * liveness proofs above are what keep an empty allowlist from making the set-equality
     * assertion vacuous instead.
     */
    private static Set<String> parseAllowlist() throws IOException {
        return parseFencedBlock(readResource(), ALLOWLIST_HEADING);
    }

    private static String readResource() throws IOException {
        try (InputStream in = BusinessDateJoinGuardTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Reads the first fenced code block following {@code heading}, one entry per non-blank line. */
    private static Set<String> parseFencedBlock(String markdown, String heading) {
        List<String> lines = markdown.lines().toList();
        int headingIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(heading)) {
                headingIndex = i;
                break;
            }
        }
        if (headingIndex < 0) {
            throw new IllegalStateException("Heading '" + heading + "' not found in " + RESOURCE);
        }
        List<String> collected = new ArrayList<>();
        boolean inFence = false;
        for (int i = headingIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.strip().startsWith("```")) {
                if (inFence) {
                    break;
                }
                inFence = true;
                continue;
            }
            if (inFence && !line.isBlank()) {
                collected.add(line.strip());
            }
        }
        if (!inFence) {
            throw new IllegalStateException("No fenced block found after heading '" + heading + "' in " + RESOURCE);
        }
        return new LinkedHashSet<>(collected);
    }
}
