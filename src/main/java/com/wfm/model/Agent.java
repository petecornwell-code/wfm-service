package com.wfm.model;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agent", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "bamboohr_id"})
})
public class Agent {

    @PlanningId
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "bamboohr_id", nullable = false)
    private String bamboohrId;

    @Column(nullable = false)
    private String name;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String email;

    private String department;

    @Column(name = "job_title")
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "working_days_known", nullable = false)
    private boolean workingDaysKnown = true;

    @Column(name = "last_refreshed_at")
    private OffsetDateTime lastRefreshedAt;

    // --- Desk assignment fields (nullable = unassigned) ---

    @Column(name = "desk_id")
    private UUID deskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_specialization_id")
    private Specialization primarySpecialization;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "agent_secondary_specialization",
        joinColumns = @JoinColumn(name = "agent_id"),
        inverseJoinColumns = @JoinColumn(name = "specialization_id")
    )
    private List<Specialization> secondarySpecializations = new ArrayList<>();

    @Column(name = "contracted_hours_per_day", precision = 5, scale = 2)
    private BigDecimal contractedHoursPerDay;

    public Agent() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public String getBamboohrId() { return bamboohrId; }
    public void setBamboohrId(String bamboohrId) { this.bamboohrId = bamboohrId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isWorkingDaysKnown() { return workingDaysKnown; }
    public void setWorkingDaysKnown(boolean workingDaysKnown) { this.workingDaysKnown = workingDaysKnown; }

    public OffsetDateTime getLastRefreshedAt() { return lastRefreshedAt; }
    public void setLastRefreshedAt(OffsetDateTime lastRefreshedAt) { this.lastRefreshedAt = lastRefreshedAt; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public Specialization getPrimarySpecialization() { return primarySpecialization; }
    public void setPrimarySpecialization(Specialization primarySpecialization) {
        this.primarySpecialization = primarySpecialization;
    }

    public List<Specialization> getSecondarySpecializations() { return secondarySpecializations; }
    public void setSecondarySpecializations(List<Specialization> secondarySpecializations) {
        this.secondarySpecializations = secondarySpecializations;
    }

    public BigDecimal getContractedHoursPerDay() { return contractedHoursPerDay; }
    public void setContractedHoursPerDay(BigDecimal contractedHoursPerDay) {
        this.contractedHoursPerDay = contractedHoursPerDay;
    }

}
