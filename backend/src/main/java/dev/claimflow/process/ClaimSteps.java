package dev.claimflow.process;

import dev.claimflow.audit.AuditLog;
import dev.claimflow.claim.Claim;
import dev.claimflow.claim.ClaimDocument;
import dev.claimflow.claim.ClaimDocumentRepository;
import dev.claimflow.claim.ClaimRepository;
import dev.claimflow.claim.ClaimStatus;
import dev.claimflow.claim.ExtractedField;
import dev.claimflow.claim.ExtractedFieldRepository;
import dev.claimflow.claim.NotificationPort;
import dev.claimflow.extraction.ClaimFields;
import dev.claimflow.extraction.ExtractionConfig.ExtractionProperties;
import dev.claimflow.extraction.ExtractionPort;
import dev.claimflow.extraction.ExtractionPort.ExtractedValue;
import dev.claimflow.extraction.ExtractionPort.ExtractionResult;
import dev.claimflow.extraction.FieldNormalizer;
import dev.claimflow.fraud.FraudScorer;
import dev.claimflow.payment.PaymentService;
import dev.claimflow.payment.Payout;
import dev.claimflow.policy.Policy;
import dev.claimflow.policy.PolicyRepository;
import dev.claimflow.task.ReviewTask;
import dev.claimflow.task.ReviewTaskRepository;
import dev.claimflow.task.TaskType;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The work behind each service task in claim.bpmn. Each method runs in one transaction, updates the read
 * model and audit log, and returns the variables the process needs next. Every method is safe to run twice
 * for the same job, because the engine retries jobs whose completion it never received.
 */
@Service
@Transactional
public class ClaimSteps {

    private final ClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final ExtractedFieldRepository fields;
    private final PolicyRepository policies;
    private final ReviewTaskRepository tasks;
    private final ExtractionPort extraction;
    private final ExtractionProperties extractionProperties;
    private final FraudScorer fraudScorer;
    private final PaymentService payments;
    private final NotificationPort notifications;
    private final AuditLog audit;
    private final MeterRegistry meters;
    private final Clock clock;

    public ClaimSteps(ClaimRepository claims, ClaimDocumentRepository documents, ExtractedFieldRepository fields,
                      PolicyRepository policies, ReviewTaskRepository tasks, ExtractionPort extraction,
                      ExtractionProperties extractionProperties, FraudScorer fraudScorer, PaymentService payments,
                      NotificationPort notifications, AuditLog audit, MeterRegistry meters, Clock clock) {
        this.claims = claims;
        this.documents = documents;
        this.fields = fields;
        this.policies = policies;
        this.tasks = tasks;
        this.extraction = extraction;
        this.extractionProperties = extractionProperties;
        this.fraudScorer = fraudScorer;
        this.payments = payments;
        this.notifications = notifications;
        this.audit = audit;
        this.meters = meters;
        this.clock = clock;
    }

    // --- claim.extract-fields --------------------------------------------------------------------

    public Map<String, Object> extractFields(UUID claimId) {
        Claim claim = claim(claimId);
        claim.moveTo(ClaimStatus.EXTRACTING, clock.instant());

        String text = documents.findByClaimIdOrderByCreatedAt(claimId).stream()
                .map(ClaimDocument::getExtractedText)
                .collect(Collectors.joining("\n\n"));
        ExtractionResult result = extraction.extract(text);

        for (String name : ClaimFields.ALL) {
            ExtractedValue value = result.get(name);
            BigDecimal confidence = BigDecimal.valueOf(value.confidence());
            fields.findByClaimIdAndName(claimId, name).ifPresentOrElse(
                    f -> f.replace(value.value(), confidence),
                    () -> fields.save(new ExtractedField(claimId, name, value.value(), confidence)));
        }
        applyToClaim(claim, result);

        List<String> uncertain = ClaimFields.REQUIRED.stream()
                .filter(f -> result.get(f).value() == null
                        || result.get(f).confidence() < extractionProperties.confidenceThreshold())
                .toList();
        boolean lowConfidence = !uncertain.isEmpty();
        audit.system(claimId, "FIELDS_EXTRACTED", result.provider() + (lowConfidence
                ? "; needs verification: " + String.join(", ", uncertain) : "; all required fields confident"));
        meters.counter("claimflow.extraction", "outcome", lowConfidence ? "needs_verification" : "confident").increment();

        Map<String, Object> vars = new HashMap<>(processFields(claim));
        vars.put("lowConfidence", lowConfidence);
        return vars;
    }

