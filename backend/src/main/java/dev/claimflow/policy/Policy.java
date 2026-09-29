package dev.claimflow.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "policy")
public class Policy {

    @Id
    private String policyNumber;

    @Column(nullable = false)
    private String holderName;

    @Column(nullable = false)
    private String policyType;

    @Column(nullable = false)
    private BigDecimal coverLimit;

    @Column(nullable = false)
    private BigDecimal deductible;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    protected Policy() {
    }

    public boolean isActiveOn(LocalDate date) {
        return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public String getPolicyNumber() { return policyNumber; }
    public String getHolderName() { return holderName; }
    public String getPolicyType() { return policyType; }
    public BigDecimal getCoverLimit() { return coverLimit; }
    public BigDecimal getDeductible() { return deductible; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
