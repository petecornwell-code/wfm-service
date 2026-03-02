package com.wfm.model;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import com.wfm.solver.AgentAssignmentDifficultyComparator;
import jakarta.persistence.*;
import java.util.UUID;

@PlanningEntity(difficultyComparatorClass = AgentAssignmentDifficultyComparator.class)
@Entity
@Table(name = "agent_assignment")
public class AgentAssignment {

    @PlanningId
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timeslot_id", nullable = false)
    private Timeslot timeslot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialization_id", nullable = false)
    private Specialization requiredSpecialization;

    @PlanningVariable(valueRangeProviderRefs = "deskAgentRange", nullable = true)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desk_agent_id")
    private DeskAgent deskAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    public AgentAssignment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }

    public Timeslot getTimeslot() { return timeslot; }
    public void setTimeslot(Timeslot timeslot) { this.timeslot = timeslot; }

    public Specialization getRequiredSpecialization() { return requiredSpecialization; }
    public void setRequiredSpecialization(Specialization requiredSpecialization) {
        this.requiredSpecialization = requiredSpecialization;
    }

    public DeskAgent getDeskAgent() { return deskAgent; }
    public void setDeskAgent(DeskAgent deskAgent) { this.deskAgent = deskAgent; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
}
