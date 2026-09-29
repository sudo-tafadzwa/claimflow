package dev.claimflow.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "extracted_field")
public class ExtractedField {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID claimId;

    @Column(nullable = false)
    private String name;

    private String value;

    @Column(nullable = false)
    private BigDecimal confidence;

    private String verifiedBy;

    protected ExtractedField() {
    }

    public ExtractedField(UUID claimId, String name, String value, BigDecimal confidence) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.name = name;
        this.value = value;
        this.confidence = confidence;
    }

    public void replace(String value, BigDecimal confidence) {
        this.value = value;
        this.confidence = confidence;
        this.verifiedBy = null;
    }

    /** A person confirmed or corrected the value, so it is now certain. */
    public void verify(String value, String verifiedBy) {
        this.value = value;
        this.confidence = BigDecimal.ONE;
        this.verifiedBy = verifiedBy;
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public String getName() { return name; }
    public String getValue() { return value; }
    public BigDecimal getConfidence() { return confidence; }
    public String getVerifiedBy() { return verifiedBy; }
}
