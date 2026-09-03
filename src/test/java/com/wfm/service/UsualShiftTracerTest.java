package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.controller.DeskAgentController;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Agent;
import com.wfm.model.AgentUsualShift;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.AgentUsualShiftRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tracer for Phase 16: an agent's usual shift on MONDAY, through every layer, with no
 * mocked layer -- store ({@link UsualShiftService}) -> roster read ({@link DeskAgentService}) ->
 * Excel export ({@link DeskAgentExportService}). Modelled on {@code ShiftTemplateTracerTest}'s
 * controller/service/repository/H2/DTO shape, with {@link DeskAgentController} deliberately NOT
 * imported (it drags in {@code BambooRefreshService} and four other services) -- the endpoint
 * itself is asserted by reflection (the last test method).
 */
@DataJpaTest
@Import({UsualShiftService.class, DeskAgentService.class, UsualShiftResolutionService.class,
        DeskAgentExportService.class})
@ActiveProfiles("test")
class UsualShiftTracerTest {

    @Autowired
    private UsualShiftService usualShiftService;

    @Autowired
    private DeskAgentService deskAgentService;

    @Autowired
    private DeskAgentExportService deskAgentExportService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private DeskRepository deskRepository;

    @Autowired
    private ShiftTemplateRepository shiftTemplateRepository;

    @Autowired
    private AgentUsualShiftRepository agentUsualShiftRepository;

    private static final long TENANT_ID = 1L;

    private Desk desk;
    private Agent agent;
    private ShiftTemplate early;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        desk = saveDesk("Support Desk");

        agent = new Agent();
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId("B100");
        agent.setName("Jane Doe");
        agent.setDeskId(desk.getId());
        agent = agentRepository.save(agent);

