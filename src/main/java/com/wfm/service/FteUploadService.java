package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.FteUploadResult;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.StaffingSource;
import com.wfm.model.Timeslot;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class FteUploadService {

    private static final Logger log = LoggerFactory.getLogger(FteUploadService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final TimeslotRepository timeslotRepository;
    private final SpecializationRepository specializationRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final TimeslotGeneratorService timeslotGeneratorService;
    private final EntityManager entityManager;

    public FteUploadService(TimeslotRepository timeslotRepository,
                            SpecializationRepository specializationRepository,
                            StaffingRequirementRepository staffingRequirementRepository,
                            TimeslotGeneratorService timeslotGeneratorService,
                            EntityManager entityManager) {
        this.timeslotRepository = timeslotRepository;
        this.specializationRepository = specializationRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.timeslotGeneratorService = timeslotGeneratorService;
        this.entityManager = entityManager;
    }

    @Transactional
    public FteUploadResult uploadFtes(UUID deskId, MultipartFile file) throws IOException {
        long tenantId = TenantContext.getTenantId();

        List<String> saved = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        // Load desk specializations keyed by lowercase name
        List<Specialization> deskSpecs = specializationRepository.findByTenantIdAndDeskId(tenantId, deskId);
        Map<String, Specialization> specByName = new HashMap<>();
        for (Specialization s : deskSpecs) {
            specByName.put(s.getName().toLowerCase(), s);
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Spreadsheet has no sheets");
            }

            // First pass: determine the date range and time range from all sheets
            LocalDate minDate = null;
            LocalDate maxDate = null;
            LocalTime startTime = null;
            LocalTime endTime = null;
            int incrementMinutes = 0;

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName().trim();

                LocalDate sheetDate;
                try {
                    sheetDate = LocalDate.parse(sheetName);
                } catch (DateTimeParseException e) {
                    skipped.add("Sheet '" + sheetName + "': invalid date format, expected yyyy-MM-dd");
                    continue;
                }

                if (minDate == null || sheetDate.isBefore(minDate)) minDate = sheetDate;
                if (maxDate == null || sheetDate.isAfter(maxDate)) maxDate = sheetDate;

                // Parse time slots from header row
                Row header = sheet.getRow(0);
                if (header == null || header.getLastCellNum() < 2) {
                    skipped.add("Sheet '" + sheetName + "': missing or empty header row");
                    continue;
                }

                for (int col = 1; col < header.getLastCellNum(); col++) {
                    Cell cell = header.getCell(col);
                    if (cell == null) continue;
                    String val = cell.getStringCellValue().trim();
                    if (val.isEmpty()) continue;

                    String[] parts = val.split("-");
                    if (parts.length != 2) continue;

                    LocalTime slotStart = LocalTime.parse(parts[0].trim(), TIME_FMT);
                    LocalTime slotEnd = LocalTime.parse(parts[1].trim(), TIME_FMT);

                    if (startTime == null || slotStart.isBefore(startTime)) startTime = slotStart;
                    if (endTime == null || slotEnd.isAfter(endTime)) endTime = slotEnd;
                    if (incrementMinutes == 0) {
                        incrementMinutes = (int) slotStart.until(slotEnd, ChronoUnit.MINUTES);
                    }
                }
            }

            if (minDate == null || startTime == null || endTime == null || incrementMinutes == 0) {
                throw new IllegalArgumentException("Could not determine date/time range from spreadsheet");
            }

            // Generate timeslots for the full date range (reuses existing if they match)
            List<Timeslot> timeslots = timeslotGeneratorService.generateTimeslots(
                    deskId, minDate, maxDate, startTime, endTime, incrementMinutes);

            // Build lookup: date -> startTime -> Timeslot
            Map<LocalDate, Map<LocalTime, Timeslot>> timeslotLookup = new HashMap<>();
            for (Timeslot ts : timeslots) {
                timeslotLookup.computeIfAbsent(ts.getDate(), k -> new HashMap<>())
                        .put(ts.getStartTime(), ts);
            }

            // Delete existing staffing requirements in the date range
            staffingRequirementRepository.deleteLiveByDeskAndDateRange(tenantId, deskId, minDate, maxDate);
            entityManager.flush();
            entityManager.clear();

            // Second pass: read FTE values and create staffing requirements
            int totalSaved = 0;
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName().trim();

                LocalDate sheetDate;
                try {
                    sheetDate = LocalDate.parse(sheetName);
                } catch (DateTimeParseException e) {
                    continue; // already skipped in first pass
                }

                Row header = sheet.getRow(0);
                if (header == null) continue;

                // Parse column time slots
                List<LocalTime> colStartTimes = new ArrayList<>();
                for (int col = 1; col < header.getLastCellNum(); col++) {
                    Cell cell = header.getCell(col);
                    if (cell == null) {
                        colStartTimes.add(null);
                        continue;
                    }
                    String val = cell.getStringCellValue().trim();
                    String[] parts = val.split("-");
                    if (parts.length != 2) {
                        colStartTimes.add(null);
                        continue;
                    }
                    try {
                        colStartTimes.add(LocalTime.parse(parts[0].trim(), TIME_FMT));
                    } catch (DateTimeParseException e) {
                        colStartTimes.add(null);
                    }
                }

                Map<LocalTime, Timeslot> daySlots = timeslotLookup.getOrDefault(sheetDate, Map.of());

                // Process each specialization row
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    Cell specCell = row.getCell(0);
                    if (specCell == null) continue;
                    String specName = specCell.getStringCellValue().trim();
                    if (specName.isEmpty()) continue;

                    Specialization spec = specByName.get(specName.toLowerCase());
                    if (spec == null) {
                        skipped.add("Sheet '" + sheetName + "' row " + (r + 1)
                                + ": specialization '" + specName + "' not found on desk");
                        continue;
                    }

                    for (int col = 0; col < colStartTimes.size(); col++) {
                        LocalTime slotStart = colStartTimes.get(col);
                        if (slotStart == null) continue;

                        Timeslot ts = daySlots.get(slotStart);
                        if (ts == null) {
                            skipped.add("Sheet '" + sheetName + "' row " + (r + 1)
                                    + ": no timeslot for " + slotStart.format(TIME_FMT));
                            continue;
                        }

                        Cell fteCell = row.getCell(col + 1);
                        if (fteCell == null) continue;

                        int fteValue;
                        if (fteCell.getCellType() == CellType.NUMERIC) {
                            fteValue = (int) fteCell.getNumericCellValue();
                        } else if (fteCell.getCellType() == CellType.STRING) {
                            try {
                                fteValue = Integer.parseInt(fteCell.getStringCellValue().trim());
                            } catch (NumberFormatException e) {
                                skipped.add("Sheet '" + sheetName + "' row " + (r + 1)
                                        + " col " + (col + 2) + ": non-numeric FTE value");
                                continue;
                            }
                        } else {
                            continue;
                        }

                        if (fteValue <= 0) continue;

                        StaffingRequirement sr = new StaffingRequirement();
                        sr.setTenantId(tenantId);
                        sr.setDeskId(deskId);
                        sr.setTimeslot(entityManager.getReference(Timeslot.class, ts.getId()));
                        sr.setSpecialization(spec);
                        sr.setRequiredFTEs(fteValue);
                        sr.setSource(StaffingSource.DIRECT);
                        staffingRequirementRepository.save(sr);
                        totalSaved++;
                    }

                    saved.add("Sheet '" + sheetName + "': " + specName + " loaded");
                }
            }

            log.info("FTE upload for desk {}: {} requirements saved, {} issues", deskId, totalSaved, skipped.size());
            return new FteUploadResult(totalSaved, skipped.size(), saved, skipped);
        }
    }
}
