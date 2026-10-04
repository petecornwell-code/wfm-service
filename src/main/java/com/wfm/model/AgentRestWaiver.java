package com.wfm.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A per-agent-per-business-date waiver of the minimum-rest constraint (REST-06, D-07).
 *
 * <p>"Waived" is the presence of the row — this entity carries no hours column of any kind.
 * {@link AgentException} is an hours-override channel: three sites build its lookup map
 * unconditionally on a non-null hours-override field, that column is {@code NOT NULL} in the
 * schema and re-checked in {@code AgentExceptionService}, and the resolver's own javadoc states
 * the non-nullability as an invariant. A rest-only row with null hours would make that agent-date's
 * effective hours null, which flows into the {@code > 0} gate that decides whether an
 * {@code AgentShiftAssignment} row exists at all — so a separate table relaxes nothing D-07
 * reasoned about.
 *
 * <p>{@code date} is a business date by the same consumption-established semantics
 * {@code agent_exception.date} already has — it is matched against the schedule's business-date
 * period walk, so REST-06's "one business date" needs no new derivation.
 */
@Entity
@Table(name = "agent_rest_waiver", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "desk_id", "agent_id", "date"})
})
public class AgentRestWaiver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String reason;

    public AgentRestWaiver() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public UUID getDeskId() { return deskId; }
    public void setDeskId(UUID deskId) { this.deskId = deskId; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
