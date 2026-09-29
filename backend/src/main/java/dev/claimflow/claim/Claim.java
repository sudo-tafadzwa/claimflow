package dev.claimflow.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claim")
public class Claim {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false)
    private String claimantName;

    @Column(nullable = false)
    private String claimantEmail;

    @Column(nullable = false)
    private String submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    private String policyNumber;
    private String policyType;
    private LocalDate incidentDate;
    private String incidentType;
    private BigDecimal claimedAmount;
    private BigDecimal payableAmount;
    private BigDecimal fraudScore;
    private String decisionReason;
    private Long processInstanceKey;
    private String payoutPreparedBy;
    private String payoutReleasedBy;
    private String payoutAccount;
    private String paymentReference;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Claim() {
    }

    public Claim(UUID id, String reference, String claimantName, String claimantEmail, String submittedBy, Instant now) {
        this.id = id;
        this.reference = reference;
        this.claimantName = claimantName;
        this.claimantEmail = claimantEmail;
        this.submittedBy = submittedBy;
        this.status = ClaimStatus.SUBMITTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Moves the claim to a new status. A claim in a final status never changes again. */
    public void moveTo(ClaimStatus next, Instant now) {
        if (status.isFinal() && status != next) {
            throw new IllegalStateException("Claim " + reference + " is already " + status);
        }
        this.status = next;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public String getClaimantName() { return claimantName; }
    public String getClaimantEmail() { return claimantEmail; }
    public String getSubmittedBy() { return submittedBy; }
    public ClaimStatus getStatus() { return status; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
    public String getPolicyType() { return policyType; }
    public void setPolicyType(String policyType) { this.policyType = policyType; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public void setIncidentDate(LocalDate incidentDate) { this.incidentDate = incidentDate; }
    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public void setClaimedAmount(BigDecimal claimedAmount) { this.claimedAmount = claimedAmount; }
    public BigDecimal getPayableAmount() { return payableAmount; }
    public void setPayableAmount(BigDecimal payableAmount) { this.payableAmount = payableAmount; }
    public BigDecimal getFraudScore() { return fraudScore; }
    public void setFraudScore(BigDecimal fraudScore) { this.fraudScore = fraudScore; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public Long getProcessInstanceKey() { return processInstanceKey; }
    public void setProcessInstanceKey(Long processInstanceKey) { this.processInstanceKey = processInstanceKey; }
    public String getPayoutPreparedBy() { return payoutPreparedBy; }
    public void setPayoutPreparedBy(String payoutPreparedBy) { this.payoutPreparedBy = payoutPreparedBy; }
    public String getPayoutReleasedBy() { return payoutReleasedBy; }
    public void setPayoutReleasedBy(String payoutReleasedBy) { this.payoutReleasedBy = payoutReleasedBy; }
    public String getPayoutAccount() { return payoutAccount; }
    public void setPayoutAccount(String payoutAccount) { this.payoutAccount = payoutAccount; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
