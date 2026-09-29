package dev.claimflow.web;

import dev.claimflow.audit.AuditEvent;
import dev.claimflow.claim.Claim;
import dev.claimflow.claim.ClaimDocument;
import dev.claimflow.claim.ClaimStatus;
import dev.claimflow.claim.ExtractedField;
import dev.claimflow.task.ReviewTask;
import dev.claimflow.task.TaskType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response shapes. Staff-only sections are null for claimants. */
public final class Views {

    private Views() {
    }

    public record ClaimSummary(UUID id, String reference, String claimantName, ClaimStatus status,
                               String policyNumber, BigDecimal claimedAmount, BigDecimal payableAmount,
                               Instant createdAt, Instant updatedAt) {
        public static ClaimSummary of(Claim c) {
            return new ClaimSummary(c.getId(), c.getReference(), c.getClaimantName(), c.getStatus(),
                    c.getPolicyNumber(), c.getClaimedAmount(), c.getPayableAmount(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record ClaimDetail(UUID id, String reference, String claimantName, String claimantEmail,
                              ClaimStatus status, String policyNumber, String policyType, LocalDate incidentDate,
                              String incidentType, BigDecimal claimedAmount, BigDecimal payableAmount,
                              String decisionReason, String paymentReference, Instant createdAt, Instant updatedAt,
                              Staff staff) {
    }

    /** What only staff see: fraud score, extraction confidence, controls, audit, tasks. */
    public record Staff(BigDecimal fraudScore, Long processInstanceKey, String payoutAccount,
                        String payoutPreparedBy, String payoutReleasedBy, List<FieldView> fields,
                        List<TaskView> tasks, List<AuditView> audit, List<DocumentView> documents) {
    }

    public record FieldView(String name, String value, BigDecimal confidence, String verifiedBy) {
        public static FieldView of(ExtractedField f) {
            return new FieldView(f.getName(), f.getValue(), f.getConfidence(), f.getVerifiedBy());
        }
    }

    public record TaskView(UUID id, UUID claimId, String claimReference, TaskType type, String candidateRole,
                           String status, Instant slaDueAt, boolean escalated, Instant createdAt,
                           String completedBy, String outcome) {
        public static TaskView of(ReviewTask t, String claimReference) {
            return new TaskView(t.getId(), t.getClaimId(), claimReference, t.getTaskType(), t.getCandidateRole(),
                    t.getStatus(), t.getSlaDueAt(), t.isEscalated(), t.getCreatedAt(), t.getCompletedBy(), t.getOutcome());
        }
    }

    public record AuditView(Instant at, String actor, String type, String detail) {
        public static AuditView of(AuditEvent e) {
            return new AuditView(e.getOccurredAt(), e.getActor(), e.getEventType(), e.getDetail());
        }
    }

    public record DocumentView(UUID id, String filename, String contentType, int sizeBytes) {
        public static DocumentView of(ClaimDocument d) {
            return new DocumentView(d.getId(), d.getFilename(), d.getContentType(), d.getSizeBytes());
        }
    }

    public record Me(String username, List<String> roles) {
    }
}
