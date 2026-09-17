package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentUsualShift;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ResolvedUsualShiftTarget;
import com.wfm.model.Schedule;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.AgentUsualShiftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 17 plan 17-03, Task 3 (T-17-01/XCUT-02) — structurally proves the solver's pre-solve
 * usual-shift resolution never writes back to {@code agent_usual_shift}. Two complementary
 * assertions, because either alone leaves a hole a comment-only fix or a future mutating call
 * elsewhere in the class could slip past:
 *
 * <ol>
 *   <li><b>Behavioural</b> ({@link #resolveUsualShiftTargets_zeroMutatingInteractionsOnTheRepository}):
 *   calls {@link SolverService#resolveUsualShiftTargets} directly against a real fixture and
 *   asserts the mocked {@link AgentUsualShiftRepository} received zero mutating interactions —
 *   one explicit never-invoked assertion per mutating method the interface exposes (its own
 *   declared {@code deleteByAgent_Id}, plus every inherited {@code CrudRepository}/
 *   {@code JpaRepository} persistence and deletion method), followed by a
 *   {@code verifyNoMoreInteractions} catch-all.</li>
 *   <li><b>Structural</b> ({@link #solverServiceSource_noCodeLineInvokesAMutatingMethodOnTheUsualShiftRepository}):
 *   reads {@code SolverService.java} from the working tree, strips comment/javadoc lines, and
 *   asserts no remaining code line calls a mutating method on the {@code agentUsualShiftRepository}
 *   field anywhere in the class — not merely inside the one method under direct test.</li>
 * </ol>
 *
 * <p>{@code resolveUsualShiftTargets} itself never references {@code this.agentUsualShiftRepository}
 * at all (it operates only on the {@code allUsualShifts} list its caller already fetched via the
 * read finder in {@code startSolve}) — so the behavioural test's mock genuinely receives zero
 * interactions of any kind, and the structural test's one legitimate match in the whole class is
 * the read finder call in {@code startSolve}, which matches none of the mutating-method patterns
 * this test scans for.
 */
@ExtendWith(MockitoExtension.class)
class SolverUsualShiftWritePathGuardTest {

    private static final long TENANT_ID = 1L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7); // a real Monday

    @Mock
    private java.time.Duration defaultTimeLimit;
    @Mock
    private InMemoryScheduleStore inMemoryStore;
    @Mock
    private ai.timefold.solver.core.api.solver.SolverManager<Schedule, UUID> solverManager;
    @Mock
    private com.wfm.repository.DeskRepository deskRepository;
    @Mock
    private com.wfm.repository.AgentRepository agentRepository;
    @Mock
    private com.wfm.repository.SpecializationRepository specializationRepository;
    @Mock
    private com.wfm.repository.TimeslotRepository timeslotRepository;
    @Mock
    private com.wfm.repository.StaffingRequirementRepository staffingRequirementRepository;
    @Mock
    private com.wfm.repository.AgentPreferenceRepository agentPreferenceRepository;
    @Mock
    private com.wfm.repository.AgentDayOffRepository agentDayOffRepository;
    @Mock
    private com.wfm.repository.AgentExceptionRepository agentExceptionRepository;
    @Mock
    private com.wfm.repository.AgentDayHoursRepository agentDayHoursRepository;
    @Mock
    private com.wfm.repository.ConstraintWeightsRepository constraintWeightsRepository;
    @Mock
    private AgentEligibilityService agentEligibilityService;
    @Mock
    private com.wfm.repository.ShiftTemplateRepository shiftTemplateRepository;
    @Mock
    private com.wfm.repository.ShiftTemplateBreakBandRepository shiftTemplateBreakBandRepository;
    @Mock
    private ShiftLibraryValidationService shiftLibraryValidationService;
    @Mock
    private AgentUsualShiftRepository agentUsualShiftRepository;
    @Mock
    private UsualShiftResolutionService usualShiftResolutionService;

    @InjectMocks
    private SolverService solverService;

    // ==================================================================
    //  Behavioural — direct call, zero mutating interactions on the mock
    // ==================================================================

    @Test
    void resolveUsualShiftTargets_zeroMutatingInteractionsOnTheRepository() {
        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setName("Ana");

        ShiftTemplate template = new ShiftTemplate();
        template.setId(UUID.randomUUID());
        template.setTenantId(TENANT_ID);
        template.setName("Early");
        template.setStartTime(LocalTime.of(8, 0));
        template.setEndTime(LocalTime.of(17, 0));

        AgentUsualShift stored = new AgentUsualShift();
        stored.setId(UUID.randomUUID());
        stored.setTenantId(TENANT_ID);
        stored.setAgent(agent);
        stored.setDayOfWeek(MONDAY.getDayOfWeek());
        stored.setShiftTemplate(template);

        AgentDayConfig workingDay = new AgentDayConfig(agent.getId(), MONDAY, new BigDecimal("8.00"),
                30, 60, new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 130, 70);

        when(usualShiftResolutionService.resolve(stored, MONDAY)).thenReturn(Optional.of(template));

        List<ResolvedUsualShiftTarget> resolved = solverService.resolveUsualShiftTargets(
                List.of(stored), new Schedule(), List.of(workingDay));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).agentId()).isEqualTo(agent.getId());
        assertThat(resolved.get(0).usualStartTime()).isEqualTo(LocalTime.of(8, 0));

        // One explicit never-invoked assertion per mutating method AgentUsualShiftRepository
        // exposes -- its own declared method, plus every inherited CrudRepository/JpaRepository
        // persistence and deletion method.
        verify(agentUsualShiftRepository, never()).deleteByAgent_Id(any());
        verify(agentUsualShiftRepository, never()).save(any());
        verify(agentUsualShiftRepository, never()).saveAll(anyIterable());
        verify(agentUsualShiftRepository, never()).saveAndFlush(any());
        verify(agentUsualShiftRepository, never()).saveAllAndFlush(anyIterable());
        verify(agentUsualShiftRepository, never()).delete(any());
        verify(agentUsualShiftRepository, never()).deleteById(any());
        verify(agentUsualShiftRepository, never()).deleteAllById(anyIterable());
        verify(agentUsualShiftRepository, never()).deleteAll(anyIterable());
        verify(agentUsualShiftRepository, never()).deleteAll();
        verify(agentUsualShiftRepository, never()).deleteAllInBatch(anyIterable());
        verify(agentUsualShiftRepository, never()).deleteAllInBatch();
        verify(agentUsualShiftRepository, never()).deleteAllByIdInBatch(anyIterable());
        verify(agentUsualShiftRepository, never()).deleteInBatch(anyIterable());
        verify(agentUsualShiftRepository, never()).flush();

        // Catch-all: the mock received NO further interactions at all -- resolveUsualShiftTargets
        // never even READS through this field, since its caller (startSolve) already loaded the
        // allUsualShifts list before calling this method.
        verifyNoMoreInteractions(agentUsualShiftRepository);
    }

    // ==================================================================
    //  Structural — no code line in the whole class calls a mutating method on the field
    // ==================================================================

    private static final List<String> MUTATING_CALL_PATTERNS = List.of(
            "agentUsualShiftRepository.save(",
            "agentUsualShiftRepository.saveAll(",
            "agentUsualShiftRepository.saveAndFlush(",
            "agentUsualShiftRepository.saveAllAndFlush(",
            "agentUsualShiftRepository.delete(",
            "agentUsualShiftRepository.deleteById(",
            "agentUsualShiftRepository.deleteAllById(",
            "agentUsualShiftRepository.deleteAll(",
            "agentUsualShiftRepository.deleteAllInBatch(",
            "agentUsualShiftRepository.deleteAllByIdInBatch(",
            "agentUsualShiftRepository.deleteInBatch(",
            "agentUsualShiftRepository.flush(",
            "agentUsualShiftRepository.deleteByAgent_Id(");

    /**
     * Strips comment and javadoc lines (leading {@code //}, leading {@code *}, and {@code /*}
     * opener lines) before matching -- load-bearing because this plan's own production javadoc
     * and this very test's javadoc both discuss the prohibition in prose, naming the mutating
     * method names literally. A scanner that matched comment text would fail on its own
     * documentation rather than on a real regression.
     */
    private static List<String> stripCommentsAndJavadoc(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            result.add(line);
        }
        return result;
    }

    @Test
    void stripCommentsAndJavadoc_removesACommentLineThatWouldOtherwiseMatch() {
        List<String> lines = List.of(
                "        // agentUsualShiftRepository.save(fake); a comment naming the prohibited call",
                "         * agentUsualShiftRepository.delete(fake) -- a javadoc line naming it too",
                "        /* agentUsualShiftRepository.flush(); an opener line */",
                "        int realCodeLine = 1;");

        List<String> stripped = stripCommentsAndJavadoc(lines);

        assertThat(stripped).containsExactly("        int realCodeLine = 1;");
    }

    @Test
    void solverServiceSource_noCodeLineInvokesAMutatingMethodOnTheUsualShiftRepository() throws IOException, URISyntaxException {
        Path moduleRoot = resolveModuleRoot();
        Path solverServiceFile = moduleRoot.resolve("src/main/java/com/wfm/service/SolverService.java");
        assertThat(Files.isRegularFile(solverServiceFile))
                .as("src/main/java/com/wfm/service/SolverService.java must exist")
                .isTrue();

        List<String> allLines = Files.readAllLines(solverServiceFile, StandardCharsets.UTF_8);
        List<String> codeLines = stripCommentsAndJavadoc(allLines);

        List<String> offendingLines = new ArrayList<>();
        for (String line : codeLines) {
            for (String pattern : MUTATING_CALL_PATTERNS) {
                if (line.contains(pattern)) {
                    offendingLines.add(line.strip());
                    break;
                }
            }
        }

        assertThat(offendingLines)
                .as("SolverService must call no mutating method on agentUsualShiftRepository "
                        + "anywhere in the class (XCUT-02) -- comments/javadoc already stripped")
                .isEmpty();
    }

    private static Path resolveModuleRoot() throws URISyntaxException {
        Path testClassesDir = Path.of(
                SolverUsualShiftWritePathGuardTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDir.getParent().getParent().getParent().getParent();
    }
}
