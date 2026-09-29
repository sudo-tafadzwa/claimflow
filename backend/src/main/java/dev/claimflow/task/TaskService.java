package dev.claimflow.task;

import dev.claimflow.audit.AuditLog;
import dev.claimflow.claim.Claim;
import dev.claimflow.claim.ClaimRepository;
import dev.claimflow.claim.ExtractedFieldRepository;
import dev.claimflow.extraction.ClaimFields;
import dev.claimflow.extraction.FieldNormalizer;
import dev.claimflow.security.CurrentUser;
import dev.claimflow.security.Roles;
import dev.claimflow.web.ApiErrors.BadRequest;
import dev.claimflow.web.ApiErrors.Conflict;
import dev.claimflow.web.ApiErrors.Forbidden;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Completes human tasks. All controls are enforced here, not in the UI: who may complete a task, what a
 * valid completion looks like, and that the person releasing a payout is not the person who prepared it.
 */
@Service
public class TaskService {

    public record Completion(String policyNumber, String incidentDate, String claimedAmount,
                             String decision, String note, String payoutAccount) {
    }

    private final ReviewTaskRepository tasks;
    private final ClaimRepository claims;
    private final ExtractedFieldRepository fields;
    private final TaskResumer resumer;
    private final AuditLog audit;
    private final Clock clock;

    public TaskService(ReviewTaskRepository tasks, ClaimRepository claims, ExtractedFieldRepository fields,
                       TaskResumer resumer, AuditLog audit, Clock clock) {
        this.tasks = tasks;
        this.claims = claims;
        this.fields = fields;
        this.resumer = resumer;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReviewTask> openTasksFor(CurrentUser user) {
        Set<String> visible = new java.util.HashSet<>(user.roles());
        if (user.has(Roles.SENIOR_ADJUSTER)) {
            visible.add(Roles.ADJUSTER);
        }
        return tasks.findByStatusAndCandidateRoleInOrderBySlaDueAtAsc("OPEN", visible);
    }

    /**
     * Records the completion together with the variables that resume the process (a small outbox), and
     * resumes the process only after commit. Workers downstream therefore always read committed data, and
     * a failed publish is retried by {@link TaskResumer#resumePending()}.
     */
    @Transactional
    public ReviewTask complete(UUID taskId, CurrentUser user, Completion in) {
        ReviewTask task = tasks.findById(taskId).orElseThrow(() -> new NoSuchElementException("No task " + taskId));
        if (!task.isOpen()) {
            throw new Conflict("This task was already completed by " + task.getCompletedBy());
        }
        if (!mayWorkOn(user, task.getCandidateRole())) {
            throw new Forbidden("This task needs the role " + task.getCandidateRole());
        }
        Claim claim = claims.findById(task.getClaimId()).orElseThrow();

        Map<String, Object> variables = switch (task.getTaskType()) {
            case VERIFY_FIELDS -> verifyFields(claim, user, in);
            case ADJUSTER_REVIEW -> review(claim, in);
            case PAYOUT_PREPARE -> preparePayout(claim, user, in);
            case PAYOUT_RELEASE -> releasePayout(claim, user);
        };
        String outcome = task.getTaskType() == TaskType.ADJUSTER_REVIEW ? in.decision() : "DONE";
        task.complete(user.username(), outcome, resumer.write(variables), clock.instant());
        claim.touch(clock.instant());
        audit.record(claim.getId(), user.username(), "TASK_COMPLETED", task.getTaskType() + ": " + outcome
                + (in.note() == null || in.note().isBlank() ? "" : " (" + in.note() + ")"));

        UUID id = task.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                resumer.resume(id);
            }
        });
        return task;
    }

    private static boolean mayWorkOn(CurrentUser user, String candidateRole) {
        return user.has(candidateRole) || (Roles.ADJUSTER.equals(candidateRole) && user.has(Roles.SENIOR_ADJUSTER));
    }

    private Map<String, Object> verifyFields(Claim claim, CurrentUser user, Completion in) {
        String policy = required(in.policyNumber(), "policyNumber").trim().toUpperCase();
        LocalDate date = FieldNormalizer.parseDate(required(in.incidentDate(), "incidentDate"));
        BigDecimal amount = FieldNormalizer.parseAmount(required(in.claimedAmount(), "claimedAmount"));
        if (date == null) throw new BadRequest("incidentDate must be a date (YYYY-MM-DD)");
        if (amount == null || amount.signum() <= 0) throw new BadRequest("claimedAmount must be a positive amount");
        if (date.isAfter(LocalDate.now(clock))) throw new BadRequest("incidentDate cannot be in the future");

        claim.setPolicyNumber(policy);
        claim.setIncidentDate(date);
        claim.setClaimedAmount(amount);
        Map<String, String> verified = Map.of(ClaimFields.POLICY_NUMBER, policy,
                ClaimFields.INCIDENT_DATE, date.toString(), ClaimFields.CLAIMED_AMOUNT, amount.toPlainString());
        verified.forEach((name, value) -> fields.findByClaimIdAndName(claim.getId(), name)
                .ifPresent(f -> f.verify(value, user.username())));

        Map<String, Object> vars = new HashMap<>();
        vars.put("policyNumber", policy);
        vars.put("incidentDate", date.toString());
        vars.put("claimedAmount", amount);
        vars.put("lowConfidence", false);
        return vars;
    }

    private Map<String, Object> review(Claim claim, Completion in) {
        String decision = required(in.decision(), "decision");
        if (!decision.equals("APPROVE") && !decision.equals("REJECT")) {
            throw new BadRequest("decision must be APPROVE or REJECT");
        }
        if (decision.equals("REJECT") && (in.note() == null || in.note().isBlank())) {
            throw new BadRequest("A rejection needs a note explaining why; the claimant will see it");
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("decision", decision);
        vars.put("reviewNote", in.note() == null ? "" : in.note());
        return vars;
    }

    private Map<String, Object> preparePayout(Claim claim, CurrentUser user, Completion in) {
        String account = required(in.payoutAccount(), "payoutAccount").trim();
        if (!account.matches("[A-Z0-9 -]{6,34}")) {
            throw new BadRequest("payoutAccount must be 6 to 34 letters, digits, spaces or dashes");
        }
        claim.setPayoutAccount(account);
        claim.setPayoutPreparedBy(user.username());
        return Map.of("payoutPreparedBy", user.username());
    }

    private Map<String, Object> releasePayout(Claim claim, CurrentUser user) {
        if (user.username().equals(claim.getPayoutPreparedBy())) {
            throw new Forbidden("Maker-checker: you prepared this payout, so someone else must release it");
        }
        claim.setPayoutReleasedBy(user.username());
        return Map.of("payoutReleasedBy", user.username());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BadRequest(name + " is required");
        }
        return value;
    }
}
