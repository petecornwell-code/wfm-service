package com.wfm.service;

import com.wfm.repository.JobTitleConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentEligibilityService {

    private final JobTitleConfigRepository jobTitleConfigRepository;

    public AgentEligibilityService(JobTitleConfigRepository jobTitleConfigRepository) {
        this.jobTitleConfigRepository = jobTitleConfigRepository;
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
}
