package com.wfm.service;

import com.wfm.model.JobTitleIncludePattern;
import com.wfm.repository.JobTitleConfigRepository;
import com.wfm.repository.JobTitleIncludePatternRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AgentEligibilityService {

    private final JobTitleConfigRepository jobTitleConfigRepository;
    private final JobTitleIncludePatternRepository jobTitleIncludePatternRepository;

    public AgentEligibilityService(JobTitleConfigRepository jobTitleConfigRepository,
                                   JobTitleIncludePatternRepository jobTitleIncludePatternRepository) {
        this.jobTitleConfigRepository = jobTitleConfigRepository;
        this.jobTitleIncludePatternRepository = jobTitleIncludePatternRepository;
    }

    /**
     * Returns true iff the given jobTitle is configured as non-schedulable for the tenant.
     * Returns false for null or blank jobTitle.
     * Callers (SolverService, DeskAssignmentUploadService, ClientManagementService) provide
     * tenantId from their own TenantContext resolution — this method does not call TenantContext.
     */
    @Transactional(readOnly = true)
    public boolean isNonSchedulable(long tenantId, String jobTitle) {
        if (jobTitle == null || jobTitle.isBlank()) {
            return false;
        }
        return jobTitleConfigRepository.findByTenantIdAndJobTitle(tenantId, jobTitle)
                .map(config -> config.isNonSchedulable())
                .orElse(false);
    }

    /**
     * Returns true iff the given jobTitle passes the tenant's job-title allowlist.
     *
     * Rules:
     * <ul>
     *   <li>No patterns configured -> allowlist inactive, everything passes (true). This keeps
     *       behaviour unchanged for tenants that never configure one.</li>
     *   <li>Patterns configured -> true only when jobTitle CONTAINS at least one pattern as a
     *       case-insensitive substring. So the pattern "Customer Support Representative" also
     *       matches "Senior Customer Support Representative II".</li>
     *   <li>A null/blank jobTitle cannot match any pattern, so it is excluded whenever the
     *       allowlist is active.</li>
     * </ul>
     *
     * This is an allowlist and is independent of {@link #isNonSchedulable}: an agent must pass
     * this AND not be non-schedulable to be included.
     */
    @Transactional(readOnly = true)
    public boolean isIncludedByTitleAllowlist(long tenantId, String jobTitle) {
        List<JobTitleIncludePattern> patterns = jobTitleIncludePatternRepository.findByTenantId(tenantId);
        if (patterns.isEmpty()) {
            return true; // allowlist inactive
        }
        if (jobTitle == null || jobTitle.isBlank()) {
            return false; // active allowlist, nothing to match against
        }
        String haystack = jobTitle.toLowerCase(Locale.ROOT);
        return patterns.stream()
                .map(JobTitleIncludePattern::getPattern)
                .filter(p -> p != null && !p.isBlank())
                .anyMatch(p -> haystack.contains(p.toLowerCase(Locale.ROOT)));
    }
}
