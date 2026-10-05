package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.integration.BambooHRClient;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Desk;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code generateTemplateForDesk} — the single-desk download the Client Management page serves.
 *
 * <p>The point of one-desk-per-file is blast radius: {@code DeskAssignmentUploadService} walks
 * every sheet in an uploaded workbook and calls {@code clearDesk} for each one whose name matches
 * a desk, so a file spanning every desk rewrites every desk on re-upload. These tests pin the two
 * properties that keep that from happening — exactly ONE sheet, and the OTHER desks never loaded —
 * plus the filename, which reaches a {@code Content-Disposition} header and so must not carry
 * anything that could break out of it.
 *
 * <p>Row content is deliberately NOT re-asserted here: it comes from the same
 * {@code writeDeskSheet} the multi-desk form uses, and is pinned by
 * {@link DeskAssignmentTemplateServiceTest}. Duplicating it would create two places to update.
 */
class DeskAssignmentTemplatePerDeskTest {

    private static final long TENANT_ID = 1L;

    private DeskRepository deskRepository;
    private DeskAgentService deskAgentService;
    private AgentEligibilityService agentEligibilityService;
    private ShiftTemplateRepository shiftTemplateRepository;
    private BambooHRClient bambooHRClient;
    private DeskAssignmentTemplateService service;

    @BeforeEach
    void setUp() {
        deskRepository = mock(DeskRepository.class);
        deskAgentService = mock(DeskAgentService.class);
        agentEligibilityService = mock(AgentEligibilityService.class);
        shiftTemplateRepository = mock(ShiftTemplateRepository.class);
        bambooHRClient = mock(BambooHRClient.class);
        // No time off by default: these classes assert the blank/pre-population shapes.
        when(bambooHRClient.listTimeOff(any(), any(), any())).thenReturn(List.of());
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);
        when(shiftTemplateRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        service = new DeskAssignmentTemplateService(
                deskRepository, deskAgentService, agentEligibilityService, shiftTemplateRepository,
                bambooHRClient);
        TenantContext.setTenantId(TENANT_ID);
    }

    private Desk desk(String name) {
        Desk desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName(name);
        return desk;
    }

    private DeskAgentResponse rosterAgent(UUID deskId, String bamboohrId) {
        return new DeskAgentResponse(
                UUID.randomUUID(), deskId, bamboohrId, "Mary Watson",
                "Mary", "Watson", "mary.watson@example.com",
                "Billing", "Customer Support Representative", true,
                null, null, List.of(),
                null, null, null,
                0, List.of(), Map.of(), Map.of());
    }

    private Desk stubDesk(String name) {
        Desk d = desk(name);
        when(deskRepository.findByIdAndTenantId(d.getId(), TENANT_ID)).thenReturn(Optional.of(d));
        when(deskAgentService.listDeskAgentResponses(eq(d.getId()), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(rosterAgent(d.getId(), "6023")));
        return d;
    }

    @Test
    @DisplayName("exactly one sheet, named after the chosen desk — the identity the upload parser maps on")
    void generateTemplateForDesk_producesExactlyOneSheetNamedAfterTheDesk() throws Exception {
        Desk billing = stubDesk("Billing");

        try (XSSFWorkbook wb = new XSSFWorkbook(
                new ByteArrayInputStream(service.generateTemplateForDesk(billing.getId())))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Billing");
        }
    }

    @Test
    @DisplayName("the other desks are never even loaded — a single-desk download cannot leak another desk's roster")
    void generateTemplateForDesk_doesNotTouchOtherDesks() {
        Desk billing = stubDesk("Billing");
        Desk support = stubDesk("Support");

        service.generateTemplateForDesk(billing.getId());

        verify(deskAgentService, never())
                .listDeskAgentResponses(eq(support.getId()), eq(null), eq(null), anyInt());
        // findByTenantId is the multi-desk path's loader; the per-desk path must not call it.
        verify(deskRepository, never()).findByTenantId(anyLong());
    }

    @Test
    @DisplayName("the sheet carries the full enriched header row, same as the multi-desk form")
    void generateTemplateForDesk_writesTheHeaderRow() throws Exception {
        Desk billing = stubDesk("Billing");

        try (XSSFWorkbook wb = new XSSFWorkbook(
                new ByteArrayInputStream(service.generateTemplateForDesk(billing.getId())))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header).isNotNull();
            List<String> identity = EnrichedColumnLayout.identityHeaders();
            for (int i = 0; i < identity.size(); i++) {
                assertThat(header.getCell(i).getStringCellValue()).isEqualTo(identity.get(i));
            }
            // The roster row is present, so the sheet is seeded rather than header-only.
            assertThat(sheet.getRow(1)).isNotNull();
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("6023");
        }
    }

    @Test
    @DisplayName("a desk from another tenant is not found, rather than silently producing an empty workbook")
    void generateTemplateForDesk_unknownDeskIsRefused() {
        UUID absent = UUID.randomUUID();
        when(deskRepository.findByIdAndTenantId(absent, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateTemplateForDesk(absent))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("filename slugs the stored desk name and strips anything a Content-Disposition header could choke on")
    void templateFilenameForDesk_slugsTheStoredName() {
        assertThat(service.templateFilenameForDesk(stubDesk("Billing").getId()))
                .isEqualTo("desk-assignment-billing.xlsx");
        assertThat(service.templateFilenameForDesk(stubDesk("Phil-US Overnight (UAT-23)").getId()))
                .isEqualTo("desk-assignment-phil-us-overnight-uat-23.xlsx");
        assertThat(service.templateFilenameForDesk(stubDesk("Stubhub (EN)").getId()))
                .isEqualTo("desk-assignment-stubhub-en.xlsx");
        // A quote, semicolon, CR or LF in the desk name must not survive into the header.
        assertThat(service.templateFilenameForDesk(stubDesk("a\"b;c\r\nd").getId()))
                .isEqualTo("desk-assignment-a-b-c-d.xlsx");
    }

    @Test
    @DisplayName("a desk named only in punctuation still yields a usable filename, never a bare extension")
    void templateFilenameForDesk_nameWithNoAlphanumericsFallsBack() {
        assertThat(service.templateFilenameForDesk(stubDesk("!!!").getId()))
                .isEqualTo("desk-assignment-desk.xlsx");
    }
}
