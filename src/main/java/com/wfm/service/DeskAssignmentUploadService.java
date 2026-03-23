package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.integration.BambooEmployee;
import com.wfm.integration.BambooHRClient;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DeskAssignmentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DeskAssignmentUploadService.class);

    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;
    private final BambooHRClient bambooHRClient;

    public DeskAssignmentUploadService(AgentRepository agentRepository,
                                        DeskRepository deskRepository,
                                        BambooHRClient bambooHRClient) {
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.bambooHRClient = bambooHRClient;
    }

    @Transactional
    public DeskAssignmentUploadResult uploadDeskAssignments(MultipartFile file) throws IOException {
        long tenantId = TenantContext.getTenantId();

        List<String> assigned = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        // Pre-load all desks for this tenant keyed by name (case-insensitive)
        List<Desk> allDesks = deskRepository.findByTenantId(tenantId);
        Map<String, Desk> deskByName = new HashMap<>();
        for (Desk d : allDesks) {
            deskByName.put(d.getName().toLowerCase(), d);
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Spreadsheet has no sheets");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String bamboohrId = getCellString(row.getCell(0));
                String name = getCellString(row.getCell(1));
                String email = getCellString(row.getCell(2));
                String deskName = getCellString(row.getCell(3));

                if (bamboohrId == null || bamboohrId.isBlank()) {
                    skipped.add("Row " + (i + 1) + ": missing BambooHR ID");
                    continue;
                }
                if (deskName == null || deskName.isBlank()) {
                    skipped.add("Row " + (i + 1) + ": missing Desk Assignment");
                    continue;
                }

                // Find the desk by name
                Desk desk = deskByName.get(deskName.trim().toLowerCase());
                if (desk == null) {
                    skipped.add("Row " + (i + 1) + ": desk '" + deskName.trim() + "' not found");
                    continue;
                }

                // Find or create the agent by bambooHR ID
                Agent agent = agentRepository.findByTenantIdAndBamboohrId(tenantId, bamboohrId.trim())
                        .orElse(null);

                if (agent == null) {
                    // Try to fetch from BambooHR
                    try {
                        BambooEmployee emp = bambooHRClient.getEmployee(bamboohrId.trim());
                        if (emp != null) {
                            agent = new Agent();
                            agent.setTenantId(tenantId);
                            agent.setBamboohrId(bamboohrId.trim());
                            agent.setName(emp.displayName());
                            agent.setEmail(emp.workEmail());
                            agent.setDepartment(emp.department());
                            agent.setJobTitle(emp.jobTitle());
                            agent.setActive("Active".equalsIgnoreCase(emp.status()));
                            agent.setLastRefreshedAt(OffsetDateTime.now());
                        }
                    } catch (Exception e) {
                        log.warn("Could not fetch employee {} from BambooHR: {}", bamboohrId, e.getMessage());
                    }

                    if (agent == null) {
                        // Create agent from spreadsheet data
                        agent = new Agent();
                        agent.setTenantId(tenantId);
                        agent.setBamboohrId(bamboohrId.trim());
                        agent.setName(name != null && !name.isBlank() ? name.trim() : "Unknown");
                        agent.setEmail(email != null ? email.trim() : null);
                        agent.setActive(true);
                        agent.setLastRefreshedAt(OffsetDateTime.now());
                    }
                } else {
                    // Update name/email from spreadsheet if provided
                    if (name != null && !name.isBlank()) {
                        agent.setName(name.trim());
                    }
                    if (email != null && !email.isBlank()) {
                        agent.setEmail(email.trim());
                    }
                    agent.setLastRefreshedAt(OffsetDateTime.now());
                }

                // Check if already assigned to a different desk
                if (agent.getDeskId() != null && !agent.getDeskId().equals(desk.getId())) {
                    skipped.add("Row " + (i + 1) + ": agent '" + agent.getName()
                            + "' already assigned to another desk");
                    continue;
                }

                agent.setDeskId(desk.getId());
                agentRepository.save(agent);
                assigned.add("Row " + (i + 1) + ": " + agent.getName() + " -> " + desk.getName());
            }
        }

        return new DeskAssignmentUploadResult(assigned.size(), skipped.size(), assigned, skipped);
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

    public record DeskAssignmentUploadResult(
            int assignedCount,
            int skippedCount,
            List<String> assignedDetails,
            List<String> skippedDetails
    ) {}
}
