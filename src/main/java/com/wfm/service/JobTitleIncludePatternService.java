package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.JobTitleIncludePatternResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.JobTitleIncludePattern;
import com.wfm.repository.JobTitleIncludePatternRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for the tenant's job-title allowlist (see {@link JobTitleIncludePattern}).
 *
 * Read-side matching lives in {@link AgentEligibilityService#isIncludedByTitleAllowlist} so that
 * the template generator, the upload parser, and any future caller share one implementation and
 * cannot drift on the empty-allowlist or case-sensitivity rules.
 */
@Service
public class JobTitleIncludePatternService {

    private final JobTitleIncludePatternRepository repository;

    public JobTitleIncludePatternService(JobTitleIncludePatternRepository repository) {
        this.repository = repository;
    }

    /** All patterns for the current tenant, sorted by pattern ascending. */
    @Transactional(readOnly = true)
    public List<JobTitleIncludePatternResponse> list() {
        long tenantId = TenantContext.getTenantId();
        return repository.findByTenantId(tenantId).stream()
                .sorted(Comparator.comparing(JobTitleIncludePattern::getPattern, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Adds a pattern for the current tenant. Trimmed before storing so trailing whitespace cannot
     * create a near-duplicate. Idempotent: re-adding an existing pattern returns the existing row
     * rather than violating the (tenant_id, pattern) unique constraint.
     *
     * @throws IllegalArgumentException when the pattern is null or blank — a blank pattern would
     *         match every title and silently disable the allowlist.
     */
    @Transactional
    public JobTitleIncludePatternResponse add(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Pattern must not be blank");
        }
        long tenantId = TenantContext.getTenantId();
        String trimmed = pattern.trim();

        return repository.findByTenantIdAndPattern(tenantId, trimmed)
                .map(this::toResponse)
                .orElseGet(() -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    JobTitleIncludePattern entity = new JobTitleIncludePattern();
                    entity.setTenantId(tenantId);
                    entity.setPattern(trimmed);
                    entity.setCreatedAt(now);
                    entity.setUpdatedAt(now);
                    return toResponse(repository.save(entity));
                });
    }

    /**
     * Removes a pattern. Removing the last pattern deactivates the allowlist entirely, which
     * re-includes every job title — intentional, and mirrored in the UI warning.
     */
    @Transactional
    public void delete(UUID id) {
        long tenantId = TenantContext.getTenantId();
        JobTitleIncludePattern entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("JobTitleIncludePattern", id));
        repository.delete(entity);
    }

    private JobTitleIncludePatternResponse toResponse(JobTitleIncludePattern entity) {
        return new JobTitleIncludePatternResponse(
                entity.getId(),
                entity.getPattern(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
