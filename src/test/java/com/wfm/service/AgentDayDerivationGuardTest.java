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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-05/D-06's derivation-chain guard — what makes "{@code agent_shift_assignment.date} stays
 * derived, never a stored {@code business_date} column" a checked property rather than a
 * convention. SOLV-07 demands the chain be "guarded by a test rather than by convention"; this
 * class is that test, over the two-link chain: {@link com.wfm.model.AgentDayConfig#date()} is
 * sourced only from the schedule period, and {@link
 * com.wfm.model.AgentShiftAssignment#getDate()} is sourced only from an {@code AgentDayConfig} or
 * from another {@code AgentShiftAssignment}'s already-derived date (the accept-time snapshot
 * copy).
 *
 * <p><strong>A near-verbatim template, not merely a shape to imitate (read_first).</strong> {@link
 * BusinessDateWritePathGuardTest}'s two-heading design — one allowlist per writer KIND, each
 * pinned to its own entries, rather than one allowlist assuming a single legitimate caller — maps
 * directly onto this chain's two links. The scanning core, the set-equality helper, the
 * fresh-occurrence liveness test, the missing-heading loud-failure test and the deliberately-broken
 * -allowlist test are all copied from that class; retargeting the tokens (from {@code
 * setBusinessDate}/{@code getBusinessDate} to the shapes below) is the whole change.
 *
 * <p><strong>Scan A — the deriving link.</strong> Every {@code new AgentDayConfig(} construction
 * site in {@code src/main/java} is recorded by its enclosing class, matching the allowlist in
 * {@code agent-day-derivation.md} exactly (today: exactly one entry, {@code SolverService}). Beyond
 * set equality — which only proves there is one WRITER — {@link
 * #derivingSite_enclosingMethodReadsTheSchedulePeriod()} additionally reads that class's source and
 * asserts the enclosing method's body contains BOTH {@code schedule.getPeriodStartDate()} and
 * {@code schedule.getPeriodEndDate()}, so the construction's {@code date} argument is demonstrably
 * sourced from the schedule period, not merely counted as the sole writer. Without this second
 * assertion, someone could move the construction site's date argument to a calendar date and the
 * set-equality check alone would stay green.
 *
 * <p><strong>Scan B — the propagating link.</strong> A comment-stripped line holding a {@code
 * .setDate(} call (the LEADING DOT excludes the setter's own declaration, which spells {@code
 * setDate(} with a preceding space, never a dot) whose receiver token is exactly {@code sa}, or
 * contains {@code shift} case-insensitively — this codebase's naming convention for an {@link
 * com.wfm.model.AgentShiftAssignment} receiver (the receiver-token extraction technique is {@link
 * MidnightTimeArithmeticGuardTest}'s). Every matched occurrence is classified EXHAUSTIVELY by its
 * own argument text, mirroring {@link BusinessDateWritePathGuardTest}'s {@code
 * isPropagatingCall}/fallback-to-deriving shape exactly: an argument containing {@code
 * .getDate()} (an {@code AgentShiftAssignment}'s own JavaBean getter) is PROPAGATING
 * (snapshot-copy); everything else is DERIVING by default — including a hypothetical rogue
 * calendar-date argument, which is exactly the failure mode this exhaustive fallback (rather than a
 * positive-match-only classification) exists to catch rather than silently drop from both scans.
 * {@link #everySetDateOccurrence_hasOneOfTheTwoExpectedArgumentShapes()} then independently asserts
 * every occurrence's argument reads one of the two expected shapes — {@code .date()} (an {@code
 * AgentDayConfig} record accessor) for DERIVING, {@code .getDate()} for PROPAGATING — which is the
 * per-entry SOURCE-EXPRESSION check SOLV-07 requires: a future {@code AgentShiftAssignment} writer
 * whose argument is neither shape fails the build even inside an ALREADY-allowlisted class, where
 * set equality keyed on class name alone could not see a second, bad call added beside a good one.
 *
 * <p><strong>Accepted false-negative risk (same terms the four existing guards in this codebase
 * already accept for themselves).</strong> Both receiver predicates above are NAME-based, not
 * type-aware: a future {@code AgentShiftAssignment} local named neither {@code sa} nor containing
 * {@code shift} (e.g. {@code row}, {@code assignment2}) would slip past Scan B's receiver predicate
 * undetected, exactly the gap {@link MidnightTimeArithmeticGuardTest}'s own comparison heuristic
 * documents for itself.
 *
 * <p>No Spring context, no database — a purely textual, comment-stripped scan.
 */
class AgentDayDerivationGuardTest {

    private static final String RESOURCE = "agent-day-derivation.md";

    private static final String CONSTRUCTION_HEADING = "### AgentDayConfig construction sites";
    private static final String DERIVING_HEADING =
            "### AgentShiftAssignment#setDate call sites -- deriving from AgentDayConfig";
    private static final String PROPAGATING_HEADING =
            "### AgentShiftAssignment#setDate call sites -- snapshot-copy (propagating, not deriving)";

    private static final String CONSTRUCTION_TOKEN = "new AgentDayConfig(";
    private static final String SET_DATE_TOKEN = ".setDate(";

    /** An {@code AgentShiftAssignment}'s own JavaBean getter -- the PROPAGATING marker. */
    private static final String PROPAGATE_MARKER = ".getDate()";

    /** An {@code AgentDayConfig} record's accessor -- the DERIVING marker. */
    private static final String DAY_CONFIG_DATE_MARKER = ".date()";

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    // ================================================================
    //  Scan A -- the deriving link: AgentDayConfig.date <- schedule period
    // ================================================================

    @Test
    @DisplayName("AgentDayConfig construction sites match the allowlist exactly")
    void agentDayConfigConstructionSites_matchTheAllowlistExactly() throws IOException {
        Set<String> derived = scanConstructionSites();
        Set<String> allowlist = parseAllowlist(CONSTRUCTION_HEADING);
        assertSetEquality(derived, allowlist, "AgentDayConfig CONSTRUCTION");
    }

    /**
     * The second half of Scan A: set equality alone proves there is exactly one writer, not that
     * the writer reads the schedule period. This reads the enclosing method's source directly.
     */
    @Test
    @DisplayName("the deriving site's enclosing method reads the schedule period, not a calendar date")
    void derivingSite_enclosingMethodReadsTheSchedulePeriod() throws IOException {
        Set<String> allowlist = parseAllowlist(CONSTRUCTION_HEADING);
        assertThat(allowlist)
                .as("Scan A's construction allowlist must hold exactly one entry for this "
                        + "assertion to target a single class")
                .hasSize(1);
        String fqcn = allowlist.iterator().next();
        Path file = sourceFileFor(fqcn);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        int constructionLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (stripComment(lines.get(i)).contains(CONSTRUCTION_TOKEN)) {
                constructionLine = i;
                break;
            }
        }
        assertThat(constructionLine)
                .as("%s must contain the %s construction this allowlist names", fqcn, CONSTRUCTION_TOKEN)
                .isGreaterThanOrEqualTo(0);

        int[] methodRange = findEnclosingMethodLines(lines, constructionLine);
        StringBuilder methodBody = new StringBuilder();
        for (int i = methodRange[0]; i <= methodRange[1]; i++) {
            methodBody.append(stripComment(lines.get(i))).append('\n');
        }
        String body = methodBody.toString();

        assertThat(body)
                .as("the AgentDayConfig-constructing method's body must read schedule.getPeriodStartDate()")
                .contains("schedule.getPeriodStartDate()");
        assertThat(body)
                .as("the AgentDayConfig-constructing method's body must read schedule.getPeriodEndDate()")
                .contains("schedule.getPeriodEndDate()");
    }

    private static Set<String> scanConstructionSites() throws IOException {
        Set<String> classes = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String fqcn = toFullyQualifiedName(file);
                for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (stripComment(rawLine).contains(CONSTRUCTION_TOKEN)) {
                        classes.add(fqcn);
                    }
                }
            }
        }
        return classes;
    }

    /**
     * Returns the [startLine, endLineInclusive] (0-indexed) of the method body enclosing {@code
     * targetLine}, found by tracking brace depth from the top of the file. This codebase's
     * formatting places every method directly inside one top-level class body, so the only lines
     * where depth transitions from {@code <= 1} to {@code >= 2} are: the class's own opening brace
     * (stays at depth 1, never triggers), and each method's own opening brace (1 -> 2). A nested
     * block inside a method body never returns depth below 2 until the method itself closes, so
     * this simple depth tracker is safe for this file shape without full Java parsing.
     */
    private static int[] findEnclosingMethodLines(List<String> lines, int targetLine) {
        int depth = 0;
        int methodStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String code = stripComment(lines.get(i));
            int before = depth;
            for (char c : code.toCharArray()) {
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }
            if (before <= 1 && depth >= 2 && i <= targetLine) {
                methodStart = i;
            }
            if (i >= targetLine && before >= 2 && depth == 1) {
                if (methodStart < 0) {
                    throw new IllegalStateException(
                            "Could not find an enclosing method open brace before line " + targetLine);
                }
                return new int[] {methodStart, i};
            }
        }
        throw new IllegalStateException(
                "Could not find the enclosing method's closing brace for line " + targetLine);
    }

    // ================================================================
    //  Scan B -- the propagating link: AgentShiftAssignment.date <- AgentDayConfig | another sa
    // ================================================================

    @Test
    @DisplayName("deriving AgentShiftAssignment#setDate call sites match the allowlist exactly")
    void derivingSetDateCallSites_matchTheAllowlistExactly() throws IOException {
        List<SetDateOccurrence> occurrences = scanSetDateOccurrences();
        Set<String> derived = classesWhere(occurrences, o -> !o.isPropagating());
        Set<String> allowlist = parseAllowlist(DERIVING_HEADING);
        assertSetEquality(derived, allowlist, "AgentShiftAssignment#setDate DERIVING");
    }

    @Test
    @DisplayName("propagating (snapshot-copy) AgentShiftAssignment#setDate call sites match the allowlist exactly")
    void propagatingSetDateCallSites_matchTheAllowlistExactly() throws IOException {
        List<SetDateOccurrence> occurrences = scanSetDateOccurrences();
        Set<String> derived = classesWhere(occurrences, SetDateOccurrence::isPropagating);
        Set<String> allowlist = parseAllowlist(PROPAGATING_HEADING);
        assertSetEquality(derived, allowlist, "AgentShiftAssignment#setDate PROPAGATING (snapshot-copy)");
    }

    /**
     * The per-entry source-expression check SOLV-07 requires: every matched occurrence's argument
     * must read one of the two expected shapes. {@code PROPAGATE_MARKER} (an {@code .getDate()}
     * read) is what classified it PROPAGATING in the first place, so that half is tautological by
     * construction -- the meaningful half is DERIVING: an occurrence classified deriving (by the
     * exhaustive "not propagating" fallback) must ADDITIONALLY, positively read {@code .date()} (an
     * {@code AgentDayConfig} accessor). An argument that is neither shape -- a hypothetical {@code
     * sa.setDate(LocalDate.now())} -- fails this even if the class happens to already be
     * allowlisted for an unrelated, legitimate call.
     */
    @Test
    @DisplayName("every setDate occurrence's argument reads either AgentDayConfig#date() or AgentShiftAssignment#getDate()")
    void everySetDateOccurrence_hasOneOfTheTwoExpectedArgumentShapes() throws IOException {
        List<SetDateOccurrence> occurrences = scanSetDateOccurrences();
        List<String> offenders = new ArrayList<>();
        for (SetDateOccurrence o : occurrences) {
            boolean readsDayConfig = o.argument().contains(DAY_CONFIG_DATE_MARKER);
            boolean readsAnotherAssignment = o.argument().contains(PROPAGATE_MARKER);
            if (!(readsDayConfig ^ readsAnotherAssignment)) {
                offenders.add(o.fqcn() + " :: " + o.code());
            }
        }
        assertThat(offenders)
                .as("every AgentShiftAssignment#setDate call's argument must read EXACTLY one of "
                        + "AgentDayConfig#date() (deriving) or AgentShiftAssignment#getDate() "
                        + "(propagating) -- neither (a third write path) or both is unexpected: %s",
                        offenders)
                .isEmpty();
    }

    private record SetDateOccurrence(String fqcn, String code, String argument) {
        boolean isPropagating() {
            return argument.contains(PROPAGATE_MARKER);
        }
    }

    private static List<SetDateOccurrence> scanSetDateOccurrences() throws IOException {
        List<SetDateOccurrence> occurrences = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String fqcn = toFullyQualifiedName(file);
                for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String code = stripComment(rawLine);
                    if (code.isEmpty()) {
                        continue;
                    }
                    int idx = code.indexOf(SET_DATE_TOKEN);
                    if (idx < 0) {
                        continue;
                    }
                    String receiver = receiverToken(code, idx);
                    if (!isAgentShiftAssignmentReceiver(receiver)) {
                        continue;
                    }
                    String argument = extractCallArgument(code, idx);
                    if (argument == null) {
                        continue;
                    }
                    occurrences.add(new SetDateOccurrence(fqcn, code, argument));
                }
            }
        }
        return occurrences;
    }

    private static Set<String> classesWhere(List<SetDateOccurrence> occurrences,
            java.util.function.Predicate<SetDateOccurrence> predicate) {
        Set<String> classes = new LinkedHashSet<>();
        for (SetDateOccurrence o : occurrences) {
            if (predicate.test(o)) {
                classes.add(o.fqcn());
            }
        }
        return classes;
    }

    /**
     * The receiver token immediately to the left of the {@code .setDate(} token's leading dot at
     * {@code dotIndex}.
     */
    private static String receiverToken(String code, int dotIndex) {
        int end = dotIndex;
        int start = end;
        while (start > 0 && isIdentifierChar(code.charAt(start - 1))) {
            start--;
        }
        return code.substring(Math.max(start, 0), Math.max(end, 0));
    }

    /**
     * This codebase's naming convention for an {@link com.wfm.model.AgentShiftAssignment}
     * receiver: the token is exactly {@code sa}, or contains {@code shift} case-insensitively.
     * Deliberately rejects the {@code AgentPreference} receiver {@code rp}, the {@code AgentDayOff}
     * receivers {@code fact}/{@code dayOff}, the {@code Timeslot} receiver {@code snapshot}, and
     * the {@code AgentException} receiver {@code entity}.
     */
    private static boolean isAgentShiftAssignmentReceiver(String receiver) {
        if (receiver.isEmpty()) {
            return false;
        }
        if (receiver.equals("sa")) {
            return true;
        }
        return receiver.toLowerCase(Locale.ROOT).contains("shift");
    }

    /**
     * Returns the argument text of the {@code .setDate(...)} call whose token starts at {@code
     * tokenStart} (the leading dot), or {@code null} if the parentheses never balance. Balances
     * parentheses from the call's opening paren so an argument that is itself a method call (e.g.
     * {@code config.date()}) is captured whole. Mirrors {@code
     * BusinessDateWritePathGuardTest#extractCallArgument}.
     */
    private static String extractCallArgument(String code, int tokenStart) {
        int start = tokenStart + SET_DATE_TOKEN.length();
        int depth = 1;
        int i = start;
        while (i < code.length() && depth > 0) {
            char c = code.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            i++;
        }
        return depth == 0 ? code.substring(start, i - 1) : null;
    }

    // ================================================================
    //  Shared matcher/receiver liveness proofs
    // ================================================================

    @Test
    @DisplayName("the construction matcher is live: a synthetic construction matches, a comment does not")
    void constructionMatcher_isLive() {
        assertThat(stripComment("configs.add(new AgentDayConfig(").contains(CONSTRUCTION_TOKEN)).isTrue();
        assertThat(stripComment("// new AgentDayConfig( is permitted here").contains(CONSTRUCTION_TOKEN))
                .isFalse();
    }

    /**
     * Scan B's receiver predicate test (acceptance criterion): a negative verdict for each of the
     * five named non-{@code AgentShiftAssignment} receivers, and a positive verdict for the two
     * real {@code AgentShiftAssignment} receiver shapes.
     */
    @Test
    @DisplayName("the receiver predicate accepts sa/*shift* and rejects every other receiver, including the setter declaration")
    void receiverPredicate_acceptsAgentShiftAssignmentReceiversAndRejectsEveryOther() {
        assertThat(isAgentShiftAssignmentReceiver("sa")).isTrue();
        assertThat(isAgentShiftAssignmentReceiver("persistedShift")).isTrue();
        assertThat(isAgentShiftAssignmentReceiver("shiftAssignment")).isTrue();

        assertThat(isAgentShiftAssignmentReceiver("rp")).isFalse(); // AgentPreference
        assertThat(isAgentShiftAssignmentReceiver("fact")).isFalse(); // AgentDayOff
        assertThat(isAgentShiftAssignmentReceiver("dayOff")).isFalse(); // AgentDayOff
        assertThat(isAgentShiftAssignmentReceiver("snapshot")).isFalse(); // Timeslot
        assertThat(isAgentShiftAssignmentReceiver("entity")).isFalse(); // AgentException

        // The setter's own declaration never reaches the receiver predicate at all -- it has no
        // leading dot before "setDate(", so SET_DATE_TOKEN itself never matches this line.
        String declaration = "public void setDate(LocalDate date) { this.date = date; }";
        assertThat(stripComment(declaration).indexOf(SET_DATE_TOKEN)).isEqualTo(-1);
    }

    @Test
    void theScanActuallySeesProductionSource() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            assertThat(files.filter(p -> p.toString().endsWith(".java")).count())
                    .as("scan must find production sources under %s", SOURCE_ROOT)
                    .isGreaterThan(50);
        }
    }

    @Test
    void missingAllowlistHeading_failsLoudly() {
        assertThatThrownBy(() -> parseFencedBlock("# Empty resource\n\nno headings here", CONSTRUCTION_HEADING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CONSTRUCTION_HEADING);
        assertThatThrownBy(() -> parseFencedBlock("# Empty resource\n\nno headings here", DERIVING_HEADING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DERIVING_HEADING);
        assertThatThrownBy(() -> parseFencedBlock("# Empty resource\n\nno headings here", PROPAGATING_HEADING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PROPAGATING_HEADING);
    }

    @Test
    void allThreeAllowlists_parseAsNonEmpty() throws IOException {
        // An empty expected set would make each set-equality assertion above vacuously
        // satisfiable if src/main/java ever stopped writing these fields at all.
        assertThat(parseAllowlist(CONSTRUCTION_HEADING)).isNotEmpty();
        assertThat(parseAllowlist(DERIVING_HEADING)).isNotEmpty();
        assertThat(parseAllowlist(PROPAGATING_HEADING)).isNotEmpty();
    }

    /**
     * The test-of-the-test: proves each scan is actually CAPABLE of going red on both an unlisted
     * new occurrence and a stale entry, not merely that it has never been observed to. Mirrors
     * {@code BusinessDateWritePathGuardTest#deliberatelyBrokenAllowlist_isDetectedAsAMismatch} and
     * {@code MidnightTimeArithmeticGuardTest#midnightAnchorScan_failsOnBothAnUnlistedOccurrenceAndAStaleEntry}.
     */
    @Test
    @DisplayName("each scan's set-equality assertion fails on both an unlisted occurrence and a stale entry")
    void setEquality_failsOnBothAnUnlistedOccurrenceAndAStaleEntry() {
        String entry = "com.wfm.example.Example";

        assertThatThrownBy(() -> assertThat(Set.of(entry)).containsExactlyInAnyOrderElementsOf(Set.of()))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertThat(Set.<String>of()).containsExactlyInAnyOrderElementsOf(Set.of(entry)))
                .isInstanceOf(AssertionError.class);
    }

    private static void assertSetEquality(Set<String> derived, Set<String> allowlist, String kind) {
        Set<String> notAllowlisted = new HashSet<>(derived);
        notAllowlisted.removeAll(allowlist);
        Set<String> staleEntries = new HashSet<>(allowlist);
        staleEntries.removeAll(derived);

        assertThat(derived)
                .as("""
                        %s sites in src/main/java must equal the allowlist in %s exactly, in BOTH \
                        directions. Widening this assertion to a looser membership-style check \
                        turns the guard into decoration (see BusinessDateWritePathGuardTest and \
                        MidnightTimeArithmeticGuardTest).

                        NEW, not allowlisted -- a second writer of this kind is how D-05's derived \
                        value quietly stops being derived. Add a row to %s describing what the new \
                        writer guarantees before allowlisting it: %s

                        STALE, allowlisted but no longer present -- remove the entry: %s""",
                        kind, RESOURCE, RESOURCE, notAllowlisted, staleEntries)
                .containsExactlyInAnyOrderElementsOf(allowlist);
    }

    // --- shared scanning/parsing helpers ---

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

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

    private static String toFullyQualifiedName(Path file) {
        String relative = SOURCE_ROOT.relativize(file).toString();
        return relative.substring(0, relative.length() - ".java".length())
                .replace(java.io.File.separatorChar, '.');
    }

    private static Path sourceFileFor(String fqcn) {
        return SOURCE_ROOT.resolve(fqcn.replace('.', java.io.File.separatorChar) + ".java");
    }

    private static Set<String> parseAllowlist(String heading) throws IOException {
        return requireNonEmpty(parseFencedBlock(readResource(), heading), heading);
    }

    private static Set<String> requireNonEmpty(Set<String> entries, String heading) {
        if (entries.isEmpty()) {
            throw new IllegalStateException(
                    "Allowlist under heading '" + heading + "' in " + RESOURCE + " is empty -- an "
                            + "empty expected set would make the set-equality assertion vacuously "
                            + "satisfiable only when NO production class writes this field at all, "
                            + "which defeats the guard.");
        }
        return entries;
    }

    private static String readResource() throws IOException {
        try (InputStream in = AgentDayDerivationGuardTest.class
                .getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

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
