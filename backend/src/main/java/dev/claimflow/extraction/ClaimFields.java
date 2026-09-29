package dev.claimflow.extraction;

import java.util.List;

/** The fields ClaimFlow extracts from a claim document. */
public final class ClaimFields {

    public static final String POLICY_NUMBER = "policyNumber";
    public static final String INCIDENT_DATE = "incidentDate";
    public static final String INCIDENT_TYPE = "incidentType";
    public static final String CLAIMED_AMOUNT = "claimedAmount";
    public static final String CLAIMANT_NAME = "claimantName";

    public static final List<String> ALL = List.of(POLICY_NUMBER, INCIDENT_DATE, INCIDENT_TYPE, CLAIMED_AMOUNT, CLAIMANT_NAME);

    /** A claim cannot be assessed without these; if any is missing or uncertain, a person verifies it. */
    public static final List<String> REQUIRED = List.of(POLICY_NUMBER, INCIDENT_DATE, CLAIMED_AMOUNT);

    private ClaimFields() {
    }
}
