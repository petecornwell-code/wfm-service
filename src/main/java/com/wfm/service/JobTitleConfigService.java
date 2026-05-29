package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.JobTitleConfigResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.JobTitleConfig;
import com.wfm.repository.JobTitleConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class JobTitleConfigService {

    private final JobTitleConfigRepository repository;

    public JobTitleConfigService(JobTitleConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns all job title configs for the current tenant, sorted by jobTitle ascending.
     */
    @Transactional(readOnly = true)
    public List<JobTitleConfigResponse> list() {
        long tenantId = TenantContext.getTenantId();
        return repository.findByTenantId(tenantId).stream()
                .sorted(Comparator.comparing(JobTitleConfig::getJobTitle))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Updates the nonSchedulable flag for the given job title config.
     * Throws EntityNotFoundException when no row matches (id, tenantId).
     */
    @Transactional
    public JobTitleConfigResponse setNonSchedulable(UUID id, boolean value) {
        long tenantId = TenantContext.getTenantId();
        JobTitleConfig cfg = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("JobTitleConfig", id));
        cfg.setNonSchedulable(value);
        cfg.setUpdatedAt(OffsetDateTime.now());
        repository.save(cfg);
        return toResponse(cfg);
    }

    /**
     * Upsert helper: creates a new row with nonSchedulable=false when the job title does not
     * already exist for this tenant. Idempotent — safe to call per agent during refresh.
     *
     * Takes tenantId as a parameter (rather than reading TenantContext) because callers from
     * BambooRefreshService run inside a scheduled/transaction context where TenantContext may
     * not be set per-tenant.
     *
     * Trims the jobTitle to avoid trailing-whitespace duplicates (T-05-02-04).
     */
    @Transactional
    public void ensureExists(long tenantId, String jobTitle) {
        if (jobTitle == null || jobTitle.isBlank()) {
            return;
        }
        String trimmed = jobTitle.trim();
        repository.findByTenantIdAndJobTitle(tenantId, trimmed)
                .orElseGet(() -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    JobTitleConfig cfg = new JobTitleConfig();
                    cfg.setTenantId(tenantId);
                    cfg.setJobTitle(trimmed);
                    cfg.setNonSchedulable(false);
                    cfg.setCreatedAt(now);
                    cfg.setUpdatedAt(now);
                    return repository.save(cfg);
                });
    }

    private JobTitleConfigResponse toResponse(JobTitleConfig cfg) {
        return new JobTitleConfigResponse(
                cfg.getId(),
                cfg.getJobTitle(),
                cfg.isNonSchedulable(),
                cfg.getCreatedAt(),
                cfg.getUpdatedAt()
        );
    }
}
