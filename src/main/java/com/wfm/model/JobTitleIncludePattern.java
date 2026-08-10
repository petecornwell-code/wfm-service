package com.wfm.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry in a tenant's job-title allowlist. An agent is included by the allowlist when
 * their job title contains this {@code pattern} as a case-insensitive substring.
 *
 * When a tenant has no rows in this table the allowlist is inactive and every title passes —
 * see {@link com.wfm.service.AgentEligibilityService#isIncludedByTitleAllowlist}.
 */
@Entity
@Table(name = "job_title_include_pattern", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "pattern"})
})
public class JobTitleIncludePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "pattern", nullable = false)
    private String pattern;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public JobTitleIncludePattern() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
