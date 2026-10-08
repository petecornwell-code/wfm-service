package com.wfm.model;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * The inputs behind one persisted Erlang C / Erlang X calculation, for one timeslot and
 * specialization (V56). {@code timeslotId}, {@code specializationId} and {@code deskId} are plain
 * UUID columns rather than associations: the FK lifecycle (ON DELETE CASCADE) is owned by the
 * migration, and a Hibernate-generated FK under the H2 test profile would have no cascade and
 * would block the existing timeslot delete paths.
 *
 * <p>Units: {@code serviceLevelTarget} and {@code retryRate} are percentages; {@code shrinkage}
 * and {@code maxOccupancy} are fractions; null means off or not applicable to the model.
 */
@Entity
@Table(name = "erlang_demand_input")
public class ErlangDemandInput {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @Column(name = "timeslot_id", nullable = false)
    private UUID timeslotId;

    @Column(name = "specialization_id", nullable = false)
    private UUID specializationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "model", nullable = false, length = 20)
    private StaffingSource model;

    @Column(name = "call_volume", nullable = false)
    private int callVolume;

    @Column(name = "aht", nullable = false)
    private double aht;

    @Column(name = "service_level_target", nullable = false)
    private double serviceLevelTarget;

    @Column(name = "service_level_threshold", nullable = false)
    private int serviceLevelThreshold;

    @Column(name = "patience")
    private Double patience;

    @Column(name = "retry_rate")
    private Double retryRate;

    @Column(name = "shrinkage")
    private Double shrinkage;

    @Column(name = "max_occupancy")
    private Double maxOccupancy;

    @Column(name = "concurrency")
    private Double concurrency;

    public ErlangDemandInput() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public UUID getTimeslotId() { return timeslotId; }
    public void setTimeslotId(UUID timeslotId) { this.timeslotId = timeslotId; }

    public UUID getSpecializationId() { return specializationId; }
    public void setSpecializationId(UUID specializationId) { this.specializationId = specializationId; }

    public StaffingSource getModel() { return model; }
    public void setModel(StaffingSource model) { this.model = model; }

    public int getCallVolume() { return callVolume; }
    public void setCallVolume(int callVolume) { this.callVolume = callVolume; }

    public double getAht() { return aht; }
    public void setAht(double aht) { this.aht = aht; }

    public double getServiceLevelTarget() { return serviceLevelTarget; }
    public void setServiceLevelTarget(double serviceLevelTarget) { this.serviceLevelTarget = serviceLevelTarget; }

    public int getServiceLevelThreshold() { return serviceLevelThreshold; }
    public void setServiceLevelThreshold(int serviceLevelThreshold) { this.serviceLevelThreshold = serviceLevelThreshold; }

    public Double getPatience() { return patience; }
    public void setPatience(Double patience) { this.patience = patience; }

    public Double getRetryRate() { return retryRate; }
    public void setRetryRate(Double retryRate) { this.retryRate = retryRate; }

    public Double getShrinkage() { return shrinkage; }
    public void setShrinkage(Double shrinkage) { this.shrinkage = shrinkage; }

    public Double getMaxOccupancy() { return maxOccupancy; }
    public void setMaxOccupancy(Double maxOccupancy) { this.maxOccupancy = maxOccupancy; }

    public Double getConcurrency() { return concurrency; }
    public void setConcurrency(Double concurrency) { this.concurrency = concurrency; }
}