        // Mon-Fri, effective from yesterday, open-ended -- a CURRENT era per D-17's picker rule.
        early = saveTemplate(desk.getId(), "Early",
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalDate.now().minusDays(1), null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Desk saveDesk(String name) {
        Desk d = new Desk();
        d.setTenantId(TENANT_ID);
        d.setName(name);
        return deskRepository.save(d);
    }

    private ShiftTemplate saveTemplate(UUID deskId, String name, Set<DayOfWeek> weekdays,
                                        LocalDate effectiveFrom, LocalDate effectiveTo) {
        ShiftTemplate template = new ShiftTemplate();
        template.setTenantId(TENANT_ID);
        template.setDeskId(deskId);
        template.setName(name);
        template.setStartTime(LocalTime.of(8, 0));
        template.setEndTime(LocalTime.of(17, 0));
        template.setValidWeekdays(weekdays);
        template.setEffectiveFrom(effectiveFrom);
        template.setEffectiveTo(effectiveTo);
        return shiftTemplateRepository.save(template);
    }

    @Test
    void happyPath_storeRosterExport_endToEnd() throws Exception {
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, early.getId(), false);

        DeskAgentResponse single = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());
        assertThat(single.usualShift().get(DayOfWeek.MONDAY))
                .isEqualTo(new DeskAgentResponse.UsualShiftEntry(
                        DeskAgentResponse.UsualShiftStatus.LIVE, "Early", null));
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            if (day == DayOfWeek.MONDAY) {
                continue;
            }
            assertThat(single.usualShift().get(day))
                    .as("weekday %s", day)
                    .isEqualTo(new DeskAgentResponse.UsualShiftEntry(
                            DeskAgentResponse.UsualShiftStatus.NOT_SET, null, null));
        }

        List<DeskAgentResponse> roster = deskAgentService.listDeskAgentResponses(desk.getId(), null, null, 50);
        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).usualShift().get(DayOfWeek.MONDAY).name()).isEqualTo("Early");

        byte[] xlsx = deskAgentExportService.exportDeskAgentsToExcel(roster);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet("Desk Agents");
            Row header = sheet.getRow(0);
            assertThat(header.getCell(19).getStringCellValue())
                    .isEqualTo(EnrichedColumnLayout.dayHeader(DayOfWeek.SUNDAY));
            assertThat(header.getCell(20).getStringCellValue())
                    .isEqualTo(EnrichedColumnLayout.usualShiftHeader(DayOfWeek.MONDAY));
            assertThat(header.getCell(26).getStringCellValue())
                    .isEqualTo(EnrichedColumnLayout.usualShiftHeader(DayOfWeek.SUNDAY));
            assertThat(header.getCell(27).getStringCellValue())
                    .isEqualTo(EnrichedColumnLayout.COL_FIRST_NAME);
            assertThat(header.getCell(28).getStringCellValue())
                    .isEqualTo(EnrichedColumnLayout.COL_LAST_NAME);

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(20).getStringCellValue()).isEqualTo("Early");
            for (int i = 21; i <= 26; i++) {
                Cell cell = dataRow.getCell(i);
                assertThat(cell == null || cell.getCellType() == CellType.BLANK)
                        .as("cell %d should be blank or absent", i).isTrue();
            }
        }
    }

    @Test
    void clearRow_deletesTheRow_rosterReadsNotSetAgain() {
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, early.getId(), false);

        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, null, true);

        DeskAgentResponse response = deskAgentService.getDeskAgentResponse(desk.getId(), agent.getId());
        assertThat(response.usualShift().get(DayOfWeek.MONDAY))
                .isEqualTo(new DeskAgentResponse.UsualShiftEntry(
                        DeskAgentResponse.UsualShiftStatus.NOT_SET, null, null));
        assertThat(agentUsualShiftRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    @Test
    void clearUsualShifts_oneRowLeavesZero_zeroRowsIsANoOp() {
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, early.getId(), false);
        assertThat(agentUsualShiftRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).hasSize(1);

        usualShiftService.clearUsualShifts(agent.getId());
        assertThat(agentUsualShiftRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();

        // Calling again on an already-empty agent is a no-op, not an error.
        usualShiftService.clearUsualShifts(agent.getId());
        assertThat(agentUsualShiftRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    @Test
    void weekdayMaskViolation_isRejected() {
        // D-03: "Early" is not valid on SATURDAY.
        assertThatThrownBy(() ->
                usualShiftService.setUsualShift(
                        desk.getId(), agent.getId(), DayOfWeek.SATURDAY, early.getId(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retiredTemplate_isRejected() {
        // P-03: a template whose effectiveTo is in the past cannot be stored through the choke point.
        ShiftTemplate retired = saveTemplate(desk.getId(), "Retired",
                Set.of(DayOfWeek.MONDAY), LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));

        assertThatThrownBy(() ->
                usualShiftService.setUsualShift(
                        desk.getId(), agent.getId(), DayOfWeek.MONDAY, retired.getId(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrongDesk_throwsEntityNotFound_andWritesNoRow() {
        // T-13-05/T-16-01: the agent is scoped to `desk`, not `otherDesk`.
        Desk otherDesk = saveDesk("Other Desk");

        assertThatThrownBy(() ->
                usualShiftService.setUsualShift(
                        otherDesk.getId(), agent.getId(), DayOfWeek.MONDAY, early.getId(), false))
                .isInstanceOf(EntityNotFoundException.class);
        assertThat(agentUsualShiftRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId())).isEmpty();
    }

    @Test
    void crossDeskTemplate_isRejected() {
        // T-16-02: a template belonging to a different desk must never be findById'd in.
        Desk otherDesk = saveDesk("Other Desk");
        ShiftTemplate otherDeskTemplate = saveTemplate(otherDesk.getId(), "Early",
                Set.of(DayOfWeek.MONDAY), LocalDate.now().minusDays(1), null);

        assertThatThrownBy(() ->
                usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY,
                        otherDeskTemplate.getId(), false))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void nullShiftTemplateIdAndNotClear_isRejected() {
        // USHF-03/empty: neither a template nor clearRow is supplied.
        assertThatThrownBy(() ->
                usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatedSetForSameWeekday_leavesExactlyOneRow() {
        // USHF-03/adjacency.
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, early.getId(), false);
        usualShiftService.setUsualShift(desk.getId(), agent.getId(), DayOfWeek.MONDAY, early.getId(), false);

        List<AgentUsualShift> rows = agentUsualShiftRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId());
        assertThat(rows).hasSize(1);
    }

    @Test
    void controller_declaresExactlyOnePutMapping_forUsualShiftEndpoint() {
        long matches = 0;
        for (Method method : DeskAgentController.class.getDeclaredMethods()) {
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            if (mapping == null) {
                continue;
            }
            for (String value : mapping.value()) {
                if ("/{agentId}/usual-shift/{day}".equals(value)) {
                    matches++;
                }
            }
        }
        assertThat(matches).isEqualTo(1);
    }
}
