package dev.claimflow.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID claimId;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String eventType;

    private String detail;

    protected AuditEvent() {
    }

    public AuditEvent(UUID claimId, Instant occurredAt, String actor, String eventType, String detail) {
        this.claimId = claimId;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.eventType = eventType;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public String getEventType() { return eventType; }
    public String getDetail() { return detail; }
}
