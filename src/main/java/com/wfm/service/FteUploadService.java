package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.util.DayWindow;
import com.wfm.dto.FteUploadResult;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.StaffingSource;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FteUploadService {

    private static final Logger log = LoggerFactory.getLogger(FteUploadService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final TimeslotRepository timeslotRepository;
    private final SpecializationRepository specializationRepository;
    private final StaffingRequirementRepository staffingRequirementRepository;
    private final TimeslotGeneratorService timeslotGeneratorService;
    private final DeskRepository deskRepository;
    private final EntityManager entityManager;

    public FteUploadService(TimeslotRepository timeslotRepository,
                            SpecializationRepository specializationRepository,
                            StaffingRequirementRepository staffingRequirementRepository,
                            TimeslotGeneratorService timeslotGeneratorService,
                            DeskRepository deskRepository,
                            EntityManager entityManager) {
        this.timeslotRepository = timeslotRepository;
        this.specializationRepository = specializationRepository;
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.timeslotGeneratorService = timeslotGeneratorService;
        this.deskRepository = deskRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public FteUploadResult uploadFtes(UUID deskId, MultipartFile file) throws IOException {
        long tenantId = TenantContext.getTenantId();

        // BDAY-04 (plan 19-06, Rule 3 deviation -- see this plan's SUMMARY): this class holds
        // neither a DeskRepository nor a Schedule parameter of its own in the anchor-source table's
        // original classification, but neither of its two real callers (StaffingRequirementController,
        // via the controller's own upload endpoint) resolves a desk or a dayStart either -- the
        // "propagate outward" channel the table assumed has no reachable anchor at the other end.
        // Loading the desk directly here, once per upload, is this class's own anchor source.
        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
        DayWindow window = DayWindow.anchoredAt(desk.getDayStart());

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

                LocalDate sheetDate = parseSheetDate(sheetName);
                if (sheetDate == null) {
                    skipped.add("Sheet '" + sheetName + "': no date found (expected yyyy-MM-dd in sheet name)");
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

                List<LocalTime> headerTimes = parseHeaderTimes(header);
                for (int i = 0; i < headerTimes.size(); i++) {
                    LocalTime slotStart = headerTimes.get(i);
                    if (slotStart == null) continue;
                    // OVNT-02/plan 21-12: compared as START boundaries through the anchored
                    // minute, not raw isBefore -- on a desk anchored away from midnight, two
                    // start times compared raw order by CLOCK, not by position in the business
                    // day, so the sheet's stored operating-window start would be computed from
                    // the clock-earliest slot rather than the business day's first. At a 00:00
                    // anchor the anchored minute equals the clock minute, so this is a no-op on
                    // every desk that exists today.
                    if (startTime == null
                            || window.anchoredStartMinute(slotStart) < window.anchoredStartMinute(startTime)) {
                        startTime = slotStart;
                    }
                    // Determine end of this slot
                    LocalTime slotEnd = (i + 1 < headerTimes.size() && headerTimes.get(i + 1) != null)
                            ? headerTimes.get(i + 1)
                            : (incrementMinutes > 0
                                    ? window.anchoredPlusWithinDay(slotStart, incrementMinutes) : null);
                    if (slotEnd != null) {
                        // Compared as END boundaries: a final slot ending at midnight stores 00:00,
                        // which isAfter() reads as the earliest time of day, so the sheet's window
                        // would have been recorded as ending an increment early.
                        if (endTime == null || window.anchoredEndMinute(slotEnd) > window.anchoredEndMinute(endTime)) {
                            endTime = slotEnd;
                        }
                        if (incrementMinutes == 0) {
                            incrementMinutes = window.anchoredDurationMinutes(slotStart, slotEnd);
                        }
                    }
                }
            }

            if (minDate == null || startTime == null || endTime == null || incrementMinutes == 0) {
                throw new IllegalArgumentException("Could not determine date/time range from spreadsheet");
            }

            // Generate timeslots for the full date range (reuses existing if they match).
            // BDAY-01/BDAY-04: DeskService.setDayStart's 00:00-only gate (lifted by SOLV-01) is
            // what keeps desk.getDayStart() at MIDNIGHT for every desk today; this now passes the
            // desk's real anchor rather than a hardcoded literal.
            List<Timeslot> timeslots = timeslotGeneratorService.generateTimeslots(
                    deskId, minDate, maxDate, desk.getDayStart(), startTime, endTime, incrementMinutes);

            // Build lookup: businessDate -> startTime -> Timeslot.
            // CR-01 (phase 21 code review): keyed by getBusinessDate(), not getDate() (calendar
            // date). The second pass below queries this map by sheetDate, which is the business
            // date parsed from the uploaded sheet's name -- the same business day for every column
            // in that sheet. On a desk anchored away from midnight, getDate() (calendar date)
            // diverges from getBusinessDate() for every post-midnight row (TimeslotGeneratorService:
            // "A 21:00-anchored desk's post-midnight rows carry the FOLLOWING calendar date but the
            // ORIGINAL business date"), so keying by calendar date made a single calendar-date
            // bucket hold a MIX of one business day's post-midnight rows and the following business
            // day's pre-midnight rows -- not just a missed lookup, but a lookup that could resolve
            // to the WRONG business day's timeslot. Keying by business date matches every sibling
            // consumer in this phase (ScheduleOutputService, ScheduleExportService,
            // StaffingRequirementService.saveRequirements/calculateErlangC/calculateErlangX) and is
            // byte-identical on a 00:00-anchored desk, where business date already equals calendar
            // date. Out of scope: staffingRequirementRepository.deleteLiveByDeskAndDateRange below
            // deliberately stays on calendar-date semantics per plan 21-04's settled decision -- this
            // fix only changes how the per-sheet lookup keys individual timeslots.
            Map<LocalDate, Map<LocalTime, Timeslot>> timeslotLookup = new HashMap<>();
            for (Timeslot ts : timeslots) {
                timeslotLookup.computeIfAbsent(ts.getBusinessDate(), k -> new HashMap<>())
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

                LocalDate sheetDate = parseSheetDate(sheetName);
                if (sheetDate == null) continue; // already skipped in first pass

                Row header = sheet.getRow(0);
                if (header == null) continue;

                List<LocalTime> colStartTimes = parseHeaderTimes(header);

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
            return new FteUploadResult(totalSaved, skipped.size(), saved, skipped,
                    minDate, maxDate, startTime, endTime, incrementMinutes);
        }
    }

    /** Extracts a yyyy-MM-dd date from a sheet name. Accepts exact match or date embedded in a longer name. */
    private static LocalDate parseSheetDate(String sheetName) {
        Matcher m = DATE_PATTERN.matcher(sheetName);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(m.group());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parses start times from a header row. Supports two formats per cell:
     *   "HH:mm-HH:mm" (range) — extracts the start time
     *   "HH:mm"        (start time only)
     * Returns one entry per data column (col 1 onwards); null for unparseable cells.
     */
    private static List<LocalTime> parseHeaderTimes(Row header) {
        List<LocalTime> times = new ArrayList<>();
        for (int col = 1; col < header.getLastCellNum(); col++) {
            Cell cell = header.getCell(col);
            if (cell == null) { times.add(null); continue; }
            String val = cell.getStringCellValue().trim();
            if (val.isEmpty()) { times.add(null); continue; }
            String startPart = val.contains("-") ? val.split("-")[0].trim() : val;
            try {
                times.add(LocalTime.parse(startPart, TIME_FMT));
            } catch (DateTimeParseException e) {
                times.add(null);
            }
        }
        return times;
    }
}
