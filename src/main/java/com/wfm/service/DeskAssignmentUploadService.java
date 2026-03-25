package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
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
    private final ClientManagementService clientManagementService;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentExceptionRepository agentExceptionRepository;
    private final SpecializationRepository specializationRepository;

    public DeskAssignmentUploadService(AgentRepository agentRepository,
                                        DeskRepository deskRepository,
                                        ClientManagementService clientManagementService,
                                        AgentPreferenceRepository agentPreferenceRepository,
                                        AgentExceptionRepository agentExceptionRepository,
                                        SpecializationRepository specializationRepository) {
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.clientManagementService = clientManagementService;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentExceptionRepository = agentExceptionRepository;
        this.specializationRepository = specializationRepository;
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

        // Pre-load specializations per desk: deskId -> (lowercased name -> Specialization)
        Map<UUID, Map<String, Specialization>> specsByDesk = new HashMap<>();
        for (Desk d : allDesks) {
            List<Specialization> specs = specializationRepository.findByTenantIdAndDeskId(tenantId, d.getId());
            Map<String, Specialization> specMap = new HashMap<>();
            for (Specialization s : specs) {
                specMap.put(s.getName().toLowerCase(), s);
            }
            specsByDesk.put(d.getId(), specMap);
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Spreadsheet has no sheets");
            }

            // Clear all desks referenced in the spreadsheet before re-assigning
            Set<UUID> clearedDeskIds = new HashSet<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String dn = getCellString(row.getCell(3));
                if (dn == null || dn.isBlank()) continue;
                Desk d = deskByName.get(dn.trim().toLowerCase());
                if (d != null && clearedDeskIds.add(d.getId())) {
                    clearDesk(tenantId, d.getId());
                }
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String bamboohrId = getCellString(row.getCell(0));
                String name = getCellString(row.getCell(1));
                String email = getCellString(row.getCell(2));
                String deskName = getCellString(row.getCell(3));

                // Parse specialty columns (4..N) — dynamic number of columns
                List<String> specialtyNames = new ArrayList<>();
                for (int col = 4; ; col++) {
                    String val = getCellString(row.getCell(col));
                    if (val == null || val.isBlank()) break;
                    specialtyNames.add(val.trim());
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

                // Resolve specialty names against desk specializations
                Map<String, Specialization> deskSpecs = specsByDesk.getOrDefault(desk.getId(), Map.of());
                List<Specialization> resolvedSpecialties = new ArrayList<>();
                boolean specialtyError = false;
                for (String specName : specialtyNames) {
                    Specialization spec = deskSpecs.get(specName.toLowerCase());
                    if (spec == null) {
                        skipped.add("Row " + (i + 1) + ": specialty '" + specName + "' not found on desk '" + desk.getName() + "'");
                        specialtyError = true;
                        break;
                    }
                    resolvedSpecialties.add(spec);
                }
                if (specialtyError) continue;

                boolean hasBambooId = bamboohrId != null && !bamboohrId.isBlank();
                boolean hasEmail = email != null && !email.isBlank();
                boolean hasName = name != null && !name.isBlank();

                // Verify the agent exists in the BambooHR cache before proceeding
                BambooEmployeeResponse cached = clientManagementService.findCachedEmployee(
                        hasBambooId ? bamboohrId.trim() : null,
                        hasEmail ? email.trim() : null,
                        hasName ? name.trim() : null);

                if (cached == null) {
                    // Agent not found in BambooHR cache — log and skip
                    String identifier = hasName ? name.trim() : (hasEmail ? email.trim() : bamboohrId);
                    log.warn("Row {}: agent '{}' from spreadsheet not found in BambooHR cache — skipping desk assignment", i + 1, identifier);
                    skipped.add("Row " + (i + 1) + ": agent '" + identifier + "' not found in BambooHR cache");
                    continue;
                }

                // Find existing agent in DB, or create a new one from the cached BambooHR data
                Agent agent = null;

                if (hasBambooId) {
                    agent = agentRepository.findByTenantIdAndBamboohrId(tenantId, bamboohrId.trim())
                            .orElse(null);
                }
                if (agent == null && hasEmail) {
                    agent = agentRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email.trim())
                            .orElse(null);
                }
                if (agent == null && hasName) {
                    agent = agentRepository.findByTenantIdAndNameIgnoreCase(tenantId, name.trim())
                            .orElse(null);
                }

                // Also try matching by the cached BambooHR ID in case spreadsheet identifiers differ
                if (agent == null) {
                    agent = agentRepository.findByTenantIdAndBamboohrId(tenantId, cached.id())
                            .orElse(null);
                }

                if (agent == null) {
                    // Create new agent from BambooHR cache data
                    agent = new Agent();
                    agent.setTenantId(tenantId);
                    agent.setBamboohrId(cached.id());
                    agent.setName(cached.displayName());
                    agent.setEmail(cached.workEmail());
                    agent.setDepartment(cached.department());
                    agent.setJobTitle(cached.jobTitle());
                    agent.setActive("Active".equalsIgnoreCase(cached.status()));
                    agent.setLastRefreshedAt(OffsetDateTime.now());
                }

                // Re-activate: the agent is in the BambooHR cache (Active status),
                // so mark active even if a prior refresh soft-deleted them
                agent.setActive("Active".equalsIgnoreCase(cached.status()));

                // Backfill missing fields from BambooHR cache
                if (agent.getEmail() == null || agent.getEmail().isBlank()) {
                    agent.setEmail(cached.workEmail());
                }
                if (agent.getDepartment() == null || agent.getDepartment().isBlank()) {
                    agent.setDepartment(cached.department());
                }
                if (agent.getJobTitle() == null || agent.getJobTitle().isBlank()) {
                    agent.setJobTitle(cached.jobTitle());
                }

                // Update fields from spreadsheet if provided
                if (hasName) {
                    agent.setName(name.trim());
                }
                if (hasEmail) {
                    agent.setEmail(email.trim());
                }
                if (hasBambooId && (agent.getBamboohrId() == null || agent.getBamboohrId().startsWith("UPLOAD-"))) {
                    agent.setBamboohrId(bamboohrId.trim());
                }
                agent.setLastRefreshedAt(OffsetDateTime.now());

                // Check if already assigned to a different desk
                if (agent.getDeskId() != null && !agent.getDeskId().equals(desk.getId())) {
                    skipped.add("Row " + (i + 1) + ": agent '" + agent.getName()
                            + "' already assigned to another desk");
                    continue;
                }

                agent.setDeskId(desk.getId());

                // Assign specializations
                if (resolvedSpecialties.isEmpty()) {
                    agent.setPrimarySpecialization(null);
                    agent.setSecondarySpecializations(new ArrayList<>());
                } else {
                    agent.setPrimarySpecialization(resolvedSpecialties.get(0));
                    agent.setSecondarySpecializations(
                            resolvedSpecialties.size() > 1
                                    ? new ArrayList<>(resolvedSpecialties.subList(1, resolvedSpecialties.size()))
                                    : new ArrayList<>());
                }

                agentRepository.save(agent);
                assigned.add("Row " + (i + 1) + ": " + agent.getName() + " -> " + desk.getName());
            }
        }

        return new DeskAssignmentUploadResult(assigned.size(), skipped.size(), assigned, skipped);
    }

    private void clearDesk(long tenantId, UUID deskId) {
        log.info("Clearing desk {} for tenant {} before spreadsheet re-import", deskId, tenantId);
        // Remove desk-scoped data
        agentPreferenceRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        agentExceptionRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        // Unassign all agents from the desk
        List<Agent> deskAgents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);
        for (Agent agent : deskAgents) {
            agent.setDeskId(null);
            agent.setPrimarySpecialization(null);
            agent.getSecondarySpecializations().clear();
            agent.setContractedHoursPerDay(null);
            agentRepository.save(agent);
        }
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
