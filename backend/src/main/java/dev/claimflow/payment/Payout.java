package dev.claimflow.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout")
public class Payout {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID claimId;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String account;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false)
    private Instant executedAt;

    private Instant reversedAt;

    protected Payout() {
    }

    public Payout(UUID claimId, String idempotencyKey, BigDecimal amount, String account, String reference, Instant now) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.account = account;
        this.status = "EXECUTED";
        this.reference = reference;
        this.executedAt = now;
    }

    public void reverse(Instant now) {
        this.status = "REVERSED";
        this.reversedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getAmount() { return amount; }
    public String getAccount() { return account; }
    public String getStatus() { return status; }
    public String getReference() { return reference; }
    public Instant getExecutedAt() { return executedAt; }
    public Instant getReversedAt() { return reversedAt; }
}
