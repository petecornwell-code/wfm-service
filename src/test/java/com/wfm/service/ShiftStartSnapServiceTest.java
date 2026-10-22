package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.Agent;
import com.wfm.model.AgentPreference;
import com.wfm.model.Desk;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preferred starts on a shift-scheduled desk are moved to the nearest shift start, because an agent
 * can only start where a shift starts -- Vinted's test data asked for 16:00 against shifts starting
 * 08:00-15:00, a guaranteed violation once Preferred Start (Shift Mode) is weighted hard.
 */
@DataJpaTest
@Import({ShiftStartSnapService.class, PreferenceUploadService.class, AgentPreferenceService.class})
@ActiveProfiles("test")
class ShiftStartSnapServiceTest {

    @Autowired private ShiftStartSnapService snapService;
    @Autowired private PreferenceUploadService uploadService;
    @Autowired private AgentPreferenceService preferenceService;
    @Autowired private DeskRepository deskRepository;
    @Autowired private ShiftTemplateRepository templateRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentPreferenceRepository preferenceRepository;

    private static final long TENANT = 1L;
    private static final LocalDate MON = LocalDate.of(2026, 10, 26);
    private static final LocalDate SUN = LocalDate.of(2026, 11, 1);

    private UUID deskId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        Desk desk = new Desk();
        desk.setTenantId(TENANT);
        desk.setName("Vinted test " + UUID.randomUUID());
        desk.setSchedulingMode(SchedulingMode.SHIFT);
        deskId = deskRepository.save(desk).getId();
        template("First-Shift", 8, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 1, 1), null);
        template("Fourth-Shift", 11, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 1, 1), null);
        template("Fifth-Shift", 12, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 1, 1), null);
        template("Eighth-Shift", 15, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2026, 1, 1), null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void template(String name, int startHour, EnumSet<DayOfWeek> days, LocalDate from, LocalDate to) {
        ShiftTemplate t = new ShiftTemplate();
        t.setTenantId(TENANT);
        t.setDeskId(deskId);
        t.setName(name);
        t.setStartTime(LocalTime.of(startHour, 0));
        t.setEndTime(LocalTime.of((startHour + 9) % 24, 0));
        t.setValidWeekdays(days);
        t.setEffectiveFrom(from);
        t.setEffectiveTo(to);
        templateRepository.save(t);
    }

    private ShiftStartSnapService.Snapper snapper() {
        return snapService.forDesk(TENANT, deskId);
    }

    @Test
    void startWithNoShift_movesToTheNearestShiftStart() {
        var snap = snapper().snap(LocalTime.of(16, 0), MON, DayOfWeek.MONDAY).orElseThrow();

        assertThat(snap.original()).isEqualTo(LocalTime.of(16, 0));
        assertThat(snap.snapped()).isEqualTo(LocalTime.of(15, 0));
        assertThat(snap.templateName()).isEqualTo("Eighth-Shift");
    }

    @Test
    void startThatMatchesAShift_isLeftAlone() {
        assertThat(snapper().snap(LocalTime.of(11, 0), MON, DayOfWeek.MONDAY)).isEmpty();
    }

    @Test
    void exactTie_goesToTheEarlierStart() {
        // 11:30 is 30 minutes from both 11:00 and 12:00.
        assertThat(snapper().snap(LocalTime.of(11, 30), MON, DayOfWeek.MONDAY).orElseThrow().snapped())
                .isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void onlyShiftsUsableThatDay_count_weekdayAndEffectiveDates() {
        // A 16:00 shift that does not run on Sundays, and one that only starts next year: neither
        // may be chosen for Sunday 1 Nov 2026, so 16:30 still lands on Eighth-Shift.
        template("Weekday-16", 16, EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalDate.of(2026, 1, 1), null);
        template("Future-16", 16, EnumSet.allOf(DayOfWeek.class), LocalDate.of(2027, 1, 1), null);

        assertThat(snapper().snap(LocalTime.of(16, 30), SUN, DayOfWeek.SUNDAY).orElseThrow().templateName())
                .isEqualTo("Eighth-Shift");
        // ...while on Monday the weekday-only 16:00 shift is usable and 16:00 already matches it.
        assertThat(snapper().snap(LocalTime.of(16, 0), MON, DayOfWeek.MONDAY)).isEmpty();
    }

    @Test
    void slotScheduledDesk_isNeverChanged() {
        Desk desk = deskRepository.findById(deskId).orElseThrow();
        desk.setSchedulingMode(SchedulingMode.SLOT);
        deskRepository.save(desk);

        assertThat(snapper().snap(LocalTime.of(16, 0), MON, DayOfWeek.MONDAY)).isEmpty();
    }

    @Test
    void spreadsheetUpload_savesTheSnappedStart_andReportsIt() throws Exception {
        Agent agent = new Agent();
        agent.setTenantId(TENANT);
        agent.setBamboohrId("B1");
        agent.setName("Vadym Pohut");
        agent.setEmail("vadym.pohut@example.com");
        agent.setDeskId(deskId);
        agent = agentRepository.save(agent);

        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Preferences");
            var header = sheet.createRow(0);
            String[] heads = {"Agent", "Email", "Date", "Day of week", "Preferred start", "Standing", "Preferred break"};
            for (int i = 0; i < heads.length; i++) header.createCell(i).setCellValue(heads[i]);
            var r1 = sheet.createRow(1);                       // no shift at 16:00 -> moved
            r1.createCell(0).setCellValue("Vadym Pohut");
            r1.createCell(1).setCellValue("vadym.pohut@example.com");
            r1.createCell(2).setCellValue("2026-10-26");
            r1.createCell(3).setCellValue("MONDAY");
            r1.createCell(4).setCellValue("16:00");
            r1.createCell(5).setCellValue("no");
            var r2 = sheet.createRow(2);                       // matches First-Shift -> unchanged
            r2.createCell(0).setCellValue("Vadym Pohut");
            r2.createCell(1).setCellValue("vadym.pohut@example.com");
            r2.createCell(2).setCellValue("2026-10-27");
            r2.createCell(3).setCellValue("TUESDAY");
            r2.createCell(4).setCellValue("08:00");
            r2.createCell(5).setCellValue("no");
            wb.write(out);
            xlsx = out.toByteArray();
        }

        var result = uploadService.uploadPreferences(deskId,
                new MockMultipartFile("file", "prefs.xlsx", null, xlsx));

        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(result.adjustedCount()).isEqualTo(1);
        assertThat(result.adjustedDetails().get(0))
                .contains("Vadym Pohut").contains("2026-10-26").contains("16:00 moved to 15:00").contains("Eighth-Shift");
        AgentPreference monday = preferenceRepository
                .findByTenantIdAndDeskIdAndAgent_IdAndIsStandingFalseAndDate(TENANT, deskId, agent.getId(), MON).orElseThrow();
        assertThat(monday.getPreferredStartTime()).isEqualTo(LocalTime.of(15, 0));
        // A blank break is worked out from the start that was actually saved.
        assertThat(monday.getPreferredBreakTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void prefsPageSave_savesTheSnappedStart_andSaysWhatItWasBefore() {
        Agent agent = new Agent();
        agent.setTenantId(TENANT);
        agent.setBamboohrId("B2");
        agent.setName("Agent Two");
        agent.setEmail("two@example.com");
        agent.setDeskId(deskId);
        agent = agentRepository.save(agent);

        var saved = preferenceService.savePreferences(deskId, agent.getId(), List.of(
                new com.wfm.dto.PreferenceResponse(null, DayOfWeek.SUNDAY, null, true, LocalTime.of(16, 0), null),
                new com.wfm.dto.PreferenceResponse(null, DayOfWeek.MONDAY, null, true, LocalTime.of(12, 0), null)));

        assertThat(saved).hasSize(2);
        var sunday = saved.stream().filter(p -> p.dayOfWeek() == DayOfWeek.SUNDAY).findFirst().orElseThrow();
        assertThat(sunday.preferredStartTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(sunday.adjustedFrom()).isEqualTo(LocalTime.of(16, 0));
        var monday = saved.stream().filter(p -> p.dayOfWeek() == DayOfWeek.MONDAY).findFirst().orElseThrow();
        assertThat(monday.preferredStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(monday.adjustedFrom()).isNull();
    }
}
