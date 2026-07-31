package com.wfm.service;

import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.model.*;
import com.wfm.repository.*;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UPL-02: unbounded {@code Specialty N} header detection (D-06) -- ordered by
 * N, the first non-blank value becomes primary, remaining values become
 * secondary. Verified against however many Specialty columns a given sheet
 * declares.
 */
class DeskAssignmentUploadSpecialtyTest {

    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private ClientManagementService clientManagementService;
    private AgentPreferenceRepository agentPreferenceRepository;
    private AgentExceptionRepository agentExceptionRepository;
    private AgentDayHoursRepository agentDayHoursRepository;
    private SpecializationRepository specializationRepository;
    private AgentEligibilityService agentEligibilityService;
    private DeskAssignmentUploadService service;

    private static final long TENANT_ID = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();
    private static final String[] FULL_WEEK = {"8", "8", "8", "8", "8", "0", "0"};

    private Specialization chat;
    private Specialization email;
    private Specialization phone;

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        deskRepository = mock(DeskRepository.class);
        clientManagementService = mock(ClientManagementService.class);
        agentPreferenceRepository = mock(AgentPreferenceRepository.class);
        agentExceptionRepository = mock(AgentExceptionRepository.class);
        agentDayHoursRepository = mock(AgentDayHoursRepository.class);
        specializationRepository = mock(SpecializationRepository.class);
        agentEligibilityService = mock(AgentEligibilityService.class);

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository, agentDayHoursRepository,
                specializationRepository, agentEligibilityService);

        com.wfm.config.TenantContext.setTenantId(TENANT_ID);
        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
        when(agentEligibilityService.isNonSchedulable(anyLong(), isNull())).thenReturn(false);

        Desk desk = new Desk();
        desk.setId(DESK_ID);
        desk.setTenantId(TENANT_ID);
        desk.setName("Support");

        chat = specialization("Chat");
        email = specialization("Email");
        phone = specialization("Phone");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(chat, email, phone));
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Specialization specialization(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setTenantId(TENANT_ID);
        s.setDeskId(DESK_ID);
        s.setName(name);
        return s;
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private static String[] identityAndDayHeaders() {
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.dayHeader(d));
        return headers.toArray(new String[0]);
    }

    /** Builds a single-desk workbook with {@code specialtyCount} "Specialty N" trailing columns. */
    private MockMultipartFile buildWorkbook(int specialtyCount, String bamboohrId, String[] specialtyValues)
            throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Support");

        List<String> headers = new ArrayList<>(List.of(identityAndDayHeaders()));
        for (int n = 1; n <= specialtyCount; n++) headers.add("Specialty " + n);

        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.size(); c++) {
            headerRow.createCell(c).setCellValue(headers.get(c));
        }

        List<String> rowValues = new ArrayList<>(List.of(bamboohrId, "First", "Last", "Agent", "", "", ""));
        rowValues.addAll(List.of(FULL_WEEK));
        rowValues.addAll(List.of(specialtyValues));

        Row row = sheet.createRow(1);
        for (int c = 0; c < rowValues.size(); c++) {
            String value = rowValues.get(c);
            if (value == null || value.isBlank()) continue;
            if (value.matches("-?\\d+(\\.\\d+)?")) {
                row.createCell(c).setCellValue(Double.parseDouble(value));
            } else {
                row.createCell(c).setCellValue(value);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    private void stubCachedEmployee(String id, String displayName) {
        BambooEmployeeResponse cached = new BambooEmployeeResponse(id, displayName,
                displayName.toLowerCase() + "@example.com", "Support", "Agent", "Active");
        when(clientManagementService.findCachedEmployee(eq(id), isNull(), isNull())).thenReturn(cached);
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void multipleSpecialtyColumns_firstNonBlank_isPrimary_restAreSecondary() throws Exception {
        stubCachedEmployee("B1", "Alice");

        // Specialty 1 blank, Specialty 2 = Email (first non-blank -> primary), Specialty 3 = Phone (secondary)
        MockMultipartFile file = buildWorkbook(3, "B1", new String[] { "", "Email", "Phone" });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.assignedCount()).isEqualTo(1);

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        Agent saved = captor.getValue();

        assertThat(saved.getPrimarySpecialization()).isNotNull();
        assertThat(saved.getPrimarySpecialization().getName()).isEqualTo("Email");
        assertThat(saved.getSecondarySpecializations()).extracting(Specialization::getName)
                .containsExactly("Phone");
    }

    @Test
    void singleSpecialtyColumn_setsPrimaryOnly() throws Exception {
        stubCachedEmployee("B2", "Bob");

        MockMultipartFile file = buildWorkbook(1, "B2", new String[] { "Chat" });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.assignedCount()).isEqualTo(1);

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        Agent saved = captor.getValue();

        assertThat(saved.getPrimarySpecialization()).isNotNull();
        assertThat(saved.getPrimarySpecialization().getName()).isEqualTo("Chat");
        assertThat(saved.getSecondarySpecializations()).isEmpty();
    }
}
