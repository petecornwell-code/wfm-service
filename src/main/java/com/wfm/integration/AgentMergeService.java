package com.wfm.integration;

import com.wfm.dto.MergeReportEntry;
import com.wfm.exception.BambooHRRateLimitedException;
import com.wfm.exception.BambooHRSyncFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fetches a single fresh BambooHR snapshot (employees + time-off) before any upload
 * transaction opens (D-01/D-03), and merges spreadsheet-supplied identity fields against it
 * using BambooHR-first precedence (D-06/D-07/D-08/MRG-02). Read-only: never writes to the
 * database -- the upload's transactional write pass is the sole writer.
 *
 * Lives in {@code com.wfm.integration} (not {@code com.wfm.service}) so it can call the
 * package-private {@link WorkingDaysParser} directly and share {@link BambooEmployee}'s raw
 * field set with {@link BambooRefreshService}.
 */
@Service
public class AgentMergeService {

    private static final Logger log = LoggerFactory.getLogger(AgentMergeService.class);

    /** D-07: outcome label when BambooHR's non-blank value won over a differing sheet value. */
    public static final String OUTCOME_BAMBOOHR_OVERRIDE = "BambooHR override";

    /** D-06/D-08: outcome label when BambooHR had no data and the sheet filled the gap. */
    public static final String OUTCOME_GAP_FILLED = "Gap-filled by spreadsheet";

    /**
     * D-08 contested identity field labels, in the fixed order the merge report renders them
     * (UI-SPEC Copywriting Contract) -- callers merge in this order so a re-upload of an
     * unchanged workbook against unchanged BambooHR data produces an identical merge-report
     * array (MRG-05/ordering).
     */
    public static final List<String> IDENTITY_FIELD_ORDER = List.of(
            "First name", "Last name", "Email", "Department", "Job title", "Active status");

    private final BambooHRClient bambooHRClient;

    @Value("${bamboohr.time-off.lookahead-weeks:8}")
    private int lookaheadWeeks;

    @Value("${bamboohr.time-off.lookback-weeks:12}")
    private int lookbackWeeks;

    public AgentMergeService(BambooHRClient bambooHRClient) {
        this.bambooHRClient = bambooHRClient;
    }

    /**
     * Fetches every BambooHR employee and time-off entry for the whole tenant in one pass
     * (D-01 -- {@code null} project so one fetch serves every sheet in the workbook,
     * regardless of whether {@code ClientManagementService}'s cache is already warm). Retains
     * ALL employees regardless of {@code status()} so the upload's skip-reason logic can
     * distinguish an unknown id from an inactive one. Performs no database access (D-03).
     */
    public BambooSnapshot fetchSnapshot(long tenantId) {
        try {
            List<BambooEmployee> employees = bambooHRClient.listEmployees(String.valueOf(tenantId), null);

            Map<String, BambooEmployee> employeesById = new LinkedHashMap<>();
            for (BambooEmployee employee : employees) {
                if (employee.id() == null) continue;
                // First occurrence wins on a duplicate id (mirrors BambooRefreshService's
                // dedupedDaysOff idiom) so repeated uploads of identical BambooHR data resolve
                // identically.
                employeesById.putIfAbsent(employee.id().trim(), employee);
            }

            LocalDate windowFrom = LocalDate.now().minusWeeks(lookbackWeeks);
            LocalDate windowTo = LocalDate.now().plusWeeks(lookaheadWeeks);
            // If listEmployees succeeded but this call throws, the catch below still fires
            // before any transaction opens, so zero rows are written (MRG-07).
            List<BambooTimeOff> timeOffs = bambooHRClient.listTimeOff(String.valueOf(tenantId), windowFrom, windowTo);
            Map<String, List<BambooTimeOff>> timeOffByEmployeeId = timeOffs.stream()
                    .collect(Collectors.groupingBy(BambooTimeOff::employeeId));

            return new BambooSnapshot(employeesById, timeOffByEmployeeId, windowFrom, windowTo);
        } catch (BambooHRRateLimitedException e) {
            // Never swallowed, never log-and-continue: this rethrows unconditionally, so the
            // throw still precedes transactionTemplate.executeWithoutResult and zero writes is
            // structural rather than something this method has to arrange (MRG-07/D-02).
            throw new BambooHRRateLimitedException(
                    uploadSyncFailureMessage(e.getMessage()), e.getRetryAfterSeconds());
        } catch (RuntimeException e) {
            throw new BambooHRSyncFailedException(uploadSyncFailureMessage(e.getMessage()), e);
        }
    }

    /**
     * The upload-specific MRG-07 operator sentence, applied only at this upload fetch site --
     * {@code HttpBambooHRClient.applyRateLimitHandler}'s own message stays untouched so the
     * manual desk-refresh path keeps its existing wording. When the upstream reason is null or
     * blank, substitutes a fixed literal so the sentence stays well-formed.
     */
    private static String uploadSyncFailureMessage(String upstreamReason) {
        String reason = hasData(upstreamReason) ? upstreamReason : "no detail available";
        return "BambooHR sync failed (" + reason + ") — no changes were made. "
                + "Retry the upload once BambooHR is available.";
    }

    /** D-06: "BambooHR has data" -- not null, not empty, not whitespace-only. */
    public static boolean hasData(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Merges one contested identity field: BambooHR wins whenever it has data (D-06); the
     * sheet fills the gap only when BambooHR is blank (D-07/D-08). Appends a
     * {@link MergeReportEntry} to {@code report} only for a genuine divergence (both sides
     * have data and differ after trimming) or a genuine gap-fill (BambooHR blank, sheet
     * supplied) -- silent agreement and both-blank emit nothing (D-11).
     */
    public String mergeIdentityFields(String bambooValue, String sheetValue, String field, String bamboohrId,
                                       String agentName, List<MergeReportEntry> report) {
        boolean bambooHasData = hasData(bambooValue);
        boolean sheetHasData = hasData(sheetValue);
        String winner = bambooHasData ? bambooValue : sheetValue;

        if (bambooHasData && sheetHasData && !bambooValue.trim().equals(sheetValue.trim())) {
            log.info("Merge decision: bamboohrId={}, field={}, outcome=override", bamboohrId, field);
            log.debug("Merge override detail: bamboohrId={}, field={}, bambooValue={}, sheetValue={}",
                    bamboohrId, field, bambooValue, sheetValue);
            report.add(new MergeReportEntry(bamboohrId, agentName, field, bambooValue, sheetValue,
                    OUTCOME_BAMBOOHR_OVERRIDE));
        } else if (!bambooHasData && sheetHasData) {
            log.info("Merge decision: bamboohrId={}, field={}, outcome=gap-filled", bamboohrId, field);
            log.debug("Merge gap-fill detail: bamboohrId={}, field={}, sheetValue={}",
                    bamboohrId, field, sheetValue);
            report.add(new MergeReportEntry(bamboohrId, agentName, field, bambooValue, sheetValue,
                    OUTCOME_GAP_FILLED));
        }
        return winner;
    }

    /** D-01/D-03: a single read-only fetch of the whole tenant's BambooHR employees + time-off. */
    public record BambooSnapshot(
            Map<String, BambooEmployee> employeesById,
            Map<String, List<BambooTimeOff>> timeOffByEmployeeId,
            LocalDate windowFrom,
            LocalDate windowTo
    ) {}
}
