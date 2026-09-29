package dev.claimflow.claim;

public enum ClaimStatus {
    SUBMITTED,
    START_FAILED,
    EXTRACTING,
    NEEDS_VERIFICATION,
    ASSESSING,
    UNDER_REVIEW,
    APPROVED,
    PAYOUT_PENDING_RELEASE,
    PAYING,
    PAID,
    REJECTED,
    PAYOUT_REVERSED;

    public boolean isFinal() {
        return this == PAID || this == REJECTED || this == PAYOUT_REVERSED;
    }
}
