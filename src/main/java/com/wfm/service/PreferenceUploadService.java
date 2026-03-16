package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.PreferenceResponse;
import com.wfm.model.Agent;
import com.wfm.model.AgentPreference;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskAgentRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PreferenceUploadService {

    private final AgentRepository agentRepository;
    private final DeskAgentRepository deskAgentRepository;
    private final AgentPreferenceRepository agentPreferenceRepository;

    public PreferenceUploadService(AgentRepository agentRepository,
                                    DeskAgentRepository deskAgentRepository,
                                    AgentPreferenceRepository agentPreferenceRepository) {
        this.agentRepository = agentRepository;
        this.deskAgentRepository = deskAgentRepository;
        this.agentPreferenceRepository = agentPreferenceRepository;
    }

    @Transactional
    public PreferenceUploadResult uploadPreferences(UUID deskId, MultipartFile file) throws IOException {
        long tenantId = TenantContext.getTenantId();

        List<PreferenceResponse> saved = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Spreadsheet has no sheets");
            }

            // Expect headers in row 0: Employee Name, Email, Date, Day of Week, Preferred Start Time, Standing Preference
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String email = getCellString(row.getCell(1));
                if (email == null || email.isBlank()) {
                    skipped.add("Row " + (i + 1) + ": missing email");
                    continue;
                }

                // Find agent by email
                var agentOpt = agentRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email.trim());
                if (agentOpt.isEmpty()) {
                    skipped.add("Row " + (i + 1) + ": agent not found for email " + email);
                    continue;
                }
                Agent agent = agentOpt.get();

                // Verify agent is assigned to the desk
                var deskAgentOpt = deskAgentRepository.findByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agent.getId());
                if (deskAgentOpt.isEmpty()) {
                    skipped.add("Row " + (i + 1) + ": agent " + email + " not assigned to desk");
                    continue;
                }

                String dateStr = getCellString(row.getCell(2));
                String dayOfWeekStr = getCellString(row.getCell(3));
                String startTimeStr = getCellString(row.getCell(4));
                String standingStr = getCellString(row.getCell(5));

                boolean isStanding = standingStr != null &&
                        (standingStr.equalsIgnoreCase("yes") || standingStr.equalsIgnoreCase("true"));

                LocalDate date = (dateStr != null && !dateStr.isBlank())
                        ? LocalDate.parse(dateStr.trim()) : null;

                DayOfWeek dayOfWeek;
                if (dayOfWeekStr != null && !dayOfWeekStr.isBlank()) {
                    dayOfWeek = DayOfWeek.valueOf(dayOfWeekStr.trim().toUpperCase());
                } else if (date != null) {
                    dayOfWeek = date.getDayOfWeek();
                } else {
                    skipped.add("Row " + (i + 1) + ": missing date/day of week");
                    continue;
                }

                LocalTime startTime = null;
                if (startTimeStr != null && !startTimeStr.isBlank()) {
                    startTime = LocalTime.parse(startTimeStr.trim(), DateTimeFormatter.ofPattern("HH:mm"));
                }

                // Upsert preference
                AgentPreference pref;
                if (isStanding) {
                    var existing = agentPreferenceRepository
                            .findByTenantIdAndDeskIdAndAgent_IdAndIsStandingTrueAndDayOfWeek(
                                    tenantId, deskId, agent.getId(), dayOfWeek);
                    if (!existing.isEmpty()) {
                        pref = existing.get(0);
                    } else {
                        pref = new AgentPreference();
                        pref.setTenantId(tenantId);
                        pref.setDeskId(deskId);
                        pref.setAgent(agent);
                        pref.setDayOfWeek(dayOfWeek);
                        pref.setStanding(true);
                    }
                } else {
                    pref = agentPreferenceRepository
                            .findByTenantIdAndDeskIdAndAgent_IdAndIsStandingFalseAndDate(
                                    tenantId, deskId, agent.getId(), date)
                            .orElseGet(() -> {
                                AgentPreference ap = new AgentPreference();
                                ap.setTenantId(tenantId);
                                ap.setDeskId(deskId);
                                ap.setAgent(agent);
                                ap.setDate(date);
                                ap.setDayOfWeek(dayOfWeek);
                                ap.setStanding(false);
                                return ap;
                            });
                }

                pref.setPreferredStartTime(startTime);
                AgentPreference savedPref = agentPreferenceRepository.save(pref);
                saved.add(toResponse(savedPref));
            }
        }

        return new PreferenceUploadResult(saved.size(), skipped.size(), skipped);
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private PreferenceResponse toResponse(AgentPreference p) {
        return new PreferenceResponse(
                p.getId(), p.getDayOfWeek(), p.getDate(), p.isStanding(),
                p.getPreferredStartTime(), p.getPreferredBreakTime());
    }

    public record PreferenceUploadResult(int savedCount, int skippedCount, List<String> skippedDetails) {}
}
