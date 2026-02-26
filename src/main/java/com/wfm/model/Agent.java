package com.wfm.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "bamboohr_id"})
})
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "bamboohr_id", nullable = false)
    private String bamboohrId;

    @Column(nullable = false)
    private String name;

    private String email;

    private String department;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_refreshed_at")
    private OffsetDateTime lastRefreshedAt;

    public Agent() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public String getBamboohrId() { return bamboohrId; }
    public void setBamboohrId(String bamboohrId) { this.bamboohrId = bamboohrId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getLastRefreshedAt() { return lastRefreshedAt; }
    public void setLastRefreshedAt(OffsetDateTime lastRefreshedAt) { this.lastRefreshedAt = lastRefreshedAt; }
}
