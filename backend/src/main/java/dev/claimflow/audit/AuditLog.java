package dev.claimflow.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Append-only audit trail. Every state change on a claim writes one row in the same transaction. */
@Component
public class AuditLog {

    public static final String SYSTEM = "system";

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditLog(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void record(java.util.UUID claimId, String actor, String eventType, String detail) {
        events.save(new AuditEvent(claimId, clock.instant(), actor, eventType, detail));
    }

    public void system(UUID claimId, String eventType, String detail) {
        record(claimId, SYSTEM, eventType, detail);
    }
}