    /**
     * Only values that passed the deterministic checks reach the claim record. A value that failed them
     * (confidence 0) stays in extracted_field, where the adjuster sees the raw text while verifying.
     */
    private static void applyToClaim(Claim claim, ExtractionResult result) {
        claim.setPolicyNumber(valid(result, ClaimFields.POLICY_NUMBER));
        String date = valid(result, ClaimFields.INCIDENT_DATE);
        claim.setIncidentDate(date == null ? null : FieldNormalizer.parseDate(date));
        String amount = valid(result, ClaimFields.CLAIMED_AMOUNT);
        claim.setClaimedAmount(amount == null ? null : FieldNormalizer.parseAmount(amount));
        claim.setIncidentType(valid(result, ClaimFields.INCIDENT_TYPE));
    }

    private static String valid(ExtractionResult result, String field) {
        ExtractedValue value = result.get(field);
        return value.confidence() > 0 ? value.value() : null;
    }

    /** The claim fields the process gateways and the DMN read. Nulls are left out rather than sent. */
    static Map<String, Object> processFields(Claim claim) {
        Map<String, Object> vars = new HashMap<>();
        if (claim.getPolicyNumber() != null) vars.put("policyNumber", claim.getPolicyNumber());
        if (claim.getIncidentDate() != null) vars.put("incidentDate", claim.getIncidentDate().toString());
        if (claim.getClaimedAmount() != null) vars.put("claimedAmount", claim.getClaimedAmount());
        return vars;
    }

    // --- policy.lookup ---------------------------------------------------------------------------

    public Map<String, Object> lookupPolicy(UUID claimId) {
        Claim claim = claim(claimId);
        claim.moveTo(ClaimStatus.ASSESSING, clock.instant());
        LocalDate submitted = LocalDate.ofInstant(claim.getCreatedAt(), ZoneOffset.UTC);
        long daysSinceIncident = claim.getIncidentDate() == null ? 0
                : ChronoUnit.DAYS.between(claim.getIncidentDate(), submitted);

        Map<String, Object> vars = new HashMap<>();
        vars.put("claimedAmount", claim.getClaimedAmount() == null ? BigDecimal.ZERO : claim.getClaimedAmount());
        vars.put("daysSinceIncident", daysSinceIncident);

        Policy policy = claim.getPolicyNumber() == null ? null : policies.findById(claim.getPolicyNumber()).orElse(null);
        if (policy == null) {
            vars.putAll(Map.of("policyFound", false, "policyActive", false, "policyType", "UNKNOWN",
                    "coverLimit", 0, "deductible", 0, "daysSincePolicyStart", 0));
            audit.system(claimId, "POLICY_NOT_FOUND", "No policy " + claim.getPolicyNumber());
            return vars;
        }
        claim.setPolicyType(policy.getPolicyType());
        vars.put("policyFound", true);
        vars.put("policyActive", policy.isActiveOn(claim.getIncidentDate()));
        vars.put("policyType", policy.getPolicyType());
        vars.put("coverLimit", policy.getCoverLimit());
        vars.put("deductible", policy.getDeductible());
        vars.put("daysSincePolicyStart", claim.getIncidentDate() == null ? 0
                : ChronoUnit.DAYS.between(policy.getStartDate(), claim.getIncidentDate()));
        audit.system(claimId, "POLICY_FOUND", policy.getPolicyNumber() + " (" + policy.getPolicyType() + ")");
        return vars;
    }

    // --- claim.fraud-score -----------------------------------------------------------------------

    public Map<String, Object> scoreFraud(UUID claimId, Map<String, Object> coverage, long daysSincePolicyStart,
                                          long daysSinceIncident, BigDecimal coverLimit) {
        Claim claim = claim(claimId);
        claim.setPayableAmount(decimal(coverage.get("payableAmount")));
        long priorClaims = claim.getPolicyNumber() == null ? 0 : claims.countByPolicyNumberAndIdNotAndCreatedAtAfter(
                claim.getPolicyNumber(), claimId, claim.getCreatedAt().minus(Duration.ofDays(365)));

        FraudScorer.Score score = fraudScorer.score(new FraudScorer.Input(claim.getClaimedAmount(), coverLimit,
                daysSincePolicyStart, daysSinceIncident, priorClaims));
        claim.setFraudScore(BigDecimal.valueOf(score.value()));
        claim.touch(clock.instant());
        audit.system(claimId, "FRAUD_SCORED", score.value() + (score.signals().isEmpty() ? " (no signals)"
                : ": " + String.join("; ", score.signals())));
        return Map.of("fraudScore", score.value());
    }

    // --- task.open / task.escalate ---------------------------------------------------------------

    public Map<String, Object> openTask(UUID claimId, long jobKey, TaskType type, String candidateRole, int slaHours) {
        // Idempotent on the job key: a retried job gets the task it already created.
        ReviewTask task = tasks.findByJobKey(jobKey).orElseGet(() -> {
            Claim claim = claim(claimId);
            claim.moveTo(switch (type) {
                case VERIFY_FIELDS -> ClaimStatus.NEEDS_VERIFICATION;
                case ADJUSTER_REVIEW -> ClaimStatus.UNDER_REVIEW;
                case PAYOUT_PREPARE -> ClaimStatus.APPROVED;
                case PAYOUT_RELEASE -> ClaimStatus.PAYOUT_PENDING_RELEASE;
            }, clock.instant());
            ReviewTask created = tasks.save(new ReviewTask(claimId, type, candidateRole, jobKey,
                    clock.instant().plus(Duration.ofHours(slaHours)), clock.instant()));
            audit.system(claimId, "TASK_OPENED", type + " for " + candidateRole);
            return created;
        });
        return Map.of("currentTaskId", task.getId().toString());
    }

    public void escalate(UUID taskId) {
        ReviewTask task = tasks.findById(taskId).orElseThrow();
        if (task.isOpen() && !task.isEscalated()) {
            task.escalate();
            audit.system(task.getClaimId(), "TASK_ESCALATED", task.getTaskType() + " passed its SLA; now with "
                    + task.getCandidateRole());
            meters.counter("claimflow.tasks.escalated", "type", task.getTaskType().name()).increment();
        }
    }

    // --- payment.execute / payment.reverse / ledger.post ------------------------------------------

    public Map<String, Object> executePayout(UUID claimId) {
        Claim claim = claim(claimId);
        // Defence in depth: the API already refuses this, but money never moves on a self-approved payout.
        if (claim.getPayoutPreparedBy() == null || claim.getPayoutPreparedBy().equals(claim.getPayoutReleasedBy())) {
            throw new IllegalStateException("Maker-checker violated for claim " + claim.getReference());
        }
        claim.moveTo(ClaimStatus.PAYING, clock.instant());
        Payout payout = payments.execute(claimId, idempotencyKey(claimId), claim.getPayableAmount(),
                claim.getPayoutAccount());
        claim.setPaymentReference(payout.getReference());
        audit.system(claimId, "PAYOUT_EXECUTED", payout.getReference() + " for " + payout.getAmount());
        return Map.of("paymentReference", payout.getReference());
    }

    public void reversePayout(UUID claimId) {
        Payout payout = payments.reverse(idempotencyKey(claimId));
        audit.system(claimId, "PAYOUT_REVERSED", payout.getReference());
    }

    public void postToLedger(UUID claimId) {
        Payout payout = payments.get(idempotencyKey(claimId));
        payments.postToLedger(payout);
        audit.system(claimId, "LEDGER_POSTED", payout.getReference());
    }

    private static String idempotencyKey(UUID claimId) {
        return "payout:" + claimId;
    }

    // --- claim.notify ----------------------------------------------------------------------------

    public void notifyOutcome(UUID claimId, String outcome, Map<String, Object> coverage, String reviewNote) {
        Claim claim = claim(claimId);
        ClaimStatus status = ClaimStatus.valueOf(outcome);
        if (claim.getStatus() == status) {
            return; // retried job, already notified
        }
        String reason = switch (status) {
            case PAID -> "Paid " + claim.getPayableAmount() + " (" + claim.getPaymentReference() + ")";
            case PAYOUT_REVERSED -> "The payout could not be posted and was reversed. Our team will contact you.";
            default -> reviewNote != null && !reviewNote.isBlank() ? reviewNote
                    : coverage != null ? String.valueOf(coverage.get("reason")) : "Rejected";
        };
        claim.setDecisionReason(reason);
        claim.moveTo(status, clock.instant());
        notifications.send(claim, "Claim " + claim.getReference() + ": " + status, reason);
        audit.system(claimId, "CLAIM_" + status, reason);
        meters.counter("claimflow.claims.completed", "outcome", status.name()).increment();
    }

    // ---------------------------------------------------------------------------------------------

    private Claim claim(UUID id) {
        return claims.findById(id).orElseThrow(() -> new NoSuchElementException("No claim " + id));
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}
